import http from "k6/http";
import { check } from "k6";
import { Counter, Rate, Trend } from "k6/metrics";

const replayed = new Counter("idempotent_replays");
const sameOrder = new Rate("same_order_rate");
const replayLatency = new Trend("idempotent_replay_latency", true);

export const options = {
  scenarios: {
    duplicate_replay: {
      executor: "shared-iterations",
      vus: Number(__ENV.VUS || 100),
      iterations: Number(__ENV.ITERATIONS || 1000),
      maxDuration: __ENV.MAX_DURATION || "30s",
    },
  },
};

const baseUrl = __ENV.BASE_URL || "http://host.docker.internal:8080";
const campaignId = __ENV.CAMPAIGN_ID;
const token = __ENV.TOKEN;
const addressId = Number(__ENV.ADDRESS_ID || 1);
const runId = (__ENV.RUN_ID || "idempotency").replace(/[^A-Za-z0-9_-]/g, "");
const idempotencyKey = `idem_${runId}`;
const expectedStatuses = http.expectedStatuses(200);

function placeOrder() {
  return http.post(
    `${baseUrl}/api/v1/campaigns/${campaignId}/orders`,
    JSON.stringify({ quantity: 1, addressId }),
    {
      headers: {
        Authorization: `Bearer ${token}`,
        "Content-Type": "application/json",
        "Idempotency-Key": idempotencyKey,
      },
      responseCallback: expectedStatuses,
      tags: { name: "idempotent_replay" },
    },
  );
}

export function setup() {
  if (!campaignId || !token || !addressId) {
    throw new Error("CAMPAIGN_ID, TOKEN and ADDRESS_ID are required.");
  }
  const seed = placeOrder();
  if (seed.status !== 200) {
    throw new Error(`Unable to seed idempotent order: HTTP ${seed.status}`);
  }
  return { orderNo: seed.json("data.orderNo") };
}

export default function (data) {
  const response = placeOrder();
  const matches = response.status === 200 && response.json("data.orderNo") === data.orderNo;
  replayed.add(response.status === 200 ? 1 : 0);
  sameOrder.add(matches);
  replayLatency.add(response.timings.duration);
  check(response, {
    "duplicate request returns the original order": () => matches,
  });
}
