#!/bin/sh
# Get the current UTC time in ISO 8601 format for the publishedAt field.
current_time=$(date -u +"%Y-%m-%dT%H:%M:%SZ")

# Read the newsletter.html file and convert it to valid UTF-8.
# This will ensure that emojis (utf8mb4 characters) are preserved.
newsletter_html=$(iconv -f UTF-8 -t UTF-8 newsletter.html | jq -Rs .)

# Write the JSON payload to a file in the current directory.
tmpfile="./newsletter_payload.json"
cat <<EOF > "$tmpfile"
{
  "type": "EmailDTO",
  "publishedAt": "$current_time",
  "html": $newsletter_html
}
EOF

## (Optional) Output the generated JSON payload for verification.
#echo "Generated JSON Payload:"
#jq . "$tmpfile"

# Send the JSON payload to the /parse-email endpoint using curl.
curl -X POST http://localhost:8082/parse-email \
  -H "Content-Type: application/json" \
  -d @"$tmpfile" | jq .
