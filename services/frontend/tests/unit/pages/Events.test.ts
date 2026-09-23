import {beforeEach, describe, expect, it, vi} from "vitest"
import {defineComponent} from "vue"
import EventsPage from "@/pages/Events.vue"
import {mountInApp, settle} from "./helpers"

const {
  mockFindEvents,
  mockFindEventSignUps,
  mockFindEventSignUpsByAccessToken,
  mockFindCommittees,
  mockFindCommitteesByUserId,
  mockHandleNetworkError,
  mockStore,
} = vi.hoisted(() => ({
  mockFindEvents: vi.fn(),
  mockFindEventSignUps: vi.fn(),
  mockFindEventSignUpsByAccessToken: vi.fn(),
  mockFindCommittees: vi.fn(),
  mockFindCommitteesByUserId: vi.fn(),
  mockHandleNetworkError: vi.fn(),
  mockStore: {
    getters: {
      isLoggedIn: true,
      isBoard: true,
      getLogin: {userId: 7},
      getGuestData: null,
    },
  },
}))

vi.mock("vuex", async (importOriginal) => {
  const {withVuexUseStore} = await import("../helpers/testUtils")
  return withVuexUseStore(importOriginal, mockStore)
})

vi.mock("@/services/api", async (importOriginal) => ({
  ...(await importOriginal<object>()),
  findEvents: mockFindEvents,
  findEventSignUps: mockFindEventSignUps,
  findEventSignUpsByAccessToken: mockFindEventSignUpsByAccessToken,
  findCommittees: mockFindCommittees,
  findCommitteesByUserId: mockFindCommitteesByUserId,
}))

vi.mock("@/plugins/handleNetworkError.ts", () => ({
  $handleNetworkError: mockHandleNetworkError,
}))

/* Each band stands in with buttons that say what the real one would, so the page's own
   bookkeeping is what is under test. */
const emitting = (name: string) => defineComponent({
  name,
  props: {
    event: {type: Object, default: undefined},
    events: {type: Array, default: undefined},
    signUps: {type: Array, default: undefined},
    mayAdd: {type: Boolean, default: false},
  },
  emits: ["update:event", "delete:event", "update:sign-up", "delete:sign-up"],
  template: `
    <div :data-test="'${name}'">
      <button data-test="emit-update-event" @click="$emit('update:event', { id: 11, title: 'Updated Event', signUpCount: 0 })">update event</button>
      <button data-test="emit-delete-event" @click="$emit('delete:event', 11)">delete event</button>
      <button data-test="emit-signup" @click="$emit('update:sign-up', { id: 101, eventId: 11 })">add signup</button>
      <button data-test="emit-signup-same" @click="$emit('update:sign-up', { id: 101, eventId: 11 })">upsert signup</button>
      <button data-test="emit-delete-signup" @click="$emit('delete:sign-up', 101)">delete signup</button>
    </div>
  `,
})

const stubs = {NextEventBand: emitting("NextEventBand"), EventAgenda: emitting("EventAgenda"), EventsBand: true}
const mountPage = () => mountInApp(EventsPage, {global: {stubs}})

