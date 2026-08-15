#!/usr/bin/env bash
set -euo pipefail

kibana_url="${KIBANA_URL:-http://localhost:5601}"
setup_dir=$(cd -- "$(dirname -- "$0")" && pwd)

until curl -fsS "$kibana_url/api/status" >/dev/null; do
  sleep 2
done

import_response=$(curl -fsS -X POST "$kibana_url/api/saved_objects/_import?overwrite=true" \
  -H 'kbn-xsrf: true' \
  --form "file=@$setup_dir/saved-objects.ndjson")
jq -e '.success == true and .successCount == 9' <<<"$import_response" >/dev/null

create_rule() {
  local rule_id="$1"
  local rule_name="$2"
  local query="$3"
  local threshold="$4"

  curl -sS -X DELETE "$kibana_url/api/alerting/rule/$rule_id" -H 'kbn-xsrf: true' >/dev/null
  rule_body=$(jq -nc \
    --arg name "$rule_name" \
    --arg query "$query" \
    --argjson threshold "$threshold" \
    '{
      name: $name,
      consumer: "stackAlerts",
      rule_type_id: ".es-query",
      enabled: true,
      tags: ["journeyops", "logs"],
      schedule: {interval: "1m"},
      actions: [],
      params: {
        searchType: "esQuery",
        esQuery: $query,
        index: ["journeyops-*"],
        timeField: "@timestamp",
        aggType: "count",
        groupBy: "top",
        termField: "service.name",
        termSize: 10,
        thresholdComparator: ">",
        threshold: [$threshold],
        timeWindowSize: 5,
        timeWindowUnit: "m",
        size: 100
      }
    }')
  curl -fsS -X POST "$kibana_url/api/alerting/rule/$rule_id" \
    -H 'Content-Type: application/json' \
    -H 'kbn-xsrf: true' \
    -d "$rule_body" >/dev/null
}

create_rule \
  journeyops-5xx-burst \
  'JourneyOps 5xx Burst' \
  '{"query":{"bool":{"filter":[{"term":{"event.dataset":"journeyops.http"}},{"range":{"http.response.status_code":{"gte":500}}}]}}}' \
  4
create_rule \
  journeyops-upstream-failure-burst \
  'JourneyOps Upstream Failure Burst' \
  '{"query":{"term":{"error.code":"UPSTREAM_UNAVAILABLE"}}}' \
  2

data_view_count=$(curl -fsS "$kibana_url/api/saved_objects/_find?type=index-pattern&per_page=100" | jq '[.saved_objects[] | select(.id == "journeyops")] | length')
search_count=$(curl -fsS "$kibana_url/api/saved_objects/_find?type=search&per_page=100" | jq '[.saved_objects[] | select(.id | startswith("journeyops-"))] | length')
dashboard_count=$(curl -fsS "$kibana_url/api/saved_objects/_find?type=dashboard&per_page=100" | jq '[.saved_objects[] | select(.id | startswith("journeyops-"))] | length')
rule_count=$(curl -fsS "$kibana_url/api/alerting/rules/_find?per_page=100" | jq '[.data[] | select(.id | startswith("journeyops-"))] | length')

[[ "$data_view_count" == "1" ]]
[[ "$search_count" == "5" ]]
[[ "$dashboard_count" == "3" ]]
[[ "$rule_count" == "2" ]]

printf 'Kibana ready: 1 data view, 5 saved searches, 3 dashboards, 2 rules\n'
