# ADR-039: Sending Addresses Keep Their Logins in Vault

## Status
Proposed

## Context

The site sends all its mail from one address, with one SMTP login read from configuration
(`spring.mail`, ADR-033). The board wants to send from other addresses too, such as the events
committee's own, each logging in to its own mail servers with its own username and password, and
to read the mail that comes back to them. Those logins are added and changed by the board on the
site, not by an operator in Vault.

Until now the api only reads from Vault KV: its secrets are written by people (ADR-033). Keeping a
login the site itself takes in means the api writes to Vault for the first time.

The alternatives were to keep the password in the database, sealed by Transit like the address
(ADR-038), or to have an operator put each login into Vault by hand. A sealed password in the
database is opened on every send, and a stolen dump plus a Vault token opens all of them. A hand-put
login takes the board's decision out of the board's hands and needs an operator for every change.

## Decision

**A sending address is a record in the api; its login is in Vault KV.** The record holds the
address, a display name, the SMTP host, port and security, the IMAP host, port and security where
the address is read, whether it is the default, and what its last check found. The username and
password are never in the database. One login serves SMTP and IMAP alike. Each address has one
entry at `secret/data/api/sending/<id>`, holding `username` and `password`.

**The api may write that path and nothing else.** Its policy gains create, read, update and delete
on `secret/data/api/sending/*`, and delete on `secret/metadata/api/sending/*` so a removed address
leaves no versions behind. Everything else the api reads from KV stays read-only.

**A password is write-only.** The api never answers it back. The form shows that one is set and
offers to replace it.

**A login is tested before it is kept.** Adding an address, or replacing its login, opens a
connection to its SMTP server, and to its IMAP server where it has one, with the credentials given.
Only a login both servers accept is written to Vault; a refusal says which server refused and what
it said.

**A kept login only goes to the servers it was tested on.** Changing an address's SMTP or IMAP
host, port or security, or dropping its IMAP server, needs the username and password again, tested
against the servers as they will be. The api never
replays a kept login to a destination nobody just logged in to, since whoever runs that server
would receive it.

**A login crosses the wire encrypted.** STARTTLS is required rather than attempted, the server's
certificate must name its host, and a server without encryption is accepted only on the site's own
network (a loopback, private or link-local address).

**Every address is checked, and the page says so.** Each night, and whenever a board member asks,
the api tries both servers with the login kept and records whether it could send, whether it could
read and what a refusing server said. A failed check is an answer, never an error.

**The board keeps the addresses.** Any board member adds, replaces and removes an address and moves
the default mark. Picking an address when writing an email is the board's too.

**The default address sends the site's own mail.** Security notifications, payment emails and every
other email the site writes itself go out from the address marked default, with its login. While no
address is marked, they go out from the address in configuration, as before.

**The transport picks its sender per email**, and reads the login from Vault when it sends. Vault
out of reach, or a login gone missing, fails that email into the outbox's failed state with the
reason, as any transport failure does, and it can be sent again from there. That holds for the site's
own mail once a default is marked: a sign-in email waits on Vault as every other email does.

**Each sent email records the address it went out from**, so the Sent list can say so.

**Dev and test use a stand-in** that keeps logins in memory under the same contract, as the sealing
stand-in does (ADR-038). Production refuses to start a send from an added address without Vault.

## Consequences

- A database copy holds no SMTP password. The logins are as safe as the api's Vault token, which
  already reads every other secret the api holds.
- The api's token can now change secrets under one path. A compromised api can replace or remove
  sending logins, and nothing else in Vault.
- A board member's account can now point the site's own mail at another server, by marking another
  default or adding an address. The login check and the encrypted-transport rule still apply, and
  every send records the address it went out from.
- Removing an address removes its login from Vault, including its earlier versions.
- Reading an address's mailbox into the site's Inbox, replies and bounces alike, follows from its
  IMAP server and is built on top of this record.
- Sender authentication (SPF, DKIM) for an address on another domain is that domain owner's
  concern. The form says so.
- The Vault bootstrap script and the operator's policy documentation change with this decision.

## Related Documentation
- [ADR-033: The Api Reads Its Secrets From Vault](ADR-033-the-api-reads-its-secrets-from-vault.md)
- [ADR-038: Private Details Are Sealed by Vault Transit](ADR-038-private-details-are-sealed-by-vault-transit.md)
- [ADR umbrella index](../ADR-INDEX.md)
- [AGENTS.md](../../../AGENTS.md)
