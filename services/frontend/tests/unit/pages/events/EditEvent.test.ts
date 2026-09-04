import {beforeEach, describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"
import EditEvent from "@/pages/events/EditEvent.vue"
import {settle} from "../helpers"

const {
  mockRoute,
  mockRouterBack,
  mockFindEventById,
} = vi.hoisted(() => ({
  mockRoute: {
    params: {},
  },
  mockRouterBack: vi.fn(),
  mockFindEventById: vi.fn(),
}))

vi.mock("vue-router", async (importOriginal) => {
  const actual = await importOriginal<typeof import("vue-router")>()
  return {
    ...actual,
    useRoute: () => mockRoute,
    useRouter: () => ({
      back: mockRouterBack,
    }),
  }
})

vi.mock("@/services/api", () => ({
  findEventById: mockFindEventById,
}))

describe("EditEvent page", () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it("renders create mode when no id is present", async () => {
    mockRoute.params = {}

    const wrapper = mount(EditEvent, {
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
    expect(mockRouterBack).toHaveBeenCalledTimes(1)
  })

  it("loads event in edit mode", async () => {
    mockRoute.params = {id: "33"}
    mockFindEventById.mockResolvedValue({data: {id: 33, title: "Hackathon"}})

    const wrapper = mount(EditEvent, {
      global: {
        stubs: {
          EventForm: true,
        },
      },
    })

    await settle()

    expect(mockFindEventById).toHaveBeenCalledWith({path: {id: 33}})
    expect((wrapper.vm as any).headerTitle).toBe("Edit Event")
    expect((wrapper.vm as any).event.id).toBe(33)
  })
})
