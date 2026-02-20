import {beforeEach, describe, expect, it, vi} from "vitest"
import {shallowMount} from "@vue/test-utils"
import Account from "@/pages/login/Account.vue"
import {settle} from "../helpers"

const {
  mockStore,
  mockFindUserById,
  mockHandleNetworkError,
} = vi.hoisted(() => ({
  mockFindUserById: vi.fn(),
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
  const actual = await importOriginal<typeof import("vuex")>()
  return {
    ...actual,
    useStore: () => mockStore,
  }
})

vi.mock("@/services/api", () => ({
  findUserById: mockFindUserById,
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
    mockFindUserById.mockResolvedValue({
      data: {
        id: 42,
        firstName: "Jane",
      },
    })
  })

  it("loads account data for the logged-in user", async () => {
    const wrapper = shallowMount(Account, {
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
