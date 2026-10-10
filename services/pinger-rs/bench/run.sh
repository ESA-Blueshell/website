#!/bin/sh
# Benchmarks the Go sender against pinger-rs on a veth pair, with one CPU and with all of them.
# Run from the repository root: services/pinger-rs/bench/run.sh [seconds]
set -eu
secs=${1:-10}
docker build -q -t pinger-bench -f services/pinger-rs/bench/Dockerfile services >/dev/null

run() {
	label=$1
	cpus=$2
	shift 2
	limit=""
	[ "$cpus" = all ] || limit="--cpus=$cpus"
	# shellcheck disable=SC2086
	out=$(docker run --rm $limit --cap-drop ALL --cap-add NET_RAW --cap-add NET_ADMIN \
		--sysctl net.ipv6.conf.all.disable_ipv6=0 --sysctl net.ipv6.conf.default.disable_ipv6=0 \
		pinger-bench "$@" | grep '^result')
	printf '%-34s cpus=%-4s %s\n' "$label" "$cpus" "$out"
}

prefix=2001:db8:b317:a000::
for cpus in 1 all; do
	run "go (sendmmsg, current)" "$cpus" gobench -prefix $prefix -duration "${secs}s"
	run "rust ring, 1 worker" "$cpus" pinger-rs --synthetic 450000 --prefix $prefix --rate 100000000 --duration "$secs"
	run "rust sendmmsg/64, 1 worker" "$cpus" pinger-rs --mode mmsg --synthetic 450000 --prefix $prefix --rate 100000000 --duration "$secs"
done
run "rust ring, 4 workers" all pinger-rs --workers 4 --synthetic 450000 --prefix $prefix --rate 100000000 --duration "$secs"
for rate in 200000 1000000; do
	run "go @ $rate" all gobench -prefix $prefix -duration "${secs}s" -rate $rate
	run "rust ring @ $rate" all pinger-rs --synthetic 450000 --prefix $prefix --rate $rate --duration "$secs"
	run "rust sendmmsg/64 @ $rate" all pinger-rs --mode mmsg --synthetic 450000 --prefix $prefix --rate $rate --duration "$secs"
	run "go @ $rate" 1 gobench -prefix $prefix -duration "${secs}s" -rate $rate
	run "rust ring @ $rate" 1 pinger-rs --synthetic 450000 --prefix $prefix --rate $rate --duration "$secs"
done
