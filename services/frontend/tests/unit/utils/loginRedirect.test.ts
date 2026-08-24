import {describe, expect, it} from "vitest"
import {resolveLoginRedirect} from "@/utils/loginRedirect"

const ORIGIN = "https://esa-blueshell.nl"

describe("resolveLoginRedirect", () => {
  it("keeps an SPA path on the router", () => {
    expect(resolveLoginRedirect("/events", ORIGIN)).toEqual({target: "/events", offSpa: false})
  })

  it("preserves query and hash on an SPA path", () => {
    expect(resolveLoginRedirect("/events?page=2#top", ORIGIN))
      .toEqual({target: "/events?page=2#top", offSpa: false})
  })

  it("marks same-origin api and authorization-server paths as off-SPA", () => {
    expect(resolveLoginRedirect("/api/oauth2/authorize?client_id=vault", ORIGIN))
      .toEqual({target: "/api/oauth2/authorize?client_id=vault", offSpa: true})
    expect(resolveLoginRedirect("/oauth2/authorize", ORIGIN).offSpa).toBe(true)
    expect(resolveLoginRedirect("/.well-known/openid-configuration", ORIGIN).offSpa).toBe(true)
  })

  it("allows an absolute url to a trusted admin host", () => {
    expect(resolveLoginRedirect("https://vault.esa-blueshell.nl/ui/vault/secrets", ORIGIN))
      .toEqual({target: "https://vault.esa-blueshell.nl/ui/vault/secrets", offSpa: true})
  })

  it("rewrites a same-origin absolute url to a path", () => {
    expect(resolveLoginRedirect(`${ORIGIN}/api/oauth2/authorize`, ORIGIN))
      .toEqual({target: "/api/oauth2/authorize", offSpa: true})
  })

  it.each([
    ["an untrusted absolute url", "https://evil.com/phish"],
    ["a protocol-relative url", "//evil.com/phish"],
    ["a backslash-smuggled host", "/\\evil.com/phish"],
    ["a javascript uri", "javascript:alert(document.domain)"],
    ["a data uri", "data:text/html,<script>alert(1)</script>"],
    ["a trusted host over plain http", "http://vault.esa-blueshell.nl/ui"],
    ["a look-alike host", "https://vault.esa-blueshell.nl.evil.com/ui"],
    ["a subdomain that is not on the allowlist", "https://mail-admin.esa-blueshell.nl/webadmin"],
  ])("falls back home for %s", (_label, raw) => {
    expect(resolveLoginRedirect(raw, ORIGIN)).toEqual({target: "/", offSpa: false})
  })

  it.each([undefined, null, ""])("falls back home for an absent redirect (%s)", (raw) => {
    expect(resolveLoginRedirect(raw, ORIGIN)).toEqual({target: "/", offSpa: false})
  })
})
