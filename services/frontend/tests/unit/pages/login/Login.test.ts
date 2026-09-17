import {beforeEach, describe, expect, it, vi} from "vitest"
import {nextTick} from "vue"
import {createMemoryHistory, createRouter} from "vue-router"
import Login from "@/pages/login/Login.vue"
import {mountInApp, settle} from "../helpers"

const {
  mockRouterReplace,
  mockRoute,
  mockSignIn,
  mockHandleNetworkError,
  mockStore,
} = vi.hoisted(() => ({
  mockRouterReplace: vi.fn(),
  mockRoute: {
    query: {},
  },
  mockSignIn: vi.fn(),
  mockHandleNetworkError: vi.fn(),
  mockStore: {
    commit: vi.fn(),
    getters: {
      isLoggedIn: false,
    },
  },
}))

vi.mock("vue-router", async (importOriginal) => {
  const {withVueRouter} = await import("../../helpers/testUtils")
  return withVueRouter(importOriginal, {
    route: mockRoute,
    router: {
      replace: mockRouterReplace,
    },
  })
})

vi.mock("vuex", async (importOriginal) => {
  const {withVuexUseStore} = await import("../../helpers/testUtils")
  return withVuexUseStore(importOriginal, mockStore)
})

vi.mock("@/domains/auth", () => ({
  signIn: mockSignIn,
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
    mockStore.getters.isLoggedIn = false
    mockRoute.query = {}
  })

  it("logs in and routes to redirect path", async () => {
    mockSignIn.mockResolvedValue({
      outcome: "signed-in",
      login: {username: "alice", userId: 4, expiration: Date.now() + 1000},
    })

    const wrapper = mountInApp(Login)
    await settle()

    ;(wrapper.vm as any).username = "alice"
    ;(wrapper.vm as any).password = "Secret123!"
    ;(wrapper.vm as any).form = {
      validate: vi.fn(async () => ({valid: true})),
    }

    mockRoute.query = {redirect: "/events"}

    await (wrapper.vm as any).login()

    expect(mockSignIn).toHaveBeenCalledWith("alice", "Secret123!")
    expect(mockStore.commit).toHaveBeenCalledWith("setLogin", expect.objectContaining({username: "alice"}))
    expect(mockRouterReplace).toHaveBeenCalledWith("/events")
  })

  it.each([
    ["an off-origin absolute url", "https://evil.com/phish"],
    ["a protocol-relative url", "//evil.com/phish"],
    ["a javascript uri", "javascript:alert(document.domain)"],
  ])("ignores %s in the redirect and stays on the SPA", async (_label, redirect) => {
    const location = stubLocation("https://esa-blueshell.nl")
    mockSignIn.mockResolvedValue({
      outcome: "signed-in",
      login: {username: "alice", userId: 4, expiration: Date.now() + 1000},
    })

    const wrapper = mountInApp(Login)
    await settle()

    ;(wrapper.vm as any).username = "alice"
    ;(wrapper.vm as any).password = "Secret123!"
    ;(wrapper.vm as any).form = {validate: vi.fn(async () => ({valid: true}))}
    mockRoute.query = {redirect}

    await (wrapper.vm as any).login()

    expect(location.assign).not.toHaveBeenCalled()
    expect(mockRouterReplace).toHaveBeenCalledWith("/")
    location.restore()
  })

  it("does a full navigation to a trusted admin host", async () => {
    const location = stubLocation("https://esa-blueshell.nl")
    mockSignIn.mockResolvedValue({
      outcome: "signed-in",
      login: {username: "alice", userId: 4, expiration: Date.now() + 1000},
    })

    const wrapper = mountInApp(Login)
    await settle()

    ;(wrapper.vm as any).username = "alice"
    ;(wrapper.vm as any).password = "Secret123!"
    ;(wrapper.vm as any).form = {validate: vi.fn(async () => ({valid: true}))}
    mockRoute.query = {redirect: "https://vault.esa-blueshell.nl/ui/vault"}

    await (wrapper.vm as any).login()

    expect(location.assign).toHaveBeenCalledWith("https://vault.esa-blueshell.nl/ui/vault")
    expect(mockRouterReplace).not.toHaveBeenCalled()
    location.restore()
  })

  it("steps out of the way of a reader who is already signed in", async () => {
    mockStore.getters.isLoggedIn = true

    mountInApp(Login)
    await settle()

    expect(mockRouterReplace).toHaveBeenCalledWith("/account")
  })

  it("shows the form to a signed-in reader whose request was refused", async () => {
    mockStore.getters.isLoggedIn = true
    mockRoute.query = {redirect: "/account"}

    mountInApp(Login)
    await settle()

    expect(mockRouterReplace).not.toHaveBeenCalled()
  })

  it("sets snackbar message for unauthorized login", async () => {
    mockSignIn.mockResolvedValue({outcome: "rejected"})

    const wrapper = mountInApp(Login)
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
    // A real router and the real router-link, because the destination is only an href
    // once something resolves it — a stub leaves the button an anchor with nowhere to go.
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{path: "/:pathMatch(.*)*", component: {template: "<div />"}}],
    })
    await router.push("/")
    const wrapper = mountInApp(Login, {global: {plugins: [router], stubs: {RouterLink: false}}})
    await settle()

    expect(wrapper.get("[data-testid='login-create-account-btn']").attributes("href"))
      .toBe("/account/create")

    ;(wrapper.vm as any).username = "alice"
    await nextTick()

    expect(wrapper.get("[data-testid='login-forgot-password-btn']").attributes("href"))
      .toBe("/login/forgor?username=alice")
  })
})
