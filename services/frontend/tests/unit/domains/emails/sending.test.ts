import {describe, expect, it} from "vitest"
import {SITE_SENDER, SmtpSecurity, fromOptions, securityLabel, usualPort} from "@/domains/emails"

const address = (id: number, isDefault: boolean) =>
  ({id, address: `a${id}@b.nl`, displayName: `A${id}`, host: "smtp.b.nl", port: 587, security: SmtpSecurity.STARTTLS, isDefault, loginKept: true})

describe("how sending addresses read", () => {
  it("names each security and the port it usually takes", () => {
    expect([SmtpSecurity.STARTTLS, SmtpSecurity.SSL, SmtpSecurity.NONE].map(securityLabel)).toEqual(["STARTTLS", "SSL/TLS", "None"])
    expect([SmtpSecurity.STARTTLS, SmtpSecurity.SSL, SmtpSecurity.NONE].map(usualPort)).toEqual([587, 465, 25])
  })

  it("starts from the site's own address when no address is the default", () => {
    const offered = fromOptions([address(1, false)])
    expect(offered.start).toBe(SITE_SENDER)
    expect(offered.options.map((one) => one.key)).toEqual(["1", SITE_SENDER])
    expect(fromOptions([]).options).toHaveLength(1)
  })
})
