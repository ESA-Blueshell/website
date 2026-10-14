import {describe, expect, it} from "vitest"
import {SITE_SENDER, MailSecurity, fromOptions, readState, securityLabel, sendState, usualPort} from "@/domains/emails"
import type {SendingAddress} from "@/domains/emails"

const address = (id: number, isDefault: boolean, fields: Partial<SendingAddress> = {}): SendingAddress => ({
  id, address: `a${id}@b.nl`, displayName: `A${id}`, host: "smtp.b.nl", port: 587, security: MailSecurity.STARTTLS,
  isDefault, loginKept: true, ...fields,
})

describe("how sending addresses read", () => {
  it("names each security and the port each kind of server usually takes with it", () => {
    const all = [MailSecurity.STARTTLS, MailSecurity.SSL, MailSecurity.NONE]
    expect(all.map(securityLabel)).toEqual(["STARTTLS", "SSL/TLS", "None"])
    expect(all.map((one) => usualPort("SMTP", one))).toEqual([587, 465, 25])
    expect(all.map((one) => usualPort("IMAP", one))).toEqual([143, 993, 143])
  })

  it("says whether an address sends and is read by its last check, and why not", () => {
    expect(sendState(address(1, false))).toEqual({kind: "not-compared", word: "Not checked", why: null})
    expect(sendState(address(1, false, {canSend: true}))).toEqual({kind: "in-sync", word: "Sends", why: null})
    expect(sendState(address(1, false, {canSend: false, sendFailure: "535 no"}))).toEqual({kind: "unreachable", word: "Cannot send", why: "535 no"})
    expect(sendState(address(1, false, {canSend: false})).why).toBeNull()

    expect(readState(address(1, false))).toEqual({kind: "not-created", word: "Not read", why: null})
    const read = {imapHost: "imap.b.nl", imapPort: 993, imapSecurity: MailSecurity.SSL}
    expect(readState(address(1, false, read)).word).toBe("Not checked")
    expect(readState(address(1, false, {...read, canRead: true})).word).toBe("Read")
    expect(readState(address(1, false, {...read, canRead: false, readFailure: "NO"}))).toEqual({kind: "unreachable", word: "Cannot read", why: "NO"})
    expect(readState(address(1, false, {...read, canRead: false})).why).toBeNull()
  })

  it("starts from the configured address while no address is the default, and from the default once one is", () => {
    const offered = fromOptions([address(1, false)])
    expect(offered.start).toBe(SITE_SENDER)
    expect(offered.options.map((one) => one.key)).toEqual(["1", SITE_SENDER])
    expect(fromOptions([]).options).toHaveLength(1)

    const marked = fromOptions([address(1, false), address(2, true)])
    expect(marked.start).toBe("2")
    expect(marked.options.map((one) => one.key)).toEqual(["2", "1"])
  })
})