describe("Events page", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockStore.getters.isLoggedIn = true
    mockStore.getters.isBoard = true
    mockStore.getters.getLogin = {userId: 7}
    mockStore.getters.getGuestData = null

    mockFindEvents.mockResolvedValue({
      data: {
        content: [
          {id: 11, title: "LAN", signUpCount: 0},
        ],
      },
    })
    mockFindEventSignUps.mockResolvedValue({data: []})
    mockFindEventSignUpsByAccessToken.mockResolvedValue({data: []})
    mockFindCommittees.mockResolvedValue({data: [{id: 1, name: "Board"}]})
    mockFindCommitteesByUserId.mockResolvedValue({data: [{id: 2, name: "Events"}]})
  })

  it("leads with the first event still to come, and lists the rest in the agenda", async () => {
    mockFindEvents.mockResolvedValue({data: {content: [
      {id: 11, title: "LAN", signUpCount: 0},
      {id: 12, title: "Pub quiz", signUpCount: 0},
      {id: 13, title: "Game night", signUpCount: 0},
    ]}})
    const wrapper = mountPage()
    await settle()

    expect(wrapper.findComponent({name: "NextEventBand"}).props("event")).toMatchObject({id: 11})
    expect(wrapper.findComponent({name: "EventAgenda"}).props("events").map((one: {id: number}) => one.id)).toEqual([12, 13])
    // The board has committees, so it may add an event.
    expect(wrapper.findComponent({name: "EventAgenda"}).props("mayAdd")).toBe(true)
  })

  it("keeps the calendar one press away, under the testid the suites know", async () => {
    const wrapper = mountPage()
    await settle()

    const subscribe = wrapper.get("[data-testid=event-calendar-subscribe-btn]")
    expect(subscribe.attributes("href")).toContain("calendar.google.com/calendar")
    expect(subscribe.attributes("target")).toBe("_blank")
    expect(wrapper.findAll("[data-testid=event-calendar-subscribe-btn]")).toHaveLength(1)
  })

  it("draws no next event while nothing is coming, and an empty agenda under it", async () => {
    mockFindEvents.mockResolvedValue({data: {content: []}})
    const wrapper = mountPage()
    await settle()

    expect(wrapper.findComponent({name: "NextEventBand"}).exists()).toBe(false)
    expect(wrapper.findComponent({name: "EventAgenda"}).props("events")).toEqual([])
  })

  it("takes a band's updates and deletions into its list, and asks for nothing again", async () => {
    const wrapper = mountPage()
    await settle()

    expect(mockFindEvents).toHaveBeenCalledTimes(1)
    expect(mockFindEventSignUps).toHaveBeenCalledTimes(1)
    expect(mockFindCommittees).toHaveBeenCalledTimes(1)

    const band = wrapper.get("[data-test=NextEventBand]")
    await band.get("[data-test='emit-update-event']").trigger("click")
    expect((wrapper.vm as any).events[0].title).toBe("Updated Event")
    await band.get("[data-test='emit-delete-event']").trigger("click")
    expect((wrapper.vm as any).events).toHaveLength(0)

    expect(mockFindEvents).toHaveBeenCalledTimes(1)
    expect(mockFindEventSignUps).toHaveBeenCalledTimes(1)
    expect(mockFindCommittees).toHaveBeenCalledTimes(1)
  })

  it("upserts signups once and keeps event signup counts consistent", async () => {
    const wrapper = mountPage()
    await settle()

    const agenda = wrapper.get("[data-test=EventAgenda]")
    await agenda.get("[data-test='emit-signup']").trigger("click")
    expect((wrapper.vm as any).eventSignUps).toHaveLength(1)
    expect((wrapper.vm as any).events[0].signUpCount).toBe(1)

    await agenda.get("[data-test='emit-signup-same']").trigger("click")
    expect((wrapper.vm as any).eventSignUps).toHaveLength(1)
    expect((wrapper.vm as any).events[0].signUpCount).toBe(1)

    await agenda.get("[data-test='emit-delete-signup']").trigger("click")
    expect((wrapper.vm as any).eventSignUps).toHaveLength(0)
    expect((wrapper.vm as any).events[0].signUpCount).toBe(0)
  })

  it("lands on the event a link names in its hash, once the events have arrived", async () => {
    window.location.hash = "#11"
    const target = document.createElement("div")
    target.id = "11"
    const landed = vi.fn()
    Object.defineProperty(target, "scrollIntoView", {value: landed})
    document.body.append(target)

    mountPage()
    await settle()

    expect(landed).toHaveBeenCalled()
    target.remove()
    window.location.hash = ""
  })

  it("loads guest signups when user is logged out and guest token exists", async () => {
    mockStore.getters.isLoggedIn = false
    mockStore.getters.getLogin = null
    mockStore.getters.getGuestData = {accessToken: "guest-token"}

    mountPage()
    await settle()

    expect(mockFindEventSignUpsByAccessToken).toHaveBeenCalledWith({
      headers: {"X-Guest-Access-Token": "guest-token"},
      throwOnError: true,
    })
    expect(mockFindEventSignUps).not.toHaveBeenCalled()
  })

  it("asks for only the reader's own committees where the reader is not board", async () => {
    mockStore.getters.isBoard = false

    const wrapper = mountPage()
    await settle()

    expect(mockFindCommitteesByUserId).toHaveBeenCalled()
    expect(mockFindCommittees).not.toHaveBeenCalled()
    expect((wrapper.vm as any).committees).toEqual([{id: 2, name: "Events"}])
  })
})
