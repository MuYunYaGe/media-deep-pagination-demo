#!/usr/bin/env sh
set -eu

BASE_URL=${1:-http://localhost:8080}
CATEGORY_ID=${2:-1001}
PAGE=${3:-3000}
SIZE=${4:-10}
WARMUPS=${5:-5}
ITERATIONS=${6:-30}

if [ "$ITERATIONS" -lt 1 ] || [ "$WARMUPS" -lt 0 ]; then
  echo "warmups must be non-negative and iterations must be positive" >&2
  exit 2
fi

RESULT_DIR=$(mktemp -d)
trap 'rm -rf "$RESULT_DIR"' EXIT INT TERM

measure_strategy() {
  strategy=$1
  uri="$BASE_URL/api/v1/categories/$CATEGORY_ID/media?strategy=$strategy&page=$PAGE&size=$SIZE"
  warmup=0
  while [ "$warmup" -lt "$WARMUPS" ]; do
    curl --fail --silent --output /dev/null "$uri"
    warmup=$((warmup + 1))
  done

  result_file="$RESULT_DIR/$strategy.txt"
  iteration=0
  while [ "$iteration" -lt "$ITERATIONS" ]; do
    curl --fail --silent --output /dev/null --write-out '%{time_total}\n' "$uri" >> "$result_file"
    iteration=$((iteration + 1))
  done

  sort -n "$result_file" -o "$result_file"
  awk -v strategy="$strategy" '
    { values[NR] = $1 * 1000 }
    END {
      median = (NR % 2 == 0) ? (values[NR / 2] + values[NR / 2 + 1]) / 2 : values[(NR + 1) / 2]
      p95_index = int(NR * 0.95 + 0.999999)
      if (p95_index < 1) p95_index = 1
      printf "%-7s min=%8.3f ms median=%8.3f ms p95=%8.3f ms iterations=%d\n", strategy, values[1], median, values[p95_index], NR
    }
  ' "$result_file"
}

echo "Client-observed timings; results depend on this machine, data distribution, network, and cache warmth."
measure_strategy offset
measure_strategy zset
