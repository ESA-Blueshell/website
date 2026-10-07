# ADR-039: Sending Addresses Keep Their Logins in Vault

## Status
Proposed

## Context

The site sends all its mail from one address, with one SMTP login read from configuration
(`spring.mail`, ADR-033). The board wants to send a mailing from another address, such as the
events committee's own, which logs in to its own SMTP server with its own username and password.
Those logins are added and changed by an admin on the site, not by an operator in Vault.

Until now the api only reads from Vault KV: its secrets are written by people (ADR-033). Keeping a
login the site itself takes in means the api writes to Vault for the first time.

The alternatives were to keep the password in the database, sealed by Transit like the address
(ADR-038), or to have an operator put each login into Vault by hand. A sealed password in the
database is opened on every send, and a stolen dump plus a Vault token opens all of them. A hand-put
login takes the board's decision out of the board's hands and needs an operator for every change.

## Decision

**A sending address is a record in the api; its login is in Vault KV.** The record holds the
address, a display name, the SMTP host and port, the transport security and whether it is the
default for writing. The username and password are never in the database. Each address has one
entry at `secret/data/api/sending/<id>`, holding `username` and `password`.

**The api may write that path and nothing else.** Its policy gains create, read, update and delete
on `secret/data/api/sending/*`, and delete on `secret/metadata/api/sending/*` so a removed address
leaves no versions behind. Everything else the api reads from KV stays read-only.

**A password is write-only.** The api never answers it back. The form shows that one is set and
offers to replace it.

**A login is tested before it is kept.** Adding an address, or replacing its login, opens a
connection to its SMTP server with the credentials given. Only a login the server accepts is
written to Vault; a refusal says what the server said.

**A kept login only goes to the server it was tested on.** Changing an address's host, port or
security needs the username and password again, tested against the new server. The api never
replays a kept login to a destination nobody just logged in to, since whoever runs that server
would receive it.

**A login crosses the wire encrypted.** STARTTLS is required rather than attempted, the server's
certificate must name its host, and a server without encryption is accepted only on the site's own
network (a loopback, private or link-local address).

**Only an admin adds, replaces or removes an address.** A board member picks one when writing an
email. The site's own mail, such as security notifications and payment emails, keeps the address in
configuration.

**The transport picks its sender per email**, and reads the login from Vault when it sends. Vault
out of reach, or a login gone missing, fails that email into the outbox's failed state with the
reason, as any transport failure does. Mail from other addresses is not held up.

**Each sent email records the address it went out from**, so the Sent list can say so.

**Dev and test use a stand-in** that keeps logins in memory under the same contract, as the sealing
stand-in does (ADR-038). Production refuses to start a send from an added address without Vault.

## Consequences

- A database copy holds no SMTP password. The logins are as safe as the api's Vault token, which
  already reads every other secret the api holds.
- The api's token can now change secrets under one path. A compromised api can replace or remove
  sending logins, and nothing else in Vault.
- Removing an address removes its login from Vault, including its earlier versions.
- Replies to an added address do not reach the site's inbox; the inbox reads one mailbox.
- Sender authentication (SPF, DKIM) for an address on another domain is that domain owner's
  concern. The form says so.
- The Vault bootstrap script and the operator's policy documentation change with this decision.

## Related Documentation
- [ADR-033: The Api Reads Its Secrets From Vault](ADR-033-the-api-reads-its-secrets-from-vault.md)
- [ADR-038: Private Details Are Sealed by Vault Transit](ADR-038-private-details-are-sealed-by-vault-transit.md)
- [ADR umbrella index](../ADR-INDEX.md)
- [AGENTS.md](../../../AGENTS.md)
