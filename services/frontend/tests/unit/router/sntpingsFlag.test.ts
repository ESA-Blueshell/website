/**
 * The SNTPings tab sits behind one flag: with it on, the nav entry and the route are both there;
 * with it off, both are gone and nothing else has to change.
 */
import {afterEach, describe, expect, it, vi} from "vitest"

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

    expect(sectionsFor([]).some(section => section.to === "/sntpings")).toBe(true)
    expect(router.resolve("/sntpings").name).toBe("sntpings")
  })

  it("drops the tab and the route when the flag is off", async () => {
    const {sectionsFor, router} = await navAndRouter(false)

    expect(sectionsFor([]).some(section => section.to === "/sntpings")).toBe(false)
    expect(router.resolve("/sntpings").name).toBe("NotFound")
  })
})
