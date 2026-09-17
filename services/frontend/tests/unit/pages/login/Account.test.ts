import {beforeEach, describe, expect, it, vi} from "vitest"
import Account from "@/pages/login/Account.vue"
import {mountInApp, settle} from "../helpers"

const {
  mockStore,
  mockReadUser,
  mockFindGames,
  mockHandleNetworkError,
} = vi.hoisted(() => ({
  mockReadUser: vi.fn(),
  mockFindGames: vi.fn(),
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

// The game handles the page shows reach for the catalogue as soon as they mount.
vi.mock("@/services/api", () => ({
  findGames: mockFindGames,
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

vi.mock("@/components/common/banners/TopBanner.vue", () => ({
  default: {
    name: "TopBanner",
    template: "<div />",
  },
}))

describe("Account page", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockFindGames.mockResolvedValue({data: []})
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
})
