#!/usr/bin/env bash
# One-shot setup for CDC mode, safe to re-run: creates the Debezium MySQL account (works on an
# existing data volume, unlike docker-entrypoint-initdb.d) and upserts the Outbox connector.
set -euo pipefail

mysql -h mysql -uroot -p"$MYSQL_ROOT_PASSWORD" <<SQL
CREATE USER IF NOT EXISTS 'debezium'@'%' IDENTIFIED BY '${DEBEZIUM_PASSWORD}';
ALTER USER 'debezium'@'%' IDENTIFIED BY '${DEBEZIUM_PASSWORD}';
GRANT SELECT, RELOAD, SHOW DATABASES, REPLICATION SLAVE, REPLICATION CLIENT ON *.* TO 'debezium'@'%';
SQL

config=$(sed "s/__DEBEZIUM_PASSWORD__/${DEBEZIUM_PASSWORD}/" /debezium/outbox-connector.json)
for attempt in $(seq 1 60); do
    if curl -fsS -o /dev/null http://kafka-connect:8083/connectors; then
        break
    fi
    sleep 2
done
# PUT .../config creates or updates, so re-running never duplicates the connector.
curl -fsS -X PUT -H 'Content-Type: application/json' \
    --data "$config" http://kafka-connect:8083/connectors/flashflow-outbox/config >/dev/null
echo "flashflow-outbox connector registered"
