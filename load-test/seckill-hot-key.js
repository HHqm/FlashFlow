import http from "k6/http";
import { check, sleep } from "k6";
import { Counter, Rate, Trend } from "k6/metrics";

const rejected = new Counter("business_rejected");
const transportErrors = new Rate("transport_errors");
const orderLatency = new Trend("order_latency", true);

export const options = {
  scenarios: {
    hot_key_protection: {
      executor: "constant-arrival-rate",
      rate: Number(__ENV.RATE || 100),
      timeUnit: "1s",
      duration: __ENV.DURATION || "30s",
      preAllocatedVUs: Number(__ENV.VUS || 50),
      maxVUs: Number(__ENV.MAX_VUS || 200),
    },
  },
  thresholds: {
    http_req_failed: ["rate<0.05"],
    transport_errors: ["rate<0.01"],
    order_latency: ["p(95)<1000"],
  },
};

const baseUrl = __ENV.BASE_URL || "http://localhost:8080";
const campaignId = __ENV.CAMPAIGN_ID;
const token = __ENV.TOKEN;

export function setup() {
  if (!campaignId || !token) {
    throw new Error("Set CAMPAIGN_ID and TOKEN before running k6.");
  }
}

export default function () {
  const idempotencyKey = `${__VU}_${__ITER}_${Date.now()}`;
  const response = http.post(
    `${baseUrl}/api/v1/campaigns/${campaignId}/orders`,
    null,
    {
      headers: {
        Authorization: `Bearer ${token}`,
        "Idempotency-Key": idempotencyKey,
      },
      tags: { name: "place_order" },
    },
  );

  orderLatency.add(response.timings.duration);
  if ([409, 429].includes(response.status)) {
    rejected.add(1);
  } else if (response.status >= 500 || response.status === 0) {
    transportErrors.add(true);
  } else {
    transportErrors.add(false);
  }

  check(response, {
    "request has expected outcome": (r) => [200, 409, 429].includes(r.status),
  });
  sleep(0.01);
}
