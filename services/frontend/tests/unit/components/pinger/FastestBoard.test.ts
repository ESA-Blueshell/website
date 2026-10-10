/**
 * The fastest board: members by peak rate with when they set it, SiteCie's own peak set apart, and
 * the reader's own row marked.
 */
import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {mount, type VueWrapper} from "@vue/test-utils"
import FastestBoard from "@/components/pinger/FastestBoard.vue"
import type {FastestStanding, HouseLine} from "@/domains/pinger"

const at = new Date(2026, 9, 9, 21, 14).toISOString()

const rows: FastestStanding[] = [
  {rank: 1, memberId: 2, peakPps: 2_000_000, peakAt: at, discordTag: "ace#1", avatarUrl: "https://cdn/ace.png", username: null},
  {rank: 2, memberId: 3, peakPps: 640, peakAt: null, discordTag: null, avatarUrl: null, username: "robin"},
]

const house: HouseLine = {label: "SiteCie", totalSent: 9_000, online: true, pps: 3_000, peakPps: 17_000, peakAt: at}

describe("FastestBoard", () => {
  const wrappers: VueWrapper[] = []
  const render = (props: InstanceType<typeof FastestBoard>["$props"]) => {
    const wrapper = mount(FastestBoard, {props})
    wrappers.push(wrapper)
    return wrapper
  }

  // Same day as at, so the time shows without a date.
  beforeEach(() => {
    vi.useFakeTimers({toFake: ["Date"]})
    vi.setSystemTime(new Date(2026, 9, 9, 22, 0))
  })

  afterEach(() => {
    wrappers.splice(0).forEach(w => w.unmount())
    vi.useRealTimers()
  })

  it("ranks each member by peak rate under their public identity, with when they set it", () => {
    const wrapper = render({rows})

    const shown = wrapper.findAll("[data-testid=snt-fastest-row]")
    expect(shown).toHaveLength(2)
    expect(shown[0].text()).toContain("ace#1")
    expect(shown[0].text()).toContain("2,000,000")
    expect(shown[0].text()).toContain("set 21:14")
    expect(shown[0].find("img").attributes("src")).toBe("https://cdn/ace.png")
    expect(shown[1].text()).toContain("robin")
    expect(shown[1].text()).not.toContain("set")
  })

  it("sets SiteCie's peak apart above the ranking", () => {
    const wrapper = render({rows, house})

    const line = wrapper.get("[data-testid=snt-fastest-house]").text()
    expect(line).toContain("SiteCie")
    expect(line).toContain("17,000")
    expect(line).toContain("set 21:14")
  })

  it("leaves out a house line with no peak and a peak with no time", () => {
    expect(render({rows, house: {...house, peakPps: 0}}).find("[data-testid=snt-fastest-house]").exists()).toBe(false)
    expect(render({rows, house: {...house, peakAt: null}}).get("[data-testid=snt-fastest-house]").text()).not.toContain("set")
  })

  it("marks the reader's own row", () => {
    const wrapper = render({rows, mineId: 3})

    expect(wrapper.findAll("[data-testid=snt-fastest-row]")[1].find("[data-testid=snt-fastest-you]").exists()).toBe(true)
  })

  it("says so when no member has a top rate yet", () => {
    expect(render({rows: []}).find("[data-testid=snt-fastest-empty]").exists()).toBe(true)
  })

  it("falls back to a dash for a member the api names neither way", () => {
    const wrapper = render({rows: [{...rows[1], username: null}]})

    expect(wrapper.get("[data-testid=snt-fastest-row]").text()).toContain("—")
  })
})
