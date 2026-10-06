/**
 * How sending addresses read on a page: the words for a server's security, the port each one
 * usually takes, and the choice of sender when writing an email.
 */
import {type SendingAddress, SmtpSecurity} from "./adapters/sendingAddresses"

const SECURITY: Record<SmtpSecurity, {label: string; port: number}> = {
  [SmtpSecurity.STARTTLS]: {label: "STARTTLS", port: 587},
  [SmtpSecurity.SSL]: {label: "SSL/TLS", port: 465},
  [SmtpSecurity.NONE]: {label: "None", port: 25},
}

/** What a server's security is called on the page. */
export const securityLabel = (security: SmtpSecurity): string => SECURITY[security].label

/** The port a server with this security usually listens on. */
export const usualPort = (security: SmtpSecurity): number => SECURITY[security].port

/** The key the site's own address goes by among the senders, since it has no id. */
export const SITE_SENDER = "site"

/**
 * The senders an email can go out from, the default first and the site's own address last, and
 * the one picked to start with.
 */
export function fromOptions(addresses: SendingAddress[]) {
  const ordered = [...addresses].sort((one, other) => Number(other.isDefault) - Number(one.isDefault))
  return {
    options: [
      ...ordered.map((one) => ({key: String(one.id), label: one.displayName, note: one.address})),
      {key: SITE_SENDER, label: "The site's own address", note: "Also used for security and payment emails"},
    ],
    start: ordered.find((one) => one.isDefault) ? String(ordered[0]!.id) : SITE_SENDER,
  }
}
