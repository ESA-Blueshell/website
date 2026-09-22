import {beforeEach, describe, expect, it, vi} from "vitest"
import EditEvent from "@/pages/events/EditEvent.vue"
import {mountInApp, settle} from "../helpers"

const {
  mockRoute,
  mockRouterReplace,
  mockReadEvent,
  mockHistoryState,
} = vi.hoisted(() => ({
  mockRoute: {
    params: {},
  },
  mockRouterReplace: vi.fn(),
  mockReadEvent: vi.fn(),
  mockHistoryState: {back: null as string | null},
}))

vi.mock("vue-router", async (importOriginal) => {
  const actual = await importOriginal<typeof import("vue-router")>()
  return {
    ...actual,
    useRoute: () => mockRoute,
    useRouter: () => ({
      replace: mockRouterReplace,
      options: {history: {state: mockHistoryState}},
    }),
  }
})

vi.mock("@/domains/events", () => ({
  readEvent: mockReadEvent,
}))

describe("EditEvent page", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockHistoryState.back = null
  })

  function mountWithSubmit() {
    return mountInApp(EditEvent, {
      global: {
        stubs: {
          EventForm: {
            template: "<button data-test='submitted' @click=\"$emit('submitted', true)\">submit</button>",
          },
        },
      },
    })
  }

  it.each([
    ["the page the reader came from", "/events?season=2025", "/events?season=2025"],
    ["the event list when the login page is behind them", "/login?redirect=/events/edit/4", "/events"],
    ["the event list when nothing is behind them", null, "/events"],
    ["the event list when the entry behind is off-site", "//evil.example.com", "/events"],
  ])("saving returns to %s", async (_name, back, expected) => {
    mockRoute.params = {id: "4"}
    mockReadEvent.mockResolvedValue({id: 4, title: "Hackathon"})
    mockHistoryState.back = back

    const wrapper = mountWithSubmit()
    await settle()

    await wrapper.get("[data-test='submitted']").trigger("click")
    expect(mockRouterReplace).toHaveBeenCalledWith(expected)
  })

  it.each([
    ["creating", {}],
    ["editing", {id: "4"}],
  ])("stays on the page when the form says the save did not happen, while %s", async (_name, params) => {
    mockRoute.params = params
    mockReadEvent.mockResolvedValue({id: 4, title: "Hackathon"})

    const wrapper = mountInApp(EditEvent, {
      global: {
        stubs: {
          EventForm: {
            template: "<button data-test='submitted' @click=\"$emit('submitted', false)\">submit</button>",
          },
        },
      },
    })
    await settle()

    await wrapper.get("[data-test='submitted']").trigger("click")
    expect(mockRouterReplace).not.toHaveBeenCalled()
  })

  it("holds what the form edited, so a save sends the edited event", async () => {
    mockRoute.params = {id: "4"}
    mockReadEvent.mockResolvedValue({id: 4, title: "Hackathon"})

    const wrapper = mountInApp(EditEvent, {
      global: {
        stubs: {
          EventForm: {
            props: ["modelValue"],
            template: "<button data-test='edited' @click=\"$emit('update:modelValue', {id: 4, title: 'Renamed'})\">edit</button>",
          },
        },
      },
    })
    await settle()

    await wrapper.get("[data-test='edited']").trigger("click")
    expect((wrapper.vm as any).event.title).toBe("Renamed")
  })

  it("reports a refused read rather than drawing a form over an event it never got", async () => {
    mockRoute.params = {id: "4"}
    mockReadEvent.mockRejectedValue(new Error("refused"))
    const logged = vi.spyOn(console, "error").mockImplementation(() => {})

    const wrapper = mountInApp(EditEvent, {global: {stubs: {EventForm: true}}})
    await settle()

    expect(logged).toHaveBeenCalled()
    expect((wrapper.vm as any).event).toBeUndefined()
  })

  it("renders create mode when no id is present", async () => {
    mockRoute.params = {}

    const wrapper = mountInApp(EditEvent, {
      global: {
        stubs: {
          EventForm: {
            template: "<button data-test='submitted' @click=\"$emit('submitted', true)\">submit</button>",
          },
        },
      },
    })

    await settle()

    expect((wrapper.vm as any).headerTitle).toBe("Create Event")
    await wrapper.get("[data-test='submitted']").trigger("click")
    expect(mockRouterReplace).toHaveBeenCalledWith("/events")
  })

  it("loads event in edit mode", async () => {
    mockRoute.params = {id: "33"}
    mockReadEvent.mockResolvedValue({id: 33, title: "Hackathon"})

    const wrapper = mountInApp(EditEvent, {
      global: {
        stubs: {
          EventForm: true,
        },
      },
    })

    await settle()

    expect(mockReadEvent).toHaveBeenCalledWith(33)
    expect((wrapper.vm as any).headerTitle).toBe("Edit Event")
    expect((wrapper.vm as any).event.id).toBe(33)
  })
})
