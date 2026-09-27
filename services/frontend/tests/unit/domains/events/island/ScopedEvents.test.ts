import {beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"
import {ref} from "vue"
import ScopedEvents from "@/domains/events/island/ScopedEvents.vue"

const adapter = vi.hoisted(() => ({listEvents: vi.fn(), readEventPage: vi.fn(), eventFileUrl: (url: string) => url}))
vi.mock("@/domains/events/adapters/events", () => adapter)
const reader = vi.hoisted(() => ({signUps: null as never, committees: null as never}))
vi.mock("@/domains/events/island/useEventReader", () => ({useEventReader: () => reader}))

const EventAgenda = {
  name: "EventAgenda",
  props: ["events", "signUps", "committees", "mayAdd"],
  emits: ["update:event", "delete:event", "update:signUp", "delete:signUp"],
  template: "<div data-testid='agenda' />",
}
const PosterStrip = {name: "PosterStrip", props: ["items", "testidPrefix"], emits: ["needs-more"], template: "<div />"}
const BandHead = {name: "BandHead", props: ["count", "countSaid", "heading"], template: "<h2>{{ heading }}</h2>"}

const event = (id: number, over: Record<string, unknown> = {}) =>
  ({id, title: `Event ${id}`, startTime: "2026-10-10T19:00:00", endTime: "2026-10-10T22:00:00", membersOnly: false, ...over})

const mountEvents = async (props: Record<string, unknown> = {}) => {
  const wrapper = mount(ScopedEvents, {
    props: {scope: {gameCode: "CHESS"}, testid: "game-events", ...props},
    slots: {default: "Nothing names Chess yet."},
    global: {stubs: {EventAgenda, PosterStrip, BandHead}},
  })
  await flushPromises()
  return wrapper
}

beforeEach(() => {
  adapter.listEvents.mockReset().mockResolvedValue([])
  adapter.readEventPage.mockReset().mockResolvedValue({events: []})
  reader.signUps = ref([{id: 40, eventId: 1}]) as never
  reader.committees = ref([{id: 2, name: "LanCie"}]) as never
})

describe("the events of one game or committee", () => {
  it("asks for its own upcoming and past events, and draws them as the agenda and the poster strip", async () => {
    adapter.listEvents.mockResolvedValue([event(1)])
    adapter.readEventPage.mockResolvedValue({events: [event(2)], page: {totalElements: 1}})
    const wrapper = await mountEvents()

    expect(adapter.listEvents).toHaveBeenCalledWith(expect.objectContaining({gameCode: "CHESS", sort: ["startTime,asc"]}))
    expect(adapter.readEventPage).toHaveBeenCalledWith(expect.objectContaining({gameCode: "CHESS", approved: true, page: 0, size: 6}))
    expect(wrapper.getComponent(EventAgenda).props("events")).toEqual([event(1)])
    expect(wrapper.getComponent(BandHead).props("count")).toBe(1)
    expect(wrapper.getComponent(PosterStrip).props("items")[0]).toMatchObject({id: 2, href: "/events/2"})
    expect(wrapper.find("[data-testid=game-events-none]").exists()).toBe(false)
  })

  it("reads the next page of past events when the strip nears its end, until a page answers short", async () => {
    adapter.readEventPage
      .mockResolvedValueOnce({events: [1, 2, 3, 4, 5, 6].map(id => event(id))})
      .mockResolvedValueOnce({events: [event(6), event(7)]})
    const wrapper = await mountEvents()

    wrapper.getComponent(PosterStrip).vm.$emit("needs-more")
    await flushPromises()
    wrapper.getComponent(PosterStrip).vm.$emit("needs-more")
    await flushPromises()

    expect(adapter.readEventPage).toHaveBeenCalledTimes(2)
    expect(adapter.readEventPage).toHaveBeenLastCalledWith(expect.objectContaining({page: 1}))
    expect(wrapper.getComponent(PosterStrip).props("items").map((one: {id: number}) => one.id)).toEqual([1, 2, 3, 4, 5, 6, 7])
  })

  it("keeps the agenda in step with what its rows change", async () => {
    adapter.listEvents.mockResolvedValue([event(1), event(3)])
    const wrapper = await mountEvents()
    const agenda = wrapper.getComponent(EventAgenda)

    agenda.vm.$emit("update:event", event(1, {title: "Renamed"}))
    agenda.vm.$emit("delete:event", 3)
    agenda.vm.$emit("update:signUp", {id: 41, eventId: 1})
    agenda.vm.$emit("delete:signUp", 40)
    await flushPromises()

    expect(agenda.props("events")).toEqual([event(1, {title: "Renamed"})])
    expect(agenda.props("signUps")).toEqual([{id: 41, eventId: 1}])
  })

  it("says the page's note where there are no events either way, and offers adding one where it may", async () => {
    adapter.listEvents.mockRejectedValue(new Error("down"))
    const wrapper = await mountEvents()

    expect(wrapper.get("[data-testid=game-events-none]").text()).toBe("Nothing names Chess yet.")
    expect(wrapper.findComponent(EventAgenda).exists()).toBe(false)
    expect(wrapper.findComponent(PosterStrip).exists()).toBe(false)

    const silent = mount(ScopedEvents, {props: {scope: {committeeId: 7}, testid: "committee-events"}, global: {stubs: {EventAgenda, PosterStrip, BandHead}}})
    await flushPromises()
    expect(silent.find("[data-testid=committee-events-none]").exists()).toBe(false)

    const adding = await mountEvents({mayAdd: true})
    expect(adding.getComponent(EventAgenda).props("mayAdd")).toBe(true)
  })
})
