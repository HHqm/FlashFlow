import http from "k6/http";
import { check } from "k6";
import { Counter, Trend } from "k6/metrics";

const accepted = new Counter("orders_accepted");
const businessRejected = new Counter("orders_business_rejected");
const throttled = new Counter("orders_throttled");
const serverErrors = new Counter("orders_server_errors");
const successLatency = new Trend("order_success_latency", true);

const rate = Number(__ENV.RATE || 100);

export const options = {
  scenarios: {
    order_writes: {
      executor: "constant-arrival-rate",
      rate,
      timeUnit: "1s",
      duration: __ENV.DURATION || "20s",
      preAllocatedVUs: Number(__ENV.VUS || Math.max(50, rate)),
      maxVUs: Number(__ENV.MAX_VUS || Math.max(200, rate * 3)),
    },
  },
};

const baseUrl = __ENV.BASE_URL || "http://host.docker.internal:8080";
const campaignId = __ENV.CAMPAIGN_ID;
const token = __ENV.TOKEN;
const addressId = Number(__ENV.ADDRESS_ID || 1);
const runId = (__ENV.RUN_ID || "run").replace(/[^A-Za-z0-9_-]/g, "");
const expectedStatuses = http.expectedStatuses(200, 409, 429);

export function setup() {
  if (!campaignId || !token || !addressId) {
    throw new Error("CAMPAIGN_ID, TOKEN and ADDRESS_ID are required.");
  }
}

export default function () {
  const idempotencyKey = `${runId}_${__VU}_${__ITER}`;
  const response = http.post(
    `${baseUrl}/api/v1/campaigns/${campaignId}/orders`,
    JSON.stringify({ quantity: 1, addressId }),
    {
      headers: {
        Authorization: `Bearer ${token}`,
        "Content-Type": "application/json",
        "Idempotency-Key": idempotencyKey,
      },
      responseCallback: expectedStatuses,
      tags: { name: "place_order" },
    },
  );

  if (response.status === 200) {
    accepted.add(1);
    successLatency.add(response.timings.duration);
  } else if (response.status === 409) {
    businessRejected.add(1);
  } else if (response.status === 429) {
    throttled.add(1);
  } else if (response.status >= 500 || response.status === 0) {
    serverErrors.add(1);
  }

  if (__ENV.DEBUG === "1" && ![200, 409, 429].includes(response.status)) {
    console.error(`unexpected HTTP ${response.status}: ${response.body}`);
  }

  check(response, {
    "response is a defined business outcome": (r) => [200, 409, 429].includes(r.status),
  });
}
