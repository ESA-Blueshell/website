import {beforeEach, describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"
import Account from "@/pages/login/Account.vue"
import {settle} from "../helpers"

const {
  mockStore,
  mockFindUserById,
  mockFindGames,
  mockHandleNetworkError,
} = vi.hoisted(() => ({
  mockFindUserById: vi.fn(),
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

// The game handles the page shows reach for the catalogue as soon as they mount.
vi.mock("@/services/api", () => ({
  findUserById: mockFindUserById,
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
    mockFindUserById.mockResolvedValue({
      data: {
        id: 42,
        firstName: "Jane",
      },
    })
  })

  it("loads account data for the logged-in user", async () => {
    const wrapper = mount(Account, {
      global: {
        stubs: {
          UserForm: true,
        },
      },
    })

    await settle()

    expect(mockFindUserById).toHaveBeenCalledWith({
      path: {userId: 42},
    })
    expect(wrapper.text()).toContain("Hello Jane")
    expect(wrapper.find("user-form-stub").exists()).toBe(true)
  })
})
