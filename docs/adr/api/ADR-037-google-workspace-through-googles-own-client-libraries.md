# ADR-037: Google Workspace Through Google's Own Client Libraries

## Status
Proposed

## Context

The api is to create per-committee groups and shared drives in the association's Google
Workspace, and keep committee, activist and member groups in step. Brevo and Discord are
reached through clients the association publishes itself (`brevo-client`,
`discord-client`), regenerated nightly because the vendors publish no maintained Java
client, or one that lags their API.

Google does publish one. `google-api-services-admin-directory` and
`google-api-services-drive` are generated from Google's discovery documents and appear on
Maven Central within days of each API revision.

## Decision

**The api reaches Google Workspace through Google's own client libraries, behind a port of
its own, as the Brevo adapter sits behind `ContactListAdapter`.**

- A service account with domain-wide delegation acts for a super-admin. Its key is read from
  Vault like every other api secret.
- Groups hold members' own email addresses; nobody needs a Workspace account of the
  association's.

The reason for a client repo of our own is a vendor library that falls behind. If Google's
releases stop following its API revisions, the Workspace client moves into its own repo on
the same pattern as the other two, with the same schema-derived surface, and the port is
what keeps that move out of the domain code.

Alternatives considered:

- **A third client repo now.** It would repeat, and trail, what Google already regenerates
  from the same discovery documents.
