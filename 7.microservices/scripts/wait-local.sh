#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
for attempt in $(seq 1 90); do
    ready=true
    for service in auth-service order-service notification-service api-gateway; do
        container=$(docker compose ps -q "$service")
        if [ -z "$container" ]; then
            ready=false
        elif [ "$(docker inspect --format '{{.State.Health.Status}}' "$container")" != healthy ]; then
            ready=false
        fi
    done
    if [ "$ready" = true ]; then
        echo "All four application health checks passed."
        exit 0
    fi
    sleep 2
done
echo "Applications did not become healthy within 180 seconds. Inspect docker compose ps/logs." >&2
docker compose ps
exit 1
