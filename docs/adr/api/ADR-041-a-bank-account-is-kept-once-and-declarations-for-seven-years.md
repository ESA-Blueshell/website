# ADR-041: A Bank Account Is Kept Once, and Declarations for Seven Years

## Status
Accepted

## Context

A person's bank details live inside their incasso mandate (ADR-038, ADR-040): the sealed IBAN and
account holder are columns of the mandate. Declarations need the same account to pay a member
back, and someone who never signs a mandate still has to be paid. Keeping a second copy for
declarations would let the two disagree, and would seal and wipe the same IBAN twice.

Declarations are the association's bookkeeping. Dutch law asks financial records to be kept for
7 years, while ADR-038 wipes bank details 13 months after their last use. The two clocks have to
be told apart, so that wiping an IBAN never takes a receipt with it, and keeping a receipt never
keeps an IBAN.

## Decision

**A person has one bank account, sealed and kept apart from anything that uses it. A mandate is
given for that account, and a declaration is paid to it. Declarations and their receipts are kept
for 7 years; only the bank account follows the 13-month wipe.**

- The bank account holds the account holder and the IBAN, sealed by Vault Transit to the person,
  and outside the Netherlands the bank's BIC, which names a bank rather than a person and is kept
  open. A readable preview shows the country and the last two digits. It is replaced whole, never
  edited.
- A mandate records the bank account it was given for. Replacing or deleting the account ends the
  mandate (ADR-040). A paper mandate may stand before any account is on file.
- Existing mandates hand their sealed details to the person's new bank account in a migration;
  nothing is unsealed on the way.
- A declaration is paid to the account on file when a board member marks it paid, and keeps that
  account's preview. Only someone with a bank account can submit one.
- The bank account is wiped 13 months after its last use, a collection or a declaration paid to
  it, and not while a mandate stands.
- A declaration, its expenses and receipts are kept for 7 years after it is paid or rejected, then
  deleted. Erasing an account deletes its drafts, rejects what is still submitted and keeps the rest
  under the 7-year rule, without the person's name.
- Receipts are private files: only the submitter and the board open them.

Alternatives considered:

- **Bank details on the mandate, read by declarations.** No new record, but someone without a
  mandate has nowhere to be paid, and ending a mandate would raise the question whether the
  account goes with it.
- **A payout account per declaration.** The account as typed for that claim. Every declaration
  would seal its own IBAN, and the wipe would have to chase each of them.
- **One retention for everything.** Keeping IBANs for 7 years breaks the 13-month promise of
  ADR-038; wiping receipts after 13 months breaks the bookkeeping.

## Consequences

- The mandate's sealed columns move to the bank account; ING's incasso file and a reveal open the
  bank account rather than the mandate.
- Payment details gets a bank account page of its own, showing only the preview.
- The retention wipe counts a paid declaration as a use of the account, as ADR-038 was amended to
  say.
- A 7-year purge is new: nothing in the site deleted financial records before.

## Related Documentation
- [ADR-038 Private details are sealed by Vault Transit](ADR-038-private-details-are-sealed-by-vault-transit.md)
- [ADR-040 A membership type is dated and a mandate decides direct debit](ADR-040-a-membership-type-is-dated-and-a-mandate-decides-direct-debit.md)
- [ADR umbrella index](../ADR-INDEX.md)
- [AGENTS.md](../../../AGENTS.md)
