# ADR-035: A Member Signs a Desktop Pinger Client In, and the Report Chain Takes a Bearer

## Status

Accepted. Amends [ADR-030](ADR-030-a-sign-in-is-a-server-side-record.md).

## Context

The pinger epic needs two callers to reach the same reporting endpoints as themselves. A member
runs a desktop client that has no browser and no cookie jar, and the always-on SiteCie painter
runs unattended with no person behind it at all.

Two rules the API already holds get in the way of the member's client:

- Every client registered with the authorization server is an admin tool, so
  `DownstreamClientAuthorizationFilter` refuses an authorization request from anyone without the
  admin role and, for a two-factor account, forces a fresh code every time. A plain member can
  clear neither bar.
- The cookie is the only credential the site accepts (ADR-030). `JwtAuthFilter` reads the
  `BSH_AUTH` cookie and nothing else, and a bearer header is refused. A desktop client cannot hold
  the site's cookie, and the painter holds no sign-in to make one from.

The painter has no person to sign in at all. It needs a credential of its own that is not a member
token.

## Decision

**A new `pinger-app` public client, a per-client authorization policy, and one bearer-accepting
security chain scoped to `/pinger/report/**`.**

- **`pinger-app` is a public client with PKCE and a loopback redirect.** It carries no secret,
  because the secret would live on the member's own machine. The client binds an ephemeral port on
  `127.0.0.1` and the authorization server allows any port for a loopback redirect (RFC 8252), so
  the registered port is a placeholder the request overrides.
- **`DownstreamClientAuthorizationFilter` branches on `client_id`.** For `pinger-app` it requires
  the member role and skips the forced two-factor step-up. For every other client it stays
  admin-only with a fresh code, unchanged.
- **One security chain scoped to `/pinger/report/**` accepts a bearer.** It validates the member's
  access token as a resource server and reads the token's roles into authorities, and it accepts
  SiteCie's `X-Pinger-Service-Token` header through a filter of its own. The header is compared in
  constant time against a value Vault provisions (ADR-033); an empty configured value turns the
  header off, which is what every environment but production holds. The chain keeps no session and
  carries no CSRF token, because neither caller is a browser.
- **The main site chain stays cookie-only.** The bearer exception lives only on this one matcher.
  A `pinger-app` token buys nothing anywhere else, because nothing else accepts a bearer.

## Why branching on `client_id` is safe here

ADR-030's gate deliberately did not branch on `client_id`, because letting that parameter decide
whether a check runs hands an attacker the switch that turns it off (CWE-807). The narrow exception
holds because the weaker policy never reaches a stronger resource:

- A member-grade `pinger-app` token is honoured by nothing but the member-scoped report chain, and
  that chain grants no more than a member already has.
- The authorization code is delivered only to the loopback redirect the member's own machine owns,
  validated against the registered client, so the parameter cannot be used to redirect an admin
  tool's code elsewhere.

The admin branch therefore still stands for every client but `pinger-app`.

## Consequences

- ADR-030's "the cookie is the only carrier" now has one exception, stated by its matcher:
  `/pinger/report/**` accepts a bearer and the service header. The rest of the site is unchanged, so
  a script on a page still cannot read a credential.
- The production service token is provisioned in Vault under `secret/api`, key
  `pinger.report.service-token`. Nobody sets it in the repository; the empty default leaves the
  service header off until Vault fills it.
- The member bearer is a short-lived access token the authorization server already mints. The report
  chain trusts its signature and roles claim, so a change to how roles reach the token reaches this
  chain too.
- This is the walking skeleton the rest of the epic stands on: `GET /pinger/report/whoami` returns
  the resolved identity and nothing more. The real reporting payloads come later.

## Related

- [ADR-030: A Sign-In Is a Server-Side Record](ADR-030-a-sign-in-is-a-server-side-record.md) — the
  cookie-only rule this amends
- [ADR-031: Two-Factor Authentication](ADR-031-two-factor-authentication.md) — the step-up
  `pinger-app` skips
- [ADR-033: The API Reads Its Secrets From Vault](ADR-033-the-api-reads-its-secrets-from-vault.md) —
  where the service token comes from
