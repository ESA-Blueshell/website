import {beforeEach, describe, expect, it, vi} from "vitest"
import {defineComponent, h} from "vue"
import EventsPage from "@/pages/Events.vue"
import {mountInApp, settle} from "./helpers"

const {
  mockFindEvents,
  mockFindEventSignUps,
  mockFindEventSignUpsByAccessToken,
  mockFindCommittees,
  mockFindCommitteesByUserId,
  mockHandleNetworkError,
  mockCalendarUpdate,
  mockCalendarDelete,
  mockStore,
} = vi.hoisted(() => ({
  mockFindEvents: vi.fn(),
  mockFindEventSignUps: vi.fn(),
  mockFindEventSignUpsByAccessToken: vi.fn(),
  mockFindCommittees: vi.fn(),
  mockFindCommitteesByUserId: vi.fn(),
  mockHandleNetworkError: vi.fn(),
  mockCalendarUpdate: vi.fn(),
  mockCalendarDelete: vi.fn(),
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

vi.mock("@/services/api", () => ({
  findEvents: mockFindEvents,
  findEventSignUps: mockFindEventSignUps,
  findEventSignUpsByAccessToken: mockFindEventSignUpsByAccessToken,
  findCommittees: mockFindCommittees,
  findCommitteesByUserId: mockFindCommitteesByUserId,
}))

vi.mock("@/plugins/handleNetworkError.ts", () => ({
  $handleNetworkError: mockHandleNetworkError,
}))

vi.mock("@/components/common/banners/TopBanner.vue", () => ({
  default: {
    name: "TopBanner",
    template: "<div />",
  },
}))

vi.mock("@/components/base/PastEventsPane.vue", () => ({
  default: {
    name: "PastEventsPane",
    template: "<div />",
  },
}))

vi.mock("@/components/base/EventCalendar.vue", () => ({
  default: {
    name: "EventCalendar",
    template: "<div />",
  },
}))

vi.mock("@/components/common/lists/EventList.vue", () => ({
  default: {
    name: "EventList",
    template: "<div />",
  },
}))

const EventCalendarStub = defineComponent({
  name: "EventCalendar",
  setup(_, {expose}) {
    expose({
      updateEvent: mockCalendarUpdate,
      deleteEvent: mockCalendarDelete,
    })
    return () => h("div", {"data-test": "calendar"})
  },
})

const EventListStub = defineComponent({
  name: "EventList",
  props: {
    events: {type: Array, required: true},
    eventSignUps: {type: Array, required: true},
  },
  emits: ["update:event", "delete:event", "update:sign-up", "delete:sign-up"],
  template: `
    <div>
      <button data-test="emit-update-event" @click="$emit('update:event', { id: 11, title: 'Updated Event', signUpCount: 0 })">update event</button>
      <button data-test="emit-delete-event" @click="$emit('delete:event', 11)">delete event</button>
      <button data-test="emit-signup" @click="$emit('update:sign-up', { id: 101, eventId: 11 })">add signup</button>
      <button data-test="emit-signup-same" @click="$emit('update:sign-up', { id: 101, eventId: 11 })">upsert signup</button>
      <button data-test="emit-delete-signup" @click="$emit('delete:sign-up', 101)">delete signup</button>
    </div>
  `,
})

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

  it("passes event updates/deletes from list to calendar and does not refetch in a loop", async () => {
    const wrapper = mountInApp(EventsPage, {
      global: {
        stubs: {
          EventCalendar: EventCalendarStub,
          EventList: EventListStub,
          PastEventsPane: true,
        },
      },
    })

    await settle()

    expect(mockFindEvents).toHaveBeenCalledTimes(1)
    expect(mockFindEventSignUps).toHaveBeenCalledTimes(1)
    expect(mockFindCommittees).toHaveBeenCalledTimes(1)

    const loadEventsCallCount = mockFindEvents.mock.calls.length
    const loadSignUpsCallCount = mockFindEventSignUps.mock.calls.length
    const loadCommitteesCallCount = mockFindCommittees.mock.calls.length

    await wrapper.get("[data-test='emit-update-event']").trigger("click")
    await wrapper.get("[data-test='emit-delete-event']").trigger("click")

    expect(mockCalendarUpdate).toHaveBeenCalledWith({id: 11, title: "Updated Event", signUpCount: 0})
    expect(mockCalendarDelete).toHaveBeenCalledWith(11)

    expect(mockFindEvents).toHaveBeenCalledTimes(loadEventsCallCount)
    expect(mockFindEventSignUps).toHaveBeenCalledTimes(loadSignUpsCallCount)
    expect(mockFindCommittees).toHaveBeenCalledTimes(loadCommitteesCallCount)
  })

  it("upserts signups once and keeps event signup counts consistent", async () => {
    const wrapper = mountInApp(EventsPage, {
      global: {
        stubs: {
          EventCalendar: EventCalendarStub,
          EventList: EventListStub,
          PastEventsPane: true,
        },
      },
    })

    await settle()

    await wrapper.get("[data-test='emit-signup']").trigger("click")
    expect((wrapper.vm as any).eventSignUps).toHaveLength(1)
    expect((wrapper.vm as any).events[0].signUpCount).toBe(1)

    await wrapper.get("[data-test='emit-signup-same']").trigger("click")
    expect((wrapper.vm as any).eventSignUps).toHaveLength(1)
    expect((wrapper.vm as any).events[0].signUpCount).toBe(1)

    await wrapper.get("[data-test='emit-delete-signup']").trigger("click")
    expect((wrapper.vm as any).eventSignUps).toHaveLength(0)
    expect((wrapper.vm as any).events[0].signUpCount).toBe(0)
  })

  it("loads guest signups when user is logged out and guest token exists", async () => {
    mockStore.getters.isLoggedIn = false
    mockStore.getters.getLogin = null
    mockStore.getters.getGuestData = {accessToken: "guest-token"}

    mountInApp(EventsPage, {
      global: {
        stubs: {
          EventCalendar: EventCalendarStub,
          EventList: EventListStub,
          PastEventsPane: true,
        },
      },
    })

    await settle()

    expect(mockFindEventSignUpsByAccessToken).toHaveBeenCalledWith({
      headers: {"X-Guest-Access-Token": "guest-token"},
      throwOnError: true,
    })
    expect(mockFindEventSignUps).not.toHaveBeenCalled()
  })
})
