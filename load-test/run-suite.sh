#!/usr/bin/env bash
# Runs the FlashFlow benchmark suite against the local Docker Compose stack and checks MySQL,
# Redis and the Outbox after every scenario. Results land in load-test/results/<suite>/.
#
# Usage: bash load-test/run-suite.sh [scenario ...]
#   Every run first creates the BUYERS load-test accounts if missing and mints fresh JWTs.
#   warmup     200 req/s for 10 s, discarded
#   throughput  RATES (default "500 800 1000") req/s for 20 s, every request a distinct buyer
#   hotsale     10,000 buyers each try twice at 2,000 req/s for 1,000 units, limit 1 per buyer
#   idempotency one key replayed 1,000 times by 100 concurrent VUs
#   ratelimit   one buyer floods 100 req/s with the default protection settings
#   chaos       SIGKILL trade-service in the middle of 500 req/s, then wait for reconciliation
# With no arguments every scenario runs in that order. The stack must use default settings
# (docker compose up -d), not compose.benchmark.yml. k6 runs inside the compose network and
# calls gateway-service directly: Docker Desktop's published host port adds a port forwarder
# that starts refusing new connections at around 1,000 requests per second.
set -euo pipefail

cd "$(dirname "$0")/.."
export MSYS_NO_PATHCONV=1

