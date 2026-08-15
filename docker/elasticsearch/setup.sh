#!/bin/sh
set -eu

elasticsearch_url="${ELASTICSEARCH_URL:-http://localhost:9200}"
setup_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)

until curl -fsS "$elasticsearch_url/_cluster/health" >/dev/null; do
  sleep 2
done

curl -fsS -X PUT "$elasticsearch_url/_ilm/policy/journeyops-http-14d" \
  -H 'Content-Type: application/json' \
  -d '{"policy":{"phases":{"delete":{"min_age":"14d","actions":{"delete":{}}}}}}'
curl -fsS -X PUT "$elasticsearch_url/_ilm/policy/journeyops-domain-90d" \
  -H 'Content-Type: application/json' \
  -d '{"policy":{"phases":{"delete":{"min_age":"90d","actions":{"delete":{}}}}}}'
curl -fsS -X PUT "$elasticsearch_url/_component_template/journeyops-common" \
  -H 'Content-Type: application/json' --data-binary "@$setup_dir/component-template.json"
curl -fsS -X PUT "$elasticsearch_url/_index_template/journeyops-http" \
  -H 'Content-Type: application/json' --data-binary "@$setup_dir/http-index-template.json"
curl -fsS -X PUT "$elasticsearch_url/_index_template/journeyops-domain" \
  -H 'Content-Type: application/json' --data-binary "@$setup_dir/domain-index-template.json"
curl -fsS -X PUT "$elasticsearch_url/_ingest/pipeline/journeyops-redact" \
  -H 'Content-Type: application/json' --data-binary "@$setup_dir/ingest-pipeline.json"

simulation=$(curl -fsS -X POST "$elasticsearch_url/_ingest/pipeline/journeyops-redact/_simulate" \
  -H 'Content-Type: application/json' \
  -d '{"docs":[{"_source":{"message":"phone 010-1234-5678 rrn 900101-1234567 email theo@example.com card 1234-5678-9012-3456 token Bearer secret-token jwt eyJabcdefghijk.abcdefghijk.abcdefghijk","custom":{"email":"theo@example.com"}}}]}')

printf '%s' "$simulation" | grep -F '010-****-5678' >/dev/null
printf '%s' "$simulation" | grep -F '900101-*******' >/dev/null
printf '%s' "$simulation" | grep -F 't***@example.com' >/dev/null
printf '%s' "$simulation" | grep -F '123456******3456' >/dev/null
printf '%s' "$simulation" | grep -F 'Bearer [REDACTED]' >/dev/null
printf '%s' "$simulation" | grep -F '[JWT_REDACTED]' >/dev/null

printf '%s\n' "$simulation"
