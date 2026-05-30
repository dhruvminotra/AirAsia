#!/usr/bin/env bash
#
# End-to-end demo: read the calendar, then prove the async sold-out update path.
#
#   1. Fetch the calendar (cold -> aggregates providers, warms cache).
#   2. Fetch the same route in another currency (served from the same cache).
#   3. Publish sold-out events for the cheapest (PROMO) class on the target date.
#   4. Re-fetch -> that date's price has risen to the next available fare.
#
# Requires: curl, jq. Override BASE to point at a remote host.
set -euo pipefail

BASE="${BASE:-http://localhost:8080}"
ORIGIN="${ORIGIN:-KUL}"
DEST="${DEST:-SIN}"
MONTH="${MONTH:-$(date +%Y-%m)}"
DAY="${DAY:-15}"
DATE="${MONTH}-${DAY}"

echo "== 1) Calendar in MYR (first 5 days) =="
curl -s "${BASE}/api/v1/flights/calendar?origin=${ORIGIN}&destination=${DEST}&month=${MONTH}&currency=MYR" \
  | jq '{currency, days: .days[0:5]}'

echo "== 2) Same calendar in USD (same cache, converted at read) =="
curl -s "${BASE}/api/v1/flights/calendar?origin=${ORIGIN}&destination=${DEST}&month=${MONTH}&currency=USD" \
  | jq '{currency, days: .days[0:5]}'

echo "== 3) Price for ${DATE} BEFORE sold-out =="
curl -s "${BASE}/api/v1/flights/calendar?origin=${ORIGIN}&destination=${DEST}&month=${MONTH}&currency=MYR" \
  | jq --arg d "${DATE}" '.days[] | select(.date == $d)'

echo "== 4) Publishing sold-out for PROMO class on AK100/AK101/AK102 (${DATE}) =="
for FLIGHT in AK100 AK101 AK102; do
  curl -s -o /dev/null -w "  ${FLIGHT} -> %{http_code}\n" \
    -X POST "${BASE}/api/v1/flights/sold-out" \
    -H "Content-Type: application/json" \
    -d "{
      \"eventId\": \"$(uuidgen 2>/dev/null || echo evt-${FLIGHT}-${DATE})\",
      \"origin\": \"${ORIGIN}\",
      \"destination\": \"${DEST}\",
      \"date\": \"${DATE}\",
      \"flightNumber\": \"${FLIGHT}\",
      \"priceClass\": \"PROMO\",
      \"occurredAtEpochMillis\": $(date +%s000)
    }"
done

echo "== 5) Waiting for async processing... =="
sleep 3

echo "== 6) Price for ${DATE} AFTER sold-out (should be higher) =="
curl -s "${BASE}/api/v1/flights/calendar?origin=${ORIGIN}&destination=${DEST}&month=${MONTH}&currency=MYR" \
  | jq --arg d "${DATE}" '.days[] | select(.date == $d)'

echo "== Cache metrics =="
curl -s "${BASE}/actuator/prometheus" | grep -E "lowfare_cache" || true
