import http from "k6/http";
import exec from "k6/execution";
import { check } from "k6";
import { SharedArray } from "k6/data";
import { Counter, Trend } from "k6/metrics";

// Every iteration is a different buyer with its own JWT, so the gateway's per-user token bucket
// and trade-service's per-user limit stay at their production defaults.
const buyers = new SharedArray("buyers", () =>
  JSON.parse(open(__ENV.USERS_FILE || "/scripts/results/users.json")),
);

const accepted = new Counter("orders_accepted");
const soldOut = new Counter("orders_sold_out");
const limitExceeded = new Counter("orders_limit_exceeded");
const throttled = new Counter("orders_throttled");
const serverErrors = new Counter("orders_server_errors");
const otherOutcomes = new Counter("orders_other");
const successLatency = new Trend("order_success_latency", true);
const rejectLatency = new Trend("order_reject_latency", true);

const rate = Number(__ENV.RATE || 200);

export const options = {
  scenarios: {
    buyers: {
      executor: "constant-arrival-rate",
      rate,
      timeUnit: "1s",
      duration: __ENV.DURATION || "20s",
      preAllocatedVUs: Number(__ENV.VUS || Math.max(100, rate)),
      maxVUs: Number(__ENV.MAX_VUS || Math.max(400, rate * 3)),
    },
  },
  summaryTrendStats: ["avg", "min", "med", "max", "p(90)", "p(95)", "p(99)"],
};

const baseUrl = __ENV.BASE_URL || "http://host.docker.internal:8080";
const campaignId = __ENV.CAMPAIGN_ID;
// BUYERS limits how many distinct buyers are used; iterations beyond it reuse buyers, which
// exercises the per-user purchase limit.
const buyerCount = Math.min(Number(__ENV.BUYERS || buyers.length), buyers.length);
const runId = (__ENV.RUN_ID || "multi").replace(/[^A-Za-z0-9_-]/g, "");
const expectedStatuses = http.expectedStatuses(200, 409, 429);

export function setup() {
  if (!campaignId) {
    throw new Error("CAMPAIGN_ID is required.");
  }
  if (buyerCount === 0) {
    throw new Error("No buyers loaded; run seed-users.sql and mint-tokens.py first.");
  }
}

export default function () {
  const iteration = exec.scenario.iterationInTest;
  const buyer = buyers[iteration % buyerCount];
  const response = http.post(
    `${baseUrl}/api/v1/campaigns/${campaignId}/orders`,
    JSON.stringify({ quantity: 1, addressId: buyer.addressId }),
    {
      headers: {
        Authorization: `Bearer ${buyer.token}`,
        "Content-Type": "application/json",
        "Idempotency-Key": `${runId}_${iteration}`,
      },
      responseCallback: expectedStatuses,
      tags: { name: "place_order" },
    },
  );

  if (response.status === 200) {
    accepted.add(1);
    successLatency.add(response.timings.duration);
  } else if (response.status === 409) {
    rejectLatency.add(response.timings.duration);
    const code = response.json("code");
    if (code === "SOLD_OUT") {
      soldOut.add(1);
    } else if (code === "PURCHASE_LIMIT_EXCEEDED") {
      limitExceeded.add(1);
    } else {
      otherOutcomes.add(1);
    }
  } else if (response.status === 429) {
    throttled.add(1);
  } else if (response.status >= 500 || response.status === 0) {
    serverErrors.add(1);
  } else {
    otherOutcomes.add(1);
  }

  check(response, {
    "defined business outcome": (r) => [200, 409, 429].includes(r.status),
  });
}
