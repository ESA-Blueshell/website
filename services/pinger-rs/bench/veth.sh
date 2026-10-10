#!/bin/sh
# Routes the documentation prefix into a veth pair inside the container's own network namespace, so
# every frame dies on the peer and nothing leaves the host. The next hop's MAC is not the peer's,
# so the peer drops each frame as soon as it arrives.
set -eu
ip link add veth0 type veth peer name veth1
ip link set veth0 up
ip link set veth1 up
ip -6 addr add fd00::1/64 dev veth0 nodad
ip -6 neigh add fd00::2 lladdr 02:00:00:00:00:02 dev veth0 nud permanent
ip -6 route add 2001:db8:b317:a000::/64 via fd00::2 dev veth0
exec "$@"
