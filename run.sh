#!/bin/sh

# Define the source and destination directories
SOURCE_DIR="$HOME/.m2/repository"
DEST_DIR="./.m2"

# Create the destination directory if it doesn't exist
mkdir -p "$DEST_DIR"

# Copy the Maven repository
cp -r "$SOURCE_DIR" "$DEST_DIR"

echo "Maven repository copied to $DEST_DIR"

cd Docker || exit
./spread.sh

cd ..
docker-compose up --build -d

echo "Docker containers started"