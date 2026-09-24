# ADR-009: Link Previews Are Included by nginx

## Status
Accepted

## Context

A pasted event page link should draw that event's **link preview** in Discord,
WhatsApp, Telegram, Signal, Slack and iMessage. Those apps read OpenGraph tags
from the HTML the server sends. They run no script, so the tags the SPA could
set after load never reach them. The frontend image is static nginx with no
Node runtime, and nothing rendered HTML per request.

Alternatives considered:

- **Crawler user-agent sniffing**, sending known bots to an api-rendered page.
  The list rots, and Signal fetches previews from the sender's phone with a
  plain browser user agent, so it would miss.
- **The api serving the whole entry document** for event addresses. That ties
  the api image to the frontend build's hashed asset names.
- **Server-side rendering or prerendering.** A Node runtime or a build per
  event change, for a dozen tags.

## Decision

**The frontend's nginx includes the event's tags into `index.html` with SSI,
from an api endpoint, for every visitor.**

- `/events/<id>` and `/events?event=<id>` name the event. A fragment never
  reaches the server, so `/events#<id>` keeps the generic preview.
- `index.html` carries the generic tags in an SSI block, and the include falls
  back to that block on any failure: a hidden or missing event, an api error,
  or a timeout of one second.
- The subrequest goes out anonymous, without cookies or authorization, so the
  tags are those a crawler would get. The event's own read rule decides.
- `GET /events/{id}/link-preview` renders the tags with Thymeleaf. It is
  hidden from the OpenAPI spec, since nginx is its only caller.
- The api writes display text here (the date line and the snippet), an
  exception to the frontend composing display strings, as emails already are.

## Consequences

- The entry document loses its ETag and Last-Modified, because SSI output
  differs per event. It is small and already served `no-cache`.
- The frontend pod calls the api Service directly in production, not only in
  the compose stack.
- The system tests serve the bundle with `vite preview`, which runs no SSI.
  The image is checked instead by `services/frontend/tests/nginx/link-preview.sh`
  in the image build job.
- Chat apps cache previews, so an edited event can show its old preview in a
  chat for a while.

## Related Documentation
- [Architecture ADR index](ADR-INDEX.md)
- `docs/CONTEXT.md`: Event page, Link preview
