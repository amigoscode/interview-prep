#!/usr/bin/env bash
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

echo "Starting infrastructure (MongoDB + Redpanda)..."
docker compose up -d

echo "Waiting for services to be ready..."
sleep 5

echo ""
echo "Infrastructure ready:"
echo "  MongoDB:          localhost:27017"
echo "  Kafka (Redpanda): localhost:9092"
echo "  Redpanda Console: http://localhost:8090"
