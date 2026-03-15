#!/bin/sh

# Optional: Wait for the database to be ready
echo "Waiting for the database..."
until nc -z -v -w30 db 3306
do
  echo "Waiting for MySQL database connection..."
  sleep 5
done
echo "MySQL is up and running!"
