import {beforeEach, describe, expect, it, vi} from "vitest"
import {shallowMount} from "@vue/test-utils"
import Login from "@/pages/login/Login.vue"
import {settle} from "../helpers"

const {
  mockRouterPush,
  mockRoute,
  mockAuthenticate,
  mockHandleNetworkError,
  mockStore,
} = vi.hoisted(() => ({
  mockRouterPush: vi.fn(),
  mockRoute: {
    query: {},
  },
  mockAuthenticate: vi.fn(),
  mockHandleNetworkError: vi.fn(),
  mockStore: {
    commit: vi.fn(),
    getters: {
      tokenExpired: true,
    },
  },
}))

vi.mock("vue-router", async (importOriginal) => {
  const actual = await importOriginal<typeof import("vue-router")>()
  return {
    ...actual,
    useRoute: () => mockRoute,
    useRouter: () => ({
      push: mockRouterPush,
    }),
  }
})

vi.mock("vuex", async (importOriginal) => {
  const actual = await importOriginal<typeof import("vuex")>()
  return {
    ...actual,
    useStore: () => mockStore,
  }
})

vi.mock("@/services/api", () => ({
  authenticate: mockAuthenticate,
}))

vi.mock("@/plugins/handleNetworkError.js", () => ({
  $handleNetworkError: mockHandleNetworkError,
}))

describe("Login page", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockStore.getters.tokenExpired = true
    mockRoute.query = {}
  })

  it("logs in and routes to redirect path", async () => {
    mockAuthenticate.mockResolvedValue({
      status: 200,
      data: {username: "alice", userId: 4, expiration: Date.now() + 1000},
    })

    const wrapper = shallowMount(Login)
    await settle()

    ;(wrapper.vm as any).username = "alice"
    ;(wrapper.vm as any).password = "Secret123!"
    ;(wrapper.vm as any).form = {
      validate: vi.fn(async () => ({valid: true})),
    }

    mockRoute.query = {redirect: "/events"}

    await (wrapper.vm as any).login()

    expect(mockAuthenticate).toHaveBeenCalledWith({
      body: {
        username: "alice",
        password: "Secret123!",
      },
    })
    expect(mockStore.commit).toHaveBeenCalledWith("setLogin", expect.objectContaining({username: "alice"}))
    expect(mockRouterPush).toHaveBeenCalledWith("/events")
  })

  it("redirects straight to account if token is not expired", async () => {
    mockStore.getters.tokenExpired = false

    shallowMount(Login)
    await settle()

    expect(mockRouterPush).toHaveBeenCalledWith("/account")
  })

  it("sets snackbar message for unauthorized login", async () => {
    mockAuthenticate.mockResolvedValue({status: 401})

    const wrapper = shallowMount(Login)
    ;(wrapper.vm as any).form = {
      validate: vi.fn(async () => ({valid: true})),
    }
    ;(wrapper.vm as any).username = "alice"
    ;(wrapper.vm as any).password = "wrong"

    await (wrapper.vm as any).login()

    expect(mockStore.commit).toHaveBeenCalledWith(
      "setStatusSnackbarMessage",
      "Incorrect login credentials. Please double check your username and password.",
    )
  })
})