GATEWAY=${GATEWAY:-http://localhost:8080}
K6_BASE_URL=${K6_BASE_URL:-http://gateway-service:8080}
K6_NETWORK=${K6_NETWORK:-flashflow_flashflow}
K6_IMAGE=${K6_IMAGE:-grafana/k6:0.57.0}
BUYERS=${BUYERS:-20000}
RATES=${RATES:-"500 800 1000"}
ADMIN_USERNAME=${ADMIN_USERNAME:-admin}
ADMIN_PASSWORD=${ADMIN_PASSWORD:-Admin@123456}
SUITE=${SUITE:-suite-$(date +%Y%m%d-%H%M%S)}
OUT=load-test/results/$SUITE
SUMMARY=$OUT/summary.md
mkdir -p "$OUT"
SCRIPTS_DIR=$(cd load-test && (pwd -W 2>/dev/null || pwd))

log() { printf '\n== %s\n' "$*" | tee -a "$OUT/suite.log"; }
json() { python -c "import json,sys; d=json.load(sys.stdin); print($1)"; }

mysql_q() {
    docker compose exec -T mysql sh -c \
        'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N -B "$0" 2>/dev/null' "${2:-flashflow_trade}" <<<"$1" | tr -d '\r'
}

redis_get() {
    docker compose exec -T redis sh -c \
        'redis-cli -a "$REDIS_PASSWORD" --no-auth-warning GET "$0"' "$1" | tr -d '\r'
}

api() {
    local method=$1 path=$2 body=${3:-}
    curl -fsS -X "$method" "$GATEWAY$path" \
        -H "Authorization: Bearer $ADMIN_TOKEN" \
        -H 'Content-Type: application/json' \
        ${body:+-d "$body"}
}

login_admin() {
    ADMIN_TOKEN=$(curl -fsS -X POST "$GATEWAY/api/v1/auth/login" \
        -H 'Content-Type: application/json' \
        -d "{\"username\":\"$ADMIN_USERNAME\",\"password\":\"$ADMIN_PASSWORD\"}" \
        | json "d['data']['accessToken']")
}

ensure_variant() {
    if [ -n "${VARIANT_ID:-}" ]; then
        return
    fi
    local category sku
    category=$(curl -fsS "$GATEWAY/api/v1/categories" | json "d['data'][0]['id']")
    sku="LT-$(date +%s)"
    VARIANT_ID=$(api POST /api/v1/admin/products \
        "{\"categoryId\":$category,\"name\":\"Benchmark item $sku\",\"description\":\"k6 load test\",\"imageUrl\":\"/product-images/headphone.svg\",\"variants\":[{\"skuCode\":\"$sku\",\"colorName\":\"Black\",\"specification\":\"Benchmark\",\"price\":999,\"stock\":2000000}]}" \
        | json "d['data']['variants'][0]['id']")
}

create_campaign() {
    local stock=$1 limit=$2 starts ends
    ensure_variant
    starts=$(date -u -d '-1 minute' +%Y-%m-%dT%H:%M:%SZ)
    ends=$(date -u -d '+1 day' +%Y-%m-%dT%H:%M:%SZ)
    api POST /api/v1/admin/campaigns \
        "{\"variantId\":$VARIANT_ID,\"flashPrice\":99,\"totalStock\":$stock,\"purchaseLimit\":$limit,\"startsAt\":\"$starts\",\"endsAt\":\"$ends\"}" \
        | json "d['data']['id']"
}

run_k6() {
    local name=$1 script=$2
    shift 2
    local env_args=()
    for pair in "$@"; do
        env_args+=(-e "$pair")
    done
    docker run --rm --network "$K6_NETWORK" \
        -v "$SCRIPTS_DIR:/scripts" \
        -e BASE_URL="$K6_BASE_URL" -e RUN_ID="$SUITE-$name" "${env_args[@]}" \
        "$K6_IMAGE" run --quiet \
        --summary-export "/scripts/results/$SUITE/$name.json" \
        --summary-trend-stats "avg,min,med,max,p(90),p(95),p(99)" \
        "/scripts/$script" > "$OUT/$name.log" 2>&1 || true
}

wait_outbox_drained() {
    local pending
    for _ in $(seq 1 90); do
        pending=$(mysql_q "select count(*) from outbox_event where status = 'PENDING'")
        if [ "$pending" = "0" ]; then
            return 0
        fi
        sleep 1
    done
    echo "outbox still has $pending pending events" | tee -a "$OUT/suite.log"
}

# Collects the post-run facts that prove or disprove oversell for one campaign.
campaign_facts() {
    local campaign=$1 row
    row=$(mysql_q "select total_stock, remaining_stock,
                          (select count(*) from trade_order where campaign_id = $campaign),
                          (select coalesce(sum(quantity), 0) from trade_order where campaign_id = $campaign),
                          (select count(distinct user_id) from trade_order where campaign_id = $campaign)
                     from flash_campaign where id = $campaign")
    read -r TOTAL REMAINING ORDERS SOLD DISTINCT_BUYERS <<<"$row"
    MAX_PER_BUYER=$(mysql_q "select coalesce(max(n), 0) from (select count(*) n from trade_order
                               where campaign_id = $campaign group by user_id) t")
    REDIS_LEFT=$(redis_get "flashflow:campaign:$campaign:stock")
}

record() {
    local name=$1 rate=$2 campaign=$3 notes=${4:-}
    wait_outbox_drained
    campaign_facts "$campaign"
    notes="campaign $campaign; orders $ORDERS; buyers $DISTINCT_BUYERS; max/buyer $MAX_PER_BUYER${notes:+; $notes}"
    python load-test/summarize.py "$SUMMARY" "$name" "$OUT/$name.json" \
        rate="$rate" total="$TOTAL" remaining="$REMAINING" sold="$SOLD" redis="$REDIS_LEFT" notes="$notes" \
        | tee -a "$OUT/suite.log"
}

scenario_seed() {
    log "seed $BUYERS buyers and mint fresh tokens"
    mysql_q "SET @users = $BUYERS; $(cat load-test/seed-users.sql)" > "$OUT/buyers.tsv"
    JWT_SECRET=$(docker compose exec -T trade-service printenv JWT_SECRET | tr -d '\r') \
        python load-test/mint-tokens.py load-test/results/users.json < "$OUT/buyers.tsv"
}

scenario_warmup() {
    log "warmup"
    local campaign
    campaign=$(create_campaign 5000 1)
    run_k6 warmup multi-user-flash-sale.js CAMPAIGN_ID="$campaign" RATE=200 DURATION=10s
    sleep 5
}

scenario_throughput() {
    local rate campaign
    for rate in $RATES; do
        log "throughput $rate req/s"
        campaign=$(create_campaign $((rate * 25)) 1)
        run_k6 "throughput-r$rate" multi-user-flash-sale.js \
            CAMPAIGN_ID="$campaign" RATE="$rate" DURATION=20s BUYERS="$BUYERS"
        record "throughput-r$rate" "$rate" "$campaign"
        sleep 10
    done
}

scenario_hotsale() {
    log "hot sale: 1,000 units, 10,000 buyers x 2 attempts at 2,000 req/s"
    local campaign
    campaign=$(create_campaign 1000 1)
    run_k6 hotsale multi-user-flash-sale.js \
        CAMPAIGN_ID="$campaign" RATE=2000 DURATION=10s BUYERS=10000 VUS=400 MAX_VUS=2000
    record hotsale 2000 "$campaign" "10000 buyers x 2 attempts"
}

scenario_idempotency() {
    log "idempotency replay"
    local campaign token address
    campaign=$(create_campaign 100 1)
    token=$(json "d[0]['token']" < load-test/results/users.json)
    address=$(json "d[0]['addressId']" < load-test/results/users.json)
    run_k6 idempotency idempotency-replay.js \
        CAMPAIGN_ID="$campaign" TOKEN="$token" ADDRESS_ID="$address" VUS=100 ITERATIONS=1000
    wait_outbox_drained
    campaign_facts "$campaign"
    python - "$OUT/idempotency.json" "$ORDERS" "$REMAINING" "$REDIS_LEFT" <<'EOF' | tee -a "$OUT/suite.log" "$OUT/idempotency.txt"
import json, sys
metrics = json.load(open(sys.argv[1]))["metrics"]
same = metrics.get("same_order_rate", {})
print("idempotency: replays=%s same_order_rate=%s p95=%.1f ms orders=%s remaining=%s redis=%s" % (
    metrics.get("idempotent_replays", {}).get("count"),
    same.get("value", same.get("rate")),
    metrics.get("idempotent_replay_latency", {}).get("p(95)", 0),
    sys.argv[2], sys.argv[3], sys.argv[4]))
EOF
}

scenario_ratelimit() {
    log "default per-user protection: one buyer floods 100 req/s"
    local campaign token address
    campaign=$(create_campaign 2000 2000)
    token=$(json "d[1]['token']" < load-test/results/users.json)
    address=$(json "d[1]['addressId']" < load-test/results/users.json)
    run_k6 ratelimit order-throughput.js \
        CAMPAIGN_ID="$campaign" TOKEN="$token" ADDRESS_ID="$address" RATE=100 DURATION=10s
    record ratelimit 100 "$campaign" "single buyer"
}

wait_trade_healthy() {
    for _ in $(seq 1 120); do
        if curl -fsS http://localhost:8081/actuator/health >/dev/null 2>&1; then
            return 0
        fi
        sleep 1
    done
    return 1
}

scenario_chaos() {
    log "chaos: SIGKILL trade-service under 500 req/s"
    local campaign k6_pid killed_at healthy_at traffic_done drift repaired_after=""
    campaign=$(create_campaign 30000 1)
    run_k6 chaos multi-user-flash-sale.js \
        CAMPAIGN_ID="$campaign" RATE=500 DURATION=40s BUYERS="$BUYERS" &
    k6_pid=$!
    sleep 12
    docker kill -s KILL "$(docker compose ps -q trade-service)" >/dev/null
    killed_at=$(date +%s)
    sleep 3
    docker compose start trade-service >/dev/null
    wait_trade_healthy
    healthy_at=$(date +%s)
    wait "$k6_pid" || true
    traffic_done=$(date +%s)
    wait_outbox_drained
    campaign_facts "$campaign"
    drift=$(( ${REDIS_LEFT:-0} - REMAINING ))
    log "after traffic: mysql=$REMAINING redis=$REDIS_LEFT drift=$drift (restart took $((healthy_at - killed_at)) s)"
    for _ in $(seq 1 36); do
        campaign_facts "$campaign"
        if [ "$REDIS_LEFT" = "$REMAINING" ]; then
            repaired_after=$(( $(date +%s) - traffic_done ))
            break
        fi
        sleep 5
    done
    mysql_q "select campaign_id, redis_stock_before, redis_stock_after, drift, trigger_source, created_at
               from stock_reconciliation_log where campaign_id = $campaign" > "$OUT/chaos-repairs.tsv"
    record chaos 500 "$campaign" "killed at +12s; redis-mysql drift $drift; restart $((healthy_at - killed_at)) s; repaired ${repaired_after:-NOT} s after traffic"
}

login_admin
scenario_seed
scenarios=("$@")
if [ ${#scenarios[@]} -eq 0 ]; then
    scenarios=(warmup throughput hotsale idempotency ratelimit chaos)
fi
for scenario in "${scenarios[@]}"; do
    "scenario_$scenario"
done
log "summary: $SUMMARY"
cat "$SUMMARY" 2>/dev/null || true
