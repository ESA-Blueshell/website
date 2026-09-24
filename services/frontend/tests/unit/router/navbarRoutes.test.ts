import {describe, expect, it} from "vitest"
import router from "@/plugins/router"

const navbarPaths = [
  "/",
  "/membership",
  "/aboutus",
  "/board",
  "/committees",
  "/blogs",
  "/documents",
  "/events",
  "/events/circuitShowdown",
  "/competition",
  "/competition/league-of-legends",
  "/competition/counter-strike-2",
  "/competition/valorant",
  "/competition/rocketleague",
  "/competition/geoguessr",
  "/partners/become-a-partner",
  "/partners/el-nino",
  "/partners/marketing-maatwerk",
  "/contact",
  "/login",
  "/account",
  "/account/security",
  "/account/games",
  "/addresses/manage",
  "/recovery/manage",
  "/committees/manage",
  "/user-manager",
  "/management/jobs",
]

describe("Navbar route targets", () => {
  it("resolves every internal route used by navbar and menus", () => {
    for (const path of navbarPaths) {
      expect(router.resolve(path).matched.length, `missing route for ${path}`).toBeGreaterThan(0)
    }
  })
})

describe("the account security pages", () => {
  it.each([
    "twoFactorOffer", "lockAccount", "confirmEmail", "reenrol", "accountSecurity", "accountPassword", "accountEmail",
    "accountTwoFactor", "accountTwoFactorSetUp", "accountSignIns", "accountSecurityLog", "twoFactorRequired",
  ])("loads %s", async (name) => {
    const load = router.getRoutes().find(one => one.name === name)?.components?.default as () => Promise<unknown>
    await expect(load()).resolves.toBeDefined()
  }, 20_000)
})

describe("the pages the fields and the parts are drawn on", () => {
  // The galleries import every island part, which a loaded runner transforms slowly.
  it.each(["design/fields", "design/parts"])("reaches %s while developing, and loads its gallery", async (name) => {
    const route = router.getRoutes().find(one => one.name === name)
    expect(route).toBeDefined()

    const load = route?.components?.default as () => Promise<unknown>
    await expect(load()).resolves.toBeDefined()
  }, 20_000)
})
