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

cd Docker || exit
if [ "$FORCE" = true ]; then
  ./spread.sh -f
else
  ./spread.sh
fi
cd ..

docker compose -f docker-compose.build.yml up --build -d

echo "Docker containers started"