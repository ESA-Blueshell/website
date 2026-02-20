import {describe, expect, it} from "vitest"
import {readFileSync} from "node:fs"
import {resolve} from "node:path"

describe("App navbar", () => {
  it("contains all expected navbar and partner links in template", () => {
    const source = readFileSync(resolve(process.cwd(), "src/App.vue"), "utf8")

    expect(source).toContain('to="/"')
    expect(source).toContain('to="/membership"')
    expect(source).toContain('to="/aboutus"')
    expect(source).toContain('to="/board"')
    expect(source).toContain('to="/committees"')
    expect(source).toContain('to="/blogs"')
    expect(source).toContain('to="/documents"')
    expect(source).toContain('to="/events"')
    expect(source).toContain('to="/events/circuitShowdown"')
    expect(source).toContain('to="/esports/competitive-scene"')
    expect(source).toContain('to="/esports/league-of-legends"')
    expect(source).toContain('to="/esports/counter-strike-2"')
    expect(source).toContain('to="/esports/valorant"')
    expect(source).toContain('to="/esports/rocketleague"')
    expect(source).toContain('to="/esports/geoguessr"')
    expect(source).toContain('to="/esports/trackmania"')
    expect(source).toContain('to="/partners/become-a-partner"')
    expect(source).toContain('to="/partners/el-nino"')
    expect(source).toContain('to="/partners/marketing-maatwerk"')
    expect(source).toContain('to="/contact"')
  })
})
