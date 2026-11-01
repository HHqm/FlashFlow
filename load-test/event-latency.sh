#!/usr/bin/env bash
# Prints end-to-end event latency (order transaction -> notification Inbox commit) for events the
# notification service processed since the given UTC epoch millisecond. Event ids are UUIDv7, so
# the first 48 bits are the creation time in the trade service; no extra column is needed.
#
# Usage: bash load-test/event-latency.sh <since-epoch-ms>
set -euo pipefail
cd "$(dirname "$0")/.."
since=${1:?since epoch ms required}

docker compose exec -T mysql sh -c \
    'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N -B flashflow_notification 2>/dev/null' <<SQL | tr -d '\r' | python -c '
import sys
values = sorted(float(line) for line in sys.stdin if line.strip())
if not values:
    print("events=0")
    sys.exit()
def pct(p):
    return values[min(len(values) - 1, int(round(p / 100.0 * (len(values) - 1))))]
print("events=%d p50=%.0fms p95=%.0fms p99=%.0fms max=%.0fms" % (
    len(values), pct(50), pct(95), pct(99), values[-1]))
'
select unix_timestamp(processed_at) * 1000 - conv(left(hex(event_id), 12), 16, 10)
  from processed_message
 where processed_at >= from_unixtime(${since} / 1000)
   and substring(hex(event_id), 13, 1) = '7';
SQL
