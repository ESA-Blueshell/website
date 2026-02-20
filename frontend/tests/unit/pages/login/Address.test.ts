import {beforeEach, describe, expect, it, vi} from "vitest"
import {shallowMount} from "@vue/test-utils"
import Address from "@/pages/login/Address.vue"
import {settle} from "../helpers"

const {
  mockStore,
  mockRoute,
  mockFindAddressById,
  mockHandleNetworkError,
} = vi.hoisted(() => ({
  mockStore: {
    getters: {
      getLogin: {userId: 5},
    },
  },
  mockRoute: {
    params: {id: "12"},
  },
  mockFindAddressById: vi.fn(),
  mockHandleNetworkError: vi.fn(),
}))

vi.mock("vuex", async (importOriginal) => {
  const actual = await importOriginal<typeof import("vuex")>()
  return {
    ...actual,
    useStore: () => mockStore,
  }
})

vi.mock("vue-router", async (importOriginal) => {
  const actual = await importOriginal<typeof import("vue-router")>()
  return {
    ...actual,
    useRoute: () => mockRoute,
  }
})

vi.mock("@/services/api", () => ({
  findAddressById: mockFindAddressById,
}))

vi.mock("@/plugins/handleNetworkError.ts", () => ({
  $handleNetworkError: mockHandleNetworkError,
}))

describe("Address page", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockFindAddressById.mockResolvedValue({
      data: {id: 12, city: "Enschede", country: "NL"},
    })
  })

  it("loads address by route id and passes it into form", async () => {
    const wrapper = shallowMount(Address, {
      global: {
        stubs: {
          AddressForm: {
            props: ["modelValue", "userId"],
            template: "<div data-test='address-form'>{{ modelValue?.city }}::{{ userId }}</div>",
          },
        },
      },
    })

    await settle()

    expect(mockFindAddressById).toHaveBeenCalledWith({
      path: {id: 12},
      throwOnError: true,
    })
    expect(wrapper.find("[data-test='address-form']").text()).toContain("Enschede::5")
  })
})
