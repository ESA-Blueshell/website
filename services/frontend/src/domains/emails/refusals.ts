// TWIN: `email/domain/SendingAddressRefusal.kt` declares the codes and their facts. See ADR-026.

import {refusalReader, type RefusalCode} from "@/utils/refusals"

interface RefusalBody extends RefusalCode {
  address?: string
  host?: string
  protocol?: string
  reason?: string
}

const sentences: Record<string, (r: RefusalBody) => string> = {
  SendingAddressNotFound: () => "That sending address is no longer there.",
  SendingAddressTaken: (r) => `${r.address} is a sending address already.`,
  SendingAddressNeedsLogin: () => "Fill in the username and password.",
  ImapServerIncomplete: () => "Fill in the IMAP server's port and security too, or leave its server empty.",
  MailLoginRefused: (r) => `The ${r.protocol} server refused the login: ${r.reason}`,
  MailNeedsEncryption: (r) => `${r.host} is not on the site's own network, so pick STARTTLS or SSL/TLS for ${r.protocol} to keep the login encrypted.`,
  SendingLoginsUnavailable: () => "Vault, where the logins are kept, cannot be reached now; try again in a moment.",
}

export const {refusable, accepted} = refusalReader(sentences)
