/**
 * How sending addresses read on a page: the words for a server's security, the port each one
 * usually takes, what the last check found, and the choice of sender when writing an email.
 */
import {type SendingAddress, MailSecurity} from "./adapters/sendingAddresses"
import type {StateKind} from "@/components/island/StateMark.vue"

/** The two servers an address has, which take their usual ports apart. */
export type MailProtocol = "SMTP" | "IMAP"

const SECURITY: Record<MailSecurity, {label: string; smtp: number; imap: number}> = {
  [MailSecurity.STARTTLS]: {label: "STARTTLS", smtp: 587, imap: 143},
  [MailSecurity.SSL]: {label: "SSL/TLS", smtp: 465, imap: 993},
  [MailSecurity.NONE]: {label: "None", smtp: 25, imap: 143},
}

/** What a server's security is called on the page. */
export const securityLabel = (security: MailSecurity): string => SECURITY[security].label

/** The port a server of this kind with this security usually listens on. */
export const usualPort = (protocol: MailProtocol, security: MailSecurity): number =>
  protocol === "SMTP" ? SECURITY[security].smtp : SECURITY[security].imap

/** What the last check found for one of an address's servers, as the page marks it. */
export interface CheckState {
  kind: StateKind
  word: string
  why: string | null
}

/** Whether the address sends, by its last check. */
export function sendState(address: SendingAddress): CheckState {
  if (address.canSend == null) return {kind: "not-compared", word: "Not checked", why: null}
  return address.canSend
    ? {kind: "in-sync", word: "Sends", why: null}
    : {kind: "unreachable", word: "Cannot send", why: address.sendFailure ?? null}
}

/** Whether the address's mailbox is read, by its last check; one without an IMAP server is not read at all. */
export function readState(address: SendingAddress): CheckState {
  if (!address.imapHost) return {kind: "not-created", word: "Not read", why: null}
  if (address.canRead == null) return {kind: "not-compared", word: "Not checked", why: null}
  return address.canRead
    ? {kind: "in-sync", word: "Read", why: null}
    : {kind: "unreachable", word: "Cannot read", why: address.readFailure ?? null}
}

/** The key the configured address goes by among the senders, since it has no id. */
export const SITE_SENDER = "site"

/**
 * The senders an email can go out from and the one picked to start with: the default first. While
 * no address is the default the site's mail goes out from the configured address, so that is
 * offered last and picked first.
 */
export function fromOptions(addresses: SendingAddress[]) {
  const ordered = [...addresses].sort((one, other) => Number(other.isDefault) - Number(one.isDefault))
  const marked = ordered.find((one) => one.isDefault)
  return {
    options: [
      ...ordered.map((one) => ({key: String(one.id), label: one.displayName, note: one.address})),
      ...(marked ? [] : [{key: SITE_SENDER, label: "The site's own address", note: "Also used for security and payment emails"}]),
    ],
    start: marked ? String(marked.id) : SITE_SENDER,
  }
}
