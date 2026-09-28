import {describe, expect, it} from "vitest"
import {mount} from "@vue/test-utils"
import BandSwipe from "@/components/island/BandSwipe.vue"
import SeasonSwipe from "@/domains/esports/island/SeasonSwipe.vue"
import type {Season} from "@/domains/esports/adapters/esports"

const season = (id: number, startDate: string) => ({id, name: `Season ${id}`, startDate}) as Season

describe("SeasonSwipe", () => {
  const autumn = season(3, "2025-09-01")
  const spring = season(4, "2026-02-01")

  it("hands the band the seasons as stops, oldest first, the season being read among them", () => {
    const wrapper = mount(SeasonSwipe, {props: {season: spring, seasons: [autumn]}})

    expect(wrapper.getComponent(BandSwipe).props("stops")).toEqual([3, 4])
  })

  it("names the seasons a gesture may reach and the one it arrives at by id", () => {
    const wrapper = mount(SeasonSwipe, {props: {season: spring, seasons: [autumn, spring]}})
    const band = wrapper.getComponent(BandSwipe)

    band.vm.$emit("reaching", ["3", 4])
    band.vm.$emit("travel", "3")

    expect(wrapper.emitted("reaching")).toEqual([[[3, 4]]])
    expect(wrapper.emitted("travel")).toEqual([[3]])
  })
})
