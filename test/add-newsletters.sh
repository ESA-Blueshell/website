#!/bin/bash
# Loop through each newsletter file in the newsletters folder.
for file in newsletters/Blueshell\ Newsletter*.html; do
  # Extract the base filename.
  filename=$(basename "$file")

  # Remove the known prefix and suffix to extract the date part.
  # For example: "Blueshell Newsletter 2023-10-1.html" => "2023-10-1"
  date_part=${filename#"Blueshell Newsletter "}
  date_part=${date_part%".html"}

  # Split the date part into year, month, and day.
  year=$(echo "$date_part" | cut -d '-' -f1)
  month=$(echo "$date_part" | cut -d '-' -f2)
  day=$(echo "$date_part" | cut -d '-' -f3)

  # Ensure month and day are two digits (pad with a zero if needed).
  month=$(printf "%02d" "$month")
  day=$(printf "%02d" "$day")

  # Create the publishedAt timestamp with a fixed time of 12:00:00 UTC.
  published_at="${year}-${month}-${day}T12:00:00Z"

  # Convert the newsletter file's content to valid UTF-8 and wrap it as a JSON string.
  newsletter_html=$(iconv -f UTF-8 -t UTF-8 "$file" | jq -Rs .)

  # Create a JSON payload file. The filename includes the date to avoid overwriting.
  json_file="./payloads/newsletter_payload_${year}-${month}-${day}.json"
  mkdir -p './payloads'
  cat <<EOF > "$json_file"
{
  "type": "EmailDTO",
  "publishedAt": "$published_at",
  "html": $newsletter_html
}
EOF

  echo "Sending payload for $filename (publishedAt: $published_at)..."
  # Send the JSON payload to the /parse-email endpoint using curl.
  curl --insecure -X POST https://localhost/api/email \
    -H "Content-Type: application/json" \
    -d @"$json_file" | jq .
done
