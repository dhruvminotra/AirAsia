#!/usr/bin/env bash
#
# Lightweight load generator for the calendar endpoint. Prefers `hey` or `wrk`
# if installed; otherwise falls back to a parallel curl loop. Useful to observe
# the cache hit ratio climb (see /actuator/prometheus) and that latency stays low
# once the cache is warm.
#
#   REQUESTS=2000 CONCURRENCY=100 ./scripts/load-test.sh
set -euo pipefail

BASE="${BASE:-http://localhost:8080}"
ORIGIN="${ORIGIN:-KUL}"
DEST="${DEST:-SIN}"
MONTH="${MONTH:-$(date +%Y-%m)}"
URL="${BASE}/api/v1/flights/calendar?origin=${ORIGIN}&destination=${DEST}&month=${MONTH}&currency=MYR"
REQUESTS="${REQUESTS:-2000}"
CONCURRENCY="${CONCURRENCY:-100}"

if command -v hey >/dev/null 2>&1; then
  echo "Using hey: ${REQUESTS} requests @ concurrency ${CONCURRENCY}"
  hey -n "${REQUESTS}" -c "${CONCURRENCY}" "${URL}"
elif command -v wrk >/dev/null 2>&1; then
  echo "Using wrk: 30s @ ${CONCURRENCY} connections"
  wrk -t4 -c"${CONCURRENCY}" -d30s "${URL}"
else
  echo "hey/wrk not found; falling back to parallel curl (${REQUESTS} requests)"
  seq "${REQUESTS}" | xargs -P "${CONCURRENCY}" -I{} \
    curl -s -o /dev/null -w "%{http_code} %{time_total}s\n" "${URL}" \
    | sort | uniq -c | sort -rn | head
fi
