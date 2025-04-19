#!/bin/sh

# Check for the -f flag
FORCE=false
while getopts "f" opt; do
  case $opt in
    f)
      FORCE=true
      ;;
    *)
      ;;
  esac
done

if [ "$FORCE" = true ]; then
  ./spread.sh -f
else
  ./spread.sh
fi

docker compose -f docker/docker-compose.build.yml up --build -d

echo "Docker containers started"