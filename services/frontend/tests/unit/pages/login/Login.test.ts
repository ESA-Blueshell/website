import {beforeEach, describe, expect, it, vi} from "vitest"
import {shallowMount} from "@vue/test-utils"
import {nextTick} from "vue"
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
  const {withVueRouter} = await import("../../helpers/testUtils")
  return withVueRouter(importOriginal, {
    route: mockRoute,
    router: {
      push: mockRouterPush,
    },
  })
})

vi.mock("vuex", async (importOriginal) => {
  const {withVuexUseStore} = await import("../../helpers/testUtils")
  return withVuexUseStore(importOriginal, mockStore)
})

vi.mock("@/services/api", () => ({
  authenticate: mockAuthenticate,
}))

vi.mock("@/plugins/handleNetworkError.js", () => ({
  $handleNetworkError: mockHandleNetworkError,
}))

// jsdom's location.assign is non-configurable, so swap the whole object for
// the two properties Login.vue reads.
function stubLocation(origin: string) {
  const original = globalThis.location
  const assign = vi.fn()
  Object.defineProperty(globalThis, "location", {
    configurable: true,
    value: {origin, assign},
  })
  return {
    assign,
    restore: () => Object.defineProperty(globalThis, "location", {
      configurable: true,
      value: original,
    }),
  }
}

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

  it.each([
    ["an off-origin absolute url", "https://evil.com/phish"],
    ["a protocol-relative url", "//evil.com/phish"],
    ["a javascript uri", "javascript:alert(document.domain)"],
  ])("ignores %s in the redirect and stays on the SPA", async (_label, redirect) => {
    const location = stubLocation("https://esa-blueshell.nl")
    mockAuthenticate.mockResolvedValue({
      status: 200,
      data: {username: "alice", userId: 4, expiration: Date.now() + 1000},
    })

    const wrapper = shallowMount(Login)
    await settle()

    ;(wrapper.vm as any).username = "alice"
    ;(wrapper.vm as any).password = "Secret123!"
    ;(wrapper.vm as any).form = {validate: vi.fn(async () => ({valid: true}))}
    mockRoute.query = {redirect}

    await (wrapper.vm as any).login()

    expect(location.assign).not.toHaveBeenCalled()
    expect(mockRouterPush).toHaveBeenCalledWith("/")
    location.restore()
  })

  it("does a full navigation to a trusted admin host", async () => {
    const location = stubLocation("https://esa-blueshell.nl")
    mockAuthenticate.mockResolvedValue({
      status: 200,
      data: {username: "alice", userId: 4, expiration: Date.now() + 1000},
    })

    const wrapper = shallowMount(Login)
    await settle()

    ;(wrapper.vm as any).username = "alice"
    ;(wrapper.vm as any).password = "Secret123!"
    ;(wrapper.vm as any).form = {validate: vi.fn(async () => ({valid: true}))}
    mockRoute.query = {redirect: "https://vault.esa-blueshell.nl/ui/vault"}

    await (wrapper.vm as any).login()

    expect(location.assign).toHaveBeenCalledWith("https://vault.esa-blueshell.nl/ui/vault")
    expect(mockRouterPush).not.toHaveBeenCalled()
    location.restore()
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

  it("renders navigation links for create-account and forgot-password with typed username", async () => {
    const wrapper = shallowMount(Login)
    await settle()

    expect(wrapper.find("[to='account/create']").exists()).toBe(true)

    ;(wrapper.vm as any).username = "alice"
    await nextTick()

    const forgotLink = wrapper.find("[to='login/forgor?username=alice']")
    expect(forgotLink.exists()).toBe(true)
  })
})
