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
  "/esports/competitive-scene",
  "/esports/league-of-legends",
  "/esports/counter-strike-2",
  "/esports/valorant",
  "/esports/rocketleague",
  "/esports/geoguessr",
  "/partners/become-a-partner",
  "/partners/el-nino",
  "/partners/marketing-maatwerk",
  "/contact",
  "/login",
  "/account",
  "/addresses/manage",
  "/recovery/manage",
  "/committees/manage",
  "/contributions/manage",
  "/members/manage",
  "/management/jobs",
]

describe("Navbar route targets", () => {
  it("resolves every internal route used by navbar and menus", () => {
    for (const path of navbarPaths) {
      expect(router.resolve(path).matched.length, `missing route for ${path}`).toBeGreaterThan(0)
    }
  })
})
