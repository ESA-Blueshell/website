import {beforeEach, describe, expect, it, vi} from "vitest"
import {shallowMount} from "@vue/test-utils"
import EventCalendar from "@/components/base/EventCalendar.vue"
import {settle} from "../../helpers/testUtils"

const {
  mockFindEvents,
  mockDisplay,
  mockLocale,
} = vi.hoisted(() => ({
  mockFindEvents: vi.fn(),
  mockDisplay: {
    xs: {value: false},
  },
  mockLocale: {
    current: {value: "nl"},
  },
}))

vi.mock("vuetify", async (importOriginal) => {
  const {withVuetify} = await import("../../helpers/testUtils")
  return withVuetify(importOriginal, {
    useDisplay: () => mockDisplay,
    useLocale: () => mockLocale,
  })
})

vi.mock("@/services/api", () => ({
  findEvents: mockFindEvents,
}))

vi.mock("@/components/base/EventDetails.vue", () => ({
  default: {
    name: "EventDetails",
    template: "<div data-test='event-details' />",
  },
}))

const firstEvent = {
  id: 1,
  title: "LAN",
  startTime: "2099-02-20T12:00:00.000Z",
  endTime: "2099-02-20T14:00:00.000Z",
  approved: true,
}

describe("EventCalendar", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockFindEvents.mockResolvedValue({
      data: {
        content: [firstEvent],
      },
    })
  })

  it("loads current-month events on mount and maps them to calendar events", async () => {
    const wrapper = shallowMount(EventCalendar, {
      global: {
        stubs: {
          VCalendar: true,
        },
      },
    })
    await settle()

    expect(mockLocale.current.value).toBe("en")
    expect(mockFindEvents).toHaveBeenCalledTimes(1)
    expect(mockFindEvents).toHaveBeenCalledWith({
      query: {
        from: expect.any(String),
        to: expect.any(String),
      },
    })

    expect((wrapper.vm as any).events).toHaveLength(1)
    expect((wrapper.vm as any).calendarEvents).toHaveLength(1)
    expect((wrapper.vm as any).calendarEvents[0].name).toBe("LAN")
    expect((wrapper.vm as any).calendarEvents[0].color).toBe("primary")
  })

  it("upserts and deletes events without refetching", async () => {
    const wrapper = shallowMount(EventCalendar, {
      global: {
        stubs: {
          VCalendar: true,
        },
      },
    })
    await settle()

    const vm = wrapper.vm as any
    const baseCalls = mockFindEvents.mock.calls.length

    vm.updateEvent({...firstEvent, id: 1, title: "Updated LAN", approved: false})
    await settle()
    expect(vm.events[0].title).toBe("Updated LAN")
    expect(vm.calendarEvents[0].color).toBe("orange")

    vm.updateEvent({
      id: 2,
      title: "Board Game Night",
      startTime: "2099-02-21T18:00:00.000Z",
      endTime: "2099-02-21T20:00:00.000Z",
      approved: true,
    })
    await settle()
    expect(vm.events).toHaveLength(2)

    vm.deleteEvent(1)
    await settle()
    expect(vm.events).toHaveLength(1)
    expect(vm.events[0].id).toBe(2)

    expect(mockFindEvents).toHaveBeenCalledTimes(baseCalls)
  })

  it("navigates months and toggles selected event popover state", async () => {
    vi.useFakeTimers()

    const wrapper = shallowMount(EventCalendar, {
      global: {
        stubs: {
          VCalendar: true,
        },
      },
    })
    await settle()

    const vm = wrapper.vm as any
    const initialMonth = vm.displayedMonth
    vm.goNextMonth()
    expect(vm.displayedMonth).not.toBe(initialMonth)
    vm.goPrevMonth()
    vm.goToCurrentMonth()

    const nativeEvent = {
      stopPropagation: vi.fn(),
      target: document.createElement("button"),
    } as unknown as MouseEvent

    vm.showEvent(nativeEvent, {event: {raw: firstEvent}})
    expect(vm.selectedOpen).toBe(true)
    expect(vm.selectedEvent.id).toBe(1)

    vm.showEvent(nativeEvent, {event: {raw: firstEvent}})
    vi.advanceTimersByTime(11)
    expect(vm.selectedOpen).toBe(false)

    vi.useRealTimers()
  })
})
