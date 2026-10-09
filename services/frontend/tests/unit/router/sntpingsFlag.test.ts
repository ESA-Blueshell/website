/**
 * The SNTPings tab sits behind one flag: with it on, the nav entry (under Events) and the route are
 * both there; with it off, both are gone and nothing else has to change.
 */
import {afterEach, describe, expect, it, vi} from "vitest"

import type {NavSection} from "@/components/common/nav"

/** Whether any bar entry, top-level or inside a dropdown, points at the SNTPings page. */
const hasSntPings = (sections: NavSection[]): boolean =>
  sections.some(section => section.to === "/sntpings" || (section.entries ?? []).some(entry => entry.to === "/sntpings"))

async function navAndRouter(enabled: boolean) {
  vi.resetModules()
  vi.doMock("@/domains/pinger/sntpings", () => ({SNTPINGS_ENABLED: enabled, SNTPINGS_PATH: "/sntpings"}))
  const {sectionsFor} = await import("@/components/common/nav")
  const {default: router} = await import("@/plugins/router")
  return {sectionsFor, router}
}

describe("the SNTPings feature flag", () => {
  afterEach(() => {
    vi.doUnmock("@/domains/pinger/sntpings")
    vi.resetModules()
  })

  it("shows the tab and registers the route when the flag is on", async () => {
    const {sectionsFor, router} = await navAndRouter(true)

    expect(hasSntPings(sectionsFor([]))).toBe(true)
    expect(router.resolve("/sntpings").name).toBe("sntpings")
  })

  it("drops the tab and the route when the flag is off", async () => {
    const {sectionsFor, router} = await navAndRouter(false)

    expect(hasSntPings(sectionsFor([]))).toBe(false)
    expect(router.resolve("/sntpings").name).toBe("NotFound")
  })
})
