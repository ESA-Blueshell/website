# ADR-012: The Pinger Is Go, on the Host Network

## Status
Accepted

## Context

SNTPings paints one pixel of a shared canvas for every IPv6 echo request sent to
`<prefix>:<X>:<Y>:<B><G>:<R><A>`. The event runs for one weekend. We want an image on the
canvas, a public page that shows progress, and an admin able to set the prefix, the rate, the
image and where it lands. The event bans prefixes that send too hard.

The paint job — the prefix, the rate, the image and the box it fills — is owned by the api and
set from the main site, where an admin uploads the image and drags its box over a preview. The
api serves the descriptor publicly at `GET /pinger/paint` and the image through its file module,
so the pinger and a distributable helper both read the same source of truth. This pod is the
sender and the watch page, nothing else: it holds no settings and no admin controls.

Two facts shape where the sender runs:

- **Pods have no IPv6.** k3s runs single-stack IPv4. The node has a public IPv6 address.
- **A raw ICMPv6 socket needs `NET_RAW`.** Kubernetes cannot grant a capability to a
  non-root user, because it has no ambient capabilities.

Alternatives considered:

- **Rust**, as first proposed. No Rust toolchain exists in the repo or on the
  maintainers' machines, and the sender's limit is the rate cap we set, not language speed.
- **Turning on dual-stack networking in k3s.** It changes networking for the whole cluster
  days before the event, to serve one weekend's tool.
- **Running from a laptop.** Then there is no SSO page and no shared settings.

## Decision

**The pinger is a Go service in `services/pinger`.** Go was already installed, its
standard library covers server-rendered HTML and `golang.org/x/net/icmp` covers the
socket. It is the repo's second backend language. It shares nothing with the api's Gradle
build and has its own Validate bucket.

**The pod runs on the host network,** so pings leave from the node's IPv6 address. It runs
as root with every capability dropped except `NET_RAW`, no privilege escalation and a
read-only root filesystem. Its page listens on `:8090` on the node. The NixOS firewall opens
that port on `cni0` only, so Traefik can reach it and the internet cannot.

**The page only watches; the api owns the settings.** The page is public — its IngressRoute
carries no forward-auth, so anonymous viewers see the live canvas — and it has no form and no
admin check at all. Every setting is edited on the main site, against the api's own ADMIN-gated
endpoint, so the node's port never has to trust a header no NetworkPolicy can cover on a
host-network pod. The pod polls `GET /pinger/paint`, fetches the image it names and rebuilds the
placed pixels when the descriptor changes.

## Consequences

- One Deployment in the cluster runs with the node's network namespace. Remove it, its
  ingress and the `cni0` rule once the event is over.
- The page and the sender share a process, so a pod restart pauses painting. The settings come
  back from the api on the next poll; only the running totals live in Valkey, so the counts
  survive a restart.
- A second language means a second toolchain in CI: `actions/setup-go` and `go test`.
