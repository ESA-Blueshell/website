import {describe, expect, it, vi} from "vitest"
import {defineComponent, h} from "vue"
import {flushPromises, mount} from "@vue/test-utils"
import {useBookingChoices} from "@/domains/declarations/island/useBookingChoices"
import {listCommittees} from "@/domains/committees"
import {listEvents} from "@/domains/events"
import {aCommittee, anEvent} from "../../../helpers/apiFixtures"

vi.mock("@/domains/committees", () => ({listCommittees: vi.fn()}))
vi.mock("@/domains/events", () => ({listEvents: vi.fn()}))

const host = () => {
  let choices!: ReturnType<typeof useBookingChoices>
  mount(defineComponent({setup: () => {
    choices = useBookingChoices()
    return () => h("div")
  }}))
  return () => choices
}

describe("the booking choices", () => {
  it("offers the committees still active and the events of the last year and next months", async () => {
    vi.mocked(listCommittees).mockResolvedValue([aCommittee({id: 3, name: "Promo", archived: false}), aCommittee({id: 4, name: "Old", archived: true})])
    vi.mocked(listEvents).mockResolvedValue([anEvent({id: 7, title: "LAN", startTime: "2026-10-01T18:00:00Z"})])

    const choices = host()
    await flushPromises()

    expect(choices().committees.value).toEqual([{id: 3, name: "Promo"}])
    expect(choices().events.value).toEqual([{id: 7, name: "LAN", note: "1 Oct 2026"}])
    expect(listEvents).toHaveBeenCalledWith(expect.objectContaining({size: 200, sort: ["startTime,desc"]}))
  })

  it("offers nothing from a list that could not be read", async () => {
    vi.mocked(listCommittees).mockRejectedValue(new Error("down"))
    vi.mocked(listEvents).mockRejectedValue(new Error("down"))

    const choices = host()
    await flushPromises()

    expect(choices().committees.value).toEqual([])
    expect(choices().events.value).toEqual([])
  })
})
