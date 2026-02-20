import {beforeEach, describe, expect, it, vi} from "vitest"
import {shallowMount} from "@vue/test-utils"
import MembershipSignUp from "@/pages/membership/MembershipSignUp.vue"
import {settle} from "../helpers"

const {
  mockRoute,
  mockRouterPush,
  mockRouterReplace,
  mockStore,
  mockFindUserById,
  mockFindAddressById,
  mockResendUserActivation,
  mockHandleNetworkError,
  mockGoto,
} = vi.hoisted(() => ({
  mockRoute: {
    query: {},
    fullPath: "/membership/signup",
  },
  mockRouterPush: vi.fn(),
  mockRouterReplace: vi.fn(),
  mockStore: {
    getters: {
      isLoggedIn: false,
      getLogin: null,
    },
    commit: vi.fn(),
  },
  mockFindUserById: vi.fn(),
  mockFindAddressById: vi.fn(),
  mockResendUserActivation: vi.fn(),
  mockHandleNetworkError: vi.fn(),
  mockGoto: vi.fn(),
}))

vi.mock("vue-router", async (importOriginal) => {
  const actual = await importOriginal<typeof import("vue-router")>()
  return {
    ...actual,
    useRoute: () => mockRoute,
  }
})

vi.mock("@/plugins/router.ts", () => ({
  default: {
    push: mockRouterPush,
    replace: mockRouterReplace,
  },
}))

vi.mock("@/plugins/store", () => ({
  default: mockStore,
}))

vi.mock("@/plugins/handleNetworkError", () => ({
  $handleNetworkError: mockHandleNetworkError,
}))

vi.mock("@/plugins/goto", () => ({
  $goto: mockGoto,
}))

vi.mock("@/services/api", () => ({
  findUserById: mockFindUserById,
  findAddressById: mockFindAddressById,
  resendUserActivation: mockResendUserActivation,
  Role: {
    MEMBER: "MEMBER",
  },
}))

vi.mock("@/components/form/UserForm.vue", () => ({
  default: {
    name: "UserForm",
    template: "<div />",
  },
}))

vi.mock("@/components/form/AddressForm.vue", () => ({
  default: {
    name: "AddressForm",
    template: "<div />",
  },
}))

vi.mock("@/components/form/MembershipForm.vue", () => ({
  default: {
    name: "MembershipForm",
    template: "<div />",
  },
}))

vi.mock("@/components/common/banners/TopBanner.vue", () => ({
  default: {
    name: "TopBanner",
    template: "<div />",
  },
}))

describe("MembershipSignUp page", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockStore.getters.isLoggedIn = false
    mockStore.getters.getLogin = null
    mockRoute.query = {}
    mockFindUserById.mockResolvedValue({data: null})
    mockFindAddressById.mockResolvedValue({data: null})
    mockResendUserActivation.mockResolvedValue({})
  })

  it("moves from personal step to confirm-email and updates query without loops", async () => {
    const wrapper = shallowMount(MembershipSignUp, {
      global: {
        stubs: {
          UserForm: true,
          AddressForm: true,
          MembershipForm: true,
        },
      },
    })

    await settle()

    ;(wrapper.vm as any).user = {id: 99, username: "new-user", email: "new@example.com"}
    ;(wrapper.vm as any).userRef = {
      save: vi.fn(async () => ({id: 99, username: "new-user", email: "new@example.com"})),
    }
    ;(wrapper.vm as any).currentStep = 1

    await (wrapper.vm as any).nextStep()

    expect((wrapper.vm as any).currentStep).toBe(2)
    expect(mockRouterReplace).toHaveBeenCalledWith({query: {step: "2"}})
    expect(mockRouterReplace.mock.calls.length).toBeLessThan(5)
  })

  it("passes resend activation and sign-in redirect events", async () => {
    const wrapper = shallowMount(MembershipSignUp, {
      global: {
        stubs: {
          UserForm: true,
          AddressForm: true,
          MembershipForm: true,
        },
      },
    })

    ;(wrapper.vm as any).user = {username: "alice"}

    await (wrapper.vm as any).resendActivation()
    expect(mockResendUserActivation).toHaveBeenCalledWith({path: {username: "alice"}})

    await (wrapper.vm as any).handleVerified()
    expect(mockRouterPush).toHaveBeenCalledWith({
      name: "login",
      query: {redirect: "/membership/signup?step=2"},
    })
  })
})
