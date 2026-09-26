import {beforeEach, describe, expect, it, vi} from "vitest"
import Address from "@/pages/login/Address.vue"
import {mountInApp, settle} from "../helpers"

const {
  mockStore,
  mockRoute,
  mockReadAddress,
  mockHandleNetworkError,
  mockRouter,
} = vi.hoisted(() => ({
  mockStore: {
    getters: {
      getLogin: {userId: 5},
    },
    dispatch: vi.fn(),
  },
  mockRoute: {
    params: {id: "12"} as {id?: string},
  },
  mockRouter: {replace: vi.fn()},
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
    router: mockRouter,
  })
})

vi.mock("@/domains/user", () => ({
  readAddress: mockReadAddress,
}))

vi.mock("@/plugins/handleNetworkError.ts", () => ({
  $handleNetworkError: mockHandleNetworkError,
}))

vi.mock("@/components/common/AccountFrame.vue", () => ({
  default: {name: "AccountFrame", props: ["heading"], template: "<div><slot /></div>"},
}))

describe("Address page", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockStore.getters.getLogin = {userId: 5}
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

  it("makes the first address written the account's, and moves to it", async () => {
    mockRoute.params = {}
    const AddressForm = {
      name: "AddressForm", props: ["modelValue", "userId"], emits: ["submitted", "update:modelValue"],
      template: "<div />",
    }
    const wrapper = mountInApp(Address, {global: {stubs: {AddressForm}}})
    await settle()
    const form = wrapper.findComponent({name: "AddressForm"})

    form.vm.$emit("submitted", false)
    form.vm.$emit("update:modelValue", {id: 31, city: "Enschede"})
    await settle()
    form.vm.$emit("submitted", true)

    expect(mockStore.dispatch).toHaveBeenCalledTimes(1)
    expect(mockStore.dispatch).toHaveBeenCalledWith("setAddressId", 31)
    expect(mockRouter.replace).toHaveBeenCalledWith("/account/addresses/31")
    mockRoute.params = {id: "12"}
  })

  it("leaves an address that already had its page where it is", async () => {
    const AddressForm = {name: "AddressForm", props: ["modelValue", "userId"], emits: ["submitted"], template: "<div />"}
    const wrapper = mountInApp(Address, {global: {stubs: {AddressForm}}})
    await settle()
    wrapper.findComponent({name: "AddressForm"}).vm.$emit("submitted", true)

    expect(mockRouter.replace).not.toHaveBeenCalled()
  })
})
