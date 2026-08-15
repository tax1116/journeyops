#!/usr/bin/env bash
set -euo pipefail

user_api_url="${USER_API_URL:-http://localhost:8081}"
application_api_url="${APPLICATION_API_URL:-http://localhost:8082}"
evaluation_api_url="${EVALUATION_API_URL:-http://localhost:8083}"
contract_api_url="${CONTRACT_API_URL:-http://localhost:8084}"
elasticsearch_url="${ELASTICSEARCH_URL:-http://localhost:9200}"

post_with_token() {
  local url="$1"
  local token="$2"
  curl -fsS -X POST "$url" -H "Authorization: Bearer $token"
}

phone_response=$(curl -fsS -X POST "$user_api_url/api/v1/phone-verifications" \
  -H 'Content-Type: application/json' \
  -d '{"phoneNumber":"010-1234-5678"}')
token=$(jq -er '.accessToken' <<<"$phone_response")

application_response=$(post_with_token "$application_api_url/api/v1/loan-applications" "$token")
application_id=$(jq -er '.applicationId' <<<"$application_response")

post_with_token "$evaluation_api_url/api/v1/loan-applications/$application_id/limit-inquiries" "$token" \
  | jq -e '.state == "OFFER_PROVIDED"' >/dev/null
post_with_token "$application_api_url/api/v1/loan-applications/$application_id/submit" "$token" \
  | jq -e '.state == "APPLICATION_SUBMITTED"' >/dev/null
post_with_token "$user_api_url/api/v1/loan-applications/$application_id/identity-verifications" "$token" \
  | jq -e '.state == "IDENTITY_VERIFIED"' >/dev/null
post_with_token "$application_api_url/api/v1/loan-applications/$application_id/documents" "$token" \
  | jq -e '.state == "DOCUMENTS_SUBMITTED"' >/dev/null
post_with_token "$evaluation_api_url/api/v1/loan-applications/$application_id/evaluations" "$token" \
  | jq -e '.state == "EVALUATION_APPROVED"' >/dev/null
post_with_token "$contract_api_url/api/v1/loan-applications/$application_id/contracts" "$token" \
  | jq -e '.state == "CONTRACT_SIGNED"' >/dev/null
post_with_token "$contract_api_url/api/v1/loan-applications/$application_id/payments" "$token" \
  | jq -e '.state == "PAID"' >/dev/null

application_query=$(jq -nc --arg application_id "$application_id" '{
  size: 100,
  sort: [{"@timestamp": "asc"}],
  query: {term: {"loan.application.id": $application_id}}
}')

event_count=0
search_response=''
for _ in $(seq 1 30); do
  curl -fsS -X POST "$elasticsearch_url/journeyops-domain-*/_refresh" >/dev/null 2>&1 || true
  search_response=$(curl -fsS -X POST "$elasticsearch_url/journeyops-domain-*/_search" \
    -H 'Content-Type: application/json' \
    -d "$application_query" 2>/dev/null || true)
  event_count=$(jq -r '.hits.total.value // 0' <<<"${search_response:-null}")
  [[ "$event_count" == "8" ]] && break
  sleep 2
done

if [[ "$event_count" != "8" ]]; then
  printf 'Expected 8 application events, found %s for %s\n' "$event_count" "$application_id" >&2
  exit 1
fi

expected_actions=$(printf '%s\n' \
  loan-application-created \
  loan-offer-provided \
  loan-application-submitted \
  identity-card-verified \
  loan-documents-submitted \
  loan-evaluation-approved \
  loan-contract-signed \
  loan-payment-completed | sort)
actual_actions=$(jq -r '.hits.hits[]._source.event.action' <<<"$search_response" | sort)
[[ "$actual_actions" == "$expected_actions" ]]

trace_count=$(jq '[.hits.hits[]._source.trace.id? | select(type == "string" and length > 0)] | length' <<<"$search_response")
custom_count=$(jq '[.hits.hits[]._source.custom? | select(type == "object" and length > 0)] | length' <<<"$search_response")
[[ "$trace_count" -ge 1 ]]
[[ "$custom_count" -ge 1 ]]

mask_marker="masking-$application_id"
mask_document=$(jq -nc --arg marker "$mask_marker" '{
  "@timestamp": (now | todateiso8601),
  message: "phone 010-1234-5678 rrn 900101-1234567 email theo@example.com card 1234-5678-9012-3456 token Bearer secret-token jwt eyJabcdefghijk.abcdefghijk.abcdefghijk",
  event: {dataset: "journeyops.smoke"},
  custom: {marker: $marker, email: "theo@example.com"}
}')
curl -fsS -X POST "$elasticsearch_url/journeyops-app-smoke/_doc?pipeline=journeyops-redact&refresh=true" \
  -H 'Content-Type: application/json' \
  -d "$mask_document" >/dev/null
mask_query=$(jq -nc --arg marker "$mask_marker" '{query: {term: {"custom.marker": $marker}}}')
masked_source=$(curl -fsS -X POST "$elasticsearch_url/journeyops-app-smoke/_search" \
  -H 'Content-Type: application/json' \
  -d "$mask_query" | jq -cer '.hits.hits[0]._source')

jq -e '
  .message | contains("010-****-5678") and
  contains("900101-******* ") and
  contains("t***@example.com") and
  contains("123456******3456") and
  contains("Bearer [REDACTED]") and
  contains("[JWT_REDACTED]")
' <<<"$masked_source" >/dev/null
! grep -Eq '010-1234-5678|900101-1234567|theo@example.com|1234-5678-9012-3456|secret-token|eyJabcdefghijk' <<<"$masked_source"

printf 'Journey completed: %s, 8 application events indexed\n' "$application_id"
