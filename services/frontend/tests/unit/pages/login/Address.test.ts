import {beforeEach, describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"
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
  const {withVuexUseStore} = await import("../../helpers/testUtils")
  return withVuexUseStore(importOriginal, mockStore)
})

vi.mock("vue-router", async (importOriginal) => {
  const {withVueRouter} = await import("../../helpers/testUtils")
  return withVueRouter(importOriginal, {
    route: mockRoute,
  })
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
    const wrapper = mount(Address, {
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

  it("calls handleNetworkError when address fetch fails", async () => {
    const error = new Error("network failure")
    mockFindAddressById.mockRejectedValue(error)

    mount(Address, {
      global: {
        stubs: {
          AddressForm: {
            props: ["modelValue", "userId"],
            template: "<div data-test='address-form' />",
          },
        },
      },
    })

    await settle()

    expect(mockHandleNetworkError).toHaveBeenCalledWith(error)
  })

  it("does not fetch address when login is missing", async () => {
    mockStore.getters.getLogin = null

    mount(Address, {
      global: {
        stubs: {
          AddressForm: {
            props: ["modelValue", "userId"],
            template: "<div data-test='address-form' />",
          },
        },
      },
    })

    await settle()

    expect(mockFindAddressById).not.toHaveBeenCalled()
  })

  it("does not fetch address when route has no id param", async () => {
    mockRoute.params = {}

    mount(Address, {
      global: {
        stubs: {
          AddressForm: {
            props: ["modelValue", "userId"],
            template: "<div data-test='address-form' />",
          },
        },
      },
    })

    await settle()

    expect(mockFindAddressById).not.toHaveBeenCalled()
  })
})
