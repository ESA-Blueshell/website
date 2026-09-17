import {beforeEach, describe, expect, it, vi} from "vitest"
import Address from "@/pages/login/Address.vue"
import {mountInApp, settle} from "../helpers"

const {
  mockStore,
  mockRoute,
  mockReadAddress,
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
  mockReadAddress: vi.fn(),
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

vi.mock("@/domains/user", () => ({
  readAddress: mockReadAddress,
}))

vi.mock("@/plugins/handleNetworkError.ts", () => ({
  $handleNetworkError: mockHandleNetworkError,
}))

describe("Address page", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockReadAddress.mockResolvedValue({id: 12, city: "Enschede", country: "NL"})
  })

  it("loads address by route id and passes it into form", async () => {
    const wrapper = mountInApp(Address, {
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

    expect(mockReadAddress).toHaveBeenCalledWith(12)
    expect(wrapper.find("[data-test='address-form']").text()).toContain("Enschede::5")
  })

  it("calls handleNetworkError when address fetch fails", async () => {
    const error = new Error("network failure")
    mockReadAddress.mockRejectedValue(error)

    mountInApp(Address, {
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

    mountInApp(Address, {
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

    expect(mockReadAddress).not.toHaveBeenCalled()
  })

  it("does not fetch address when route has no id param", async () => {
    mockRoute.params = {}

    mountInApp(Address, {
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

    expect(mockReadAddress).not.toHaveBeenCalled()
  })
})
