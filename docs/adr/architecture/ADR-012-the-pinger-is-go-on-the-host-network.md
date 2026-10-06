# ADR-012: The Pinger Is Go, on the Host Network

## Status
Accepted

## Context

SNTPings paints one pixel of a shared canvas for every IPv6 echo request sent to
`<prefix>:<X>:<Y>:<B><G>:<R><A>`. The event runs for one weekend. We want the Blueshell
logo on the canvas, a members-only page that shows progress, and admins able to set the
prefix and the rate. The event bans prefixes that send too hard.

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
standard library covers server-rendered HTML, and `golang.org/x/net/icmp` covers the
socket. It is the repo's second backend language. It shares nothing with the api's Gradle
build and has its own Validate bucket.

**The pod runs on the host network,** so pings leave from the node's IPv6 address. It runs
as root with every capability dropped except `NET_RAW`, no privilege escalation and a
read-only root filesystem. Its page listens on `:8090` on the node. The NixOS firewall opens
that port on `cni0` only, so Traefik can reach it and the internet cannot.

**A settings change asks the api, not the header.** Forward-auth gates the host at MEMBER.
Any pod in the cluster can reach the node's port with a forged `X-User-Groups`, and a
NetworkPolicy does not cover a host-network pod. So a change replays the caller's cookies
to `/oauth2/forward-auth` and needs ADMIN in the api's answer. Cross-origin posts are
refused, because the session cookie is `SameSite=None`.

## Consequences

- One Deployment in the cluster runs with the node's network namespace. Remove it, its
  ingress and the `cni0` rule once the event is over.
- The page and the sender share a process, so a pod restart pauses painting. Settings live
  in Valkey and survive a restart.
- A second language means a second toolchain in CI: `actions/setup-go` and `go test`.
