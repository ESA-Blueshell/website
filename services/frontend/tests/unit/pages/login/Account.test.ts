import {beforeEach, describe, expect, it, vi} from "vitest"
import Account from "@/pages/login/Account.vue"
import {mountInApp, settle} from "../helpers"

const {
  mockStore,
  mockReadUser,
  mockHandleNetworkError,
} = vi.hoisted(() => ({
  mockReadUser: vi.fn(),
  mockHandleNetworkError: vi.fn(),
  mockStore: {
    getters: {
      isMember: true,
      isBoard: false,
      isActive: true,
      getLogin: {userId: 42},
    },
  },
}))

vi.mock("vuex", async (importOriginal) => {
  const {withVuexUseStore} = await import("../../helpers/testUtils")
  return withVuexUseStore(importOriginal, mockStore)
})

vi.mock("@/domains/user", () => ({
  readUser: mockReadUser,
}))

vi.mock("@/plugins/handleNetworkError.ts", () => ({
  $handleNetworkError: mockHandleNetworkError,
}))

vi.mock("@/components/form/UserForm.vue", () => ({
  default: {
    name: "UserForm",
    template: "<div data-test='user-form' />",
  },
}))

vi.mock("@/components/common/AccountFrame.vue", () => ({
  default: {name: "AccountFrame", props: ["heading", "crumb", "islandContent", "tabs", "eyebrow", "body"], template: "<div><slot /><slot name=\"actions\" /></div>"},
}))

describe("Account page", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockReadUser.mockResolvedValue({
      id: 42,
      firstName: "Jane",
    })
  })

  it("loads account data for the logged-in user", async () => {
    const wrapper = mountInApp(Account, {
      global: {
        stubs: {
          UserForm: true,
        },
      },
    })

    await settle()

    expect(mockReadUser).toHaveBeenCalledWith(42)
    expect(wrapper.text()).toContain("Hello Jane")
    expect(wrapper.find("user-form-stub").exists()).toBe(true)
  })

  it("leaves the game handles to their own page", async () => {
    const wrapper = mountInApp(Account, {global: {stubs: {UserForm: true}}})

    await settle()

    expect(wrapper.find("game-handles-stub").exists()).toBe(false)
    expect(wrapper.text()).not.toContain("Game handles")
  })
})
