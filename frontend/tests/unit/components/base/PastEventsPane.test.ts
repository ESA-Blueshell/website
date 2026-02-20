import {beforeEach, describe, expect, it, vi} from "vitest"
import {shallowMount} from "@vue/test-utils"
import PastEventsPane from "@/components/base/PastEventsPane.vue"
import {settle} from "../../helpers/testUtils"

const {
  mockFindEvents,
  mockRoute,
  mockRouter,
} = vi.hoisted(() => ({
  mockFindEvents: vi.fn(),
  mockRoute: {
    query: {} as Record<string, unknown>,
    path: "/events",
  },
  mockRouter: {
    resolve: vi.fn(({path, query}: {path: string; query: Record<string, unknown>}) => ({
      href: `${path}?page=${String(query.page)}`,
    })),
  },
}))

vi.mock("@/services/api", () => ({
  findEvents: mockFindEvents,
}))

vi.mock("vue-router", () => ({
  useRoute: () => mockRoute,
  useRouter: () => mockRouter,
}))

vi.mock("@/components/common/lists/EventList.vue", () => ({
  default: {
    name: "EventList",
    props: ["events"],
    template: "<div data-test='event-list'>{{ events?.length }}</div>",
  },
}))

describe("PastEventsPane", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockRoute.query = {}
    mockFindEvents.mockResolvedValue({
      data: {
        content: [
          {
            id: 11,
            title: "Past LAN",
            startTime: "2099-02-20T12:00:00.000Z",
            endTime: "2099-02-20T14:00:00.000Z",
          },
        ],
        page: {
          totalPages: 6,
        },
      },
    })
  })

  it("loads past events on mount and initializes page query when missing", async () => {
    const replaceSpy = vi.spyOn(globalThis.history, "replaceState")

    const wrapper = shallowMount(PastEventsPane, {
      props: {
        committees: [],
        eventSignUps: [],
        pageSize: 5,
      },
    })
    await settle()

    expect(mockFindEvents).toHaveBeenCalledWith({
      query: {
        to: expect.any(String),
        page: 0,
        size: 5,
        sort: ["startTime,desc"],
      },
    })
    expect(replaceSpy).toHaveBeenCalled()
    expect((wrapper.vm as any).pastEvents).toHaveLength(1)
  })

  it("updates URL and reloads data when current page changes", async () => {
    const replaceSpy = vi.spyOn(globalThis.history, "replaceState")

    const wrapper = shallowMount(PastEventsPane, {
      props: {
        committees: [],
        eventSignUps: [],
      },
    })
    await settle()

    mockFindEvents.mockClear()
    replaceSpy.mockClear()

    ;(wrapper.vm as any).currentPage = 2
    await settle()

    expect(mockFindEvents).toHaveBeenCalledWith({
      query: {
        to: expect.any(String),
        page: 1,
        size: 10,
        sort: ["startTime,desc"],
      },
    })
    expect(replaceSpy).toHaveBeenCalled()
  })

  it("calculates responsive pagination window without producing loops", async () => {
    const wrapper = shallowMount(PastEventsPane, {
      props: {
        committees: [],
        eventSignUps: [],
      },
    })
    await settle()

    ;(wrapper.vm as any).containerWidth = 580
    ;(wrapper.vm as any).pageMeta = {totalPages: 10}
    ;(wrapper.vm as any).currentPage = 5

    expect((wrapper.vm as any).totalSlots).toBe(10)
    expect((wrapper.vm as any).buttonsFitting).toBe(8)
    expect((wrapper.vm as any).totalVisible).toBe(6)
  })
})
