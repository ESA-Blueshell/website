import {beforeEach, describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"
import {createMemoryHistory, createRouter} from "vue-router"
import {VListItem} from "vuetify/components"
import App from "@/App.vue"
import {
  COOKIE_CONSENT_STORAGE_KEY,
  encodeCookieConsentPayload,
  hasAcceptedCookiePolicy,
} from "@/config/policies"
import {settle} from "../helpers/testUtils"
import {forgetGames} from "@/domains/esports/island/useGames"

const {
  mockDisplay,
  mockTheme,
  mockRoute,
  mockStore,
  mockGoto,
  mockFindUserById,
  mockHandleNetworkError,
  mockAlert,
  matchMediaState,
} = vi.hoisted(() => {
  const mockTheme = {
    global: {
      current: {
        value: {
          dark: false,
        },
      },
    },
    change: vi.fn((name: string) => {
      mockTheme.global.current.value.dark = name === "dark"
    }),
  }

  return {
    mockDisplay: {
      mdAndDown: {value: false},
    },
    mockTheme,
    mockRoute: {
      meta: {
        requiresAuth: false,
      },
    },
    mockStore: {
      state: {
        statusSnackbarMessage: "",
      },
      getters: {
        isLoggedIn: true,
        isBoard: true,
        isAdmin: true,
        getLogin: {
          userId: 42,
          addressId: 7,
        },
      },
      commit: vi.fn(),
    },
    mockGoto: vi.fn(),
    mockFindUserById: vi.fn(),
    mockHandleNetworkError: vi.fn(),
    mockAlert: vi.fn(),
    matchMediaState: {
      dark: false,
      light: false,
    },
  }
})

vi.mock("vuetify", async (importOriginal) => {
  const {withVuetify} = await import("../helpers/testUtils")
  return withVuetify(importOriginal, {
    useDisplay: () => mockDisplay,
    useTheme: () => mockTheme,
  })
})

vi.mock("vue-router", async (importOriginal) => {
  const {withVueRouter} = await import("../helpers/testUtils")
  return withVueRouter(importOriginal, {
    route: mockRoute,
  })
})

vi.mock("vuex", async (importOriginal) => {
  const {withVuexUseStore} = await import("../helpers/testUtils")
  return withVuexUseStore(importOriginal, mockStore)
})

vi.mock("@/plugins/goto", () => ({
  $goto: mockGoto,
}))

vi.mock("@/plugins/handleNetworkError", () => ({
  $handleNetworkError: mockHandleNetworkError,
}))

vi.mock("@/services/api", () => ({
  findUserById: mockFindUserById,
  // The real one composes the page's own origin with /api; logOut() reads it.
  apiUrl: (path: string) => `${globalThis.location.origin}/api${path}`,
}))

// The esports menu lists what the records report as currently played, so a navbar case has to say
// which games there are.
vi.mock("@/domains/esports/adapters/esports", () => ({
  loadGames: vi.fn(async () => [
    {code: "GEOGUESSR", name: "GeoGuessr", slug: "geoguessr", accent: null, banner: null, icon: null, intro: null, sortIndex: 5, current: true},
    {code: "TRACKMANIA", name: "Trackmania", slug: "trackmania", accent: null, banner: null, icon: null, intro: null, sortIndex: 6, current: true},
    {code: "CSGO", name: "CS:GO", slug: "counter-strike-global-offensive", accent: null, banner: null, icon: null, intro: null, sortIndex: 7, current: false},
  ]),
}))

vi.mock("@/components/common/banners/FooterBanner.vue", () => ({
  default: {
    name: "FooterBanner",
    template: "<div data-test='footer-banner' />",
  },
}))

// A real router and the real router-link, because a navbar entry is only a destination a
// reader can follow once something resolves it into an href.
const mountWithLinks = async () => {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{path: "/:pathMatch(.*)*", component: {template: "<div />"}}],
  })
  await router.push("/")
  const wrapper = mount(App, {global: {plugins: [router], stubs: {RouterLink: false}}})
  await settle()
  return wrapper
}

const destinations = (wrapper: ReturnType<typeof mount>) =>
  wrapper.findAll("a[href]").map((link) => link.attributes("href"))

// The management menu renders its items in an overlay the application teleports out of the
// navbar, so they are read off the component tree, which follows a teleport, rather than off
// the markup under the wrapper. Nothing else in the navbar addresses `/management`.
const managementDestinations = (wrapper: ReturnType<typeof mount>) =>
  wrapper
    .findAllComponents(VListItem)
    .map((item) => item.props("to"))
    .filter((to): to is string => typeof to === "string" && to.startsWith("/management"))

describe("App navbar behavior", () => {
  beforeEach(() => {
    forgetGames()
    vi.clearAllMocks()
    localStorage.clear()

    mockDisplay.mdAndDown.value = false
    mockTheme.global.current.value.dark = false

    mockStore.state.statusSnackbarMessage = ""
    mockStore.getters.isLoggedIn = true
    mockStore.getters.isBoard = true
    mockStore.getters.isAdmin = true
    mockStore.getters.getLogin = {userId: 42, addressId: 7}
    mockStore.commit.mockImplementation((mutation: string, payload?: unknown) => {
      if (mutation === "setStatusSnackbarMessage") {
        mockStore.state.statusSnackbarMessage = String(payload ?? "")
      }
    })

    mockRoute.meta.requiresAuth = false

    mockFindUserById.mockResolvedValue({
      data: {
        id: 42,
        roles: ["MEMBER", "BOARD"],
      },
    })

    vi.stubGlobal("alert", mockAlert)
    vi.stubGlobal("matchMedia", vi.fn().mockImplementation((query: string) => ({
      matches: query.includes("dark") ? matchMediaState.dark : matchMediaState.light,
      media: query,
      onchange: null,
      addListener: vi.fn(),
      removeListener: vi.fn(),
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      dispatchEvent: vi.fn(),
    })))
  })

  it("shows full desktop navigation and management links for board/admin users", async () => {
    const wrapper = await mountWithLinks()

    expect(wrapper.find(".mdi-menu").exists()).toBe(false)
    expect(destinations(wrapper)).not.toContain("/login")

    expect(destinations(wrapper)).toContain("/esports/geoguessr")
    // Trackmania was played this season or last and is offered; CS:GO is history and is not.
    expect(destinations(wrapper)).toContain("/esports/trackmania")
    expect(destinations(wrapper)).not.toContain("/esports/counter-strike-global-offensive")
    expect(destinations(wrapper)).toContain("/blogs")
    await wrapper.get("[data-testid='nav-management']").trigger("click")
    await settle()

    expect(managementDestinations(wrapper)).toContain("/management/jobs")
    // A board is edited on the page it is read on, so the management entry is gone from here.
    expect(managementDestinations(wrapper)).not.toContain("/management/boards")
  })

  it("shows mobile menu toggle and contains the same key esports and association links", async () => {
    mockDisplay.mdAndDown.value = true

    const wrapper = await mountWithLinks()

    expect(wrapper.find(".mdi-menu").exists()).toBe(true)
    expect(destinations(wrapper)).toContain("/blogs")
    expect(destinations(wrapper)).toContain("/esports/geoguessr")
    expect(destinations(wrapper)).toContain("/esports/trackmania")
  })

  it("loads roles for the logged-in user on mount", async () => {
    mount(App)
    await settle()

    expect(mockFindUserById).toHaveBeenCalledWith({
      path: {
        userId: 42,
      },
      throwOnError: true,
    })
    expect(mockStore.commit).toHaveBeenCalledWith("setRoles", ["MEMBER", "BOARD"])
  })

  it("passes user-loading failures to network error handling", async () => {
    const error = new Error("load failed")
    mockFindUserById.mockRejectedValue(error)

    mount(App)
    await settle()

    expect(mockHandleNetworkError).toHaveBeenCalledWith(error)
  })

  it("toggles dark mode and persists the preference", async () => {
    localStorage.setItem(COOKIE_CONSENT_STORAGE_KEY, encodeCookieConsentPayload())
    const wrapper = mount(App)
    await settle()

    mockTheme.change.mockClear()
    ;(wrapper.vm as any).toggleDarkMode()

    expect(localStorage.getItem("esa-blueshell.nl:darkMode")).toBe("true")
    expect(mockTheme.change).toHaveBeenCalledWith("dark")
  })

  it("accepts cookies and closes the cookie snackbar", async () => {
    const wrapper = mount(App)
    await settle()

    expect((wrapper.vm as any).showCookieSnackbar).toBe(true)
    ;(wrapper.vm as any).acceptCookies()

    expect(hasAcceptedCookiePolicy(localStorage.getItem(COOKIE_CONSENT_STORAGE_KEY))).toBe(true)
    expect((wrapper.vm as any).showCookieSnackbar).toBe(false)
  })

  it("does not treat legacy cookie-consent key as accepted for the active policy", async () => {
    localStorage.setItem(COOKIE_CONSENT_STORAGE_KEY, "true")
    const wrapper = mount(App)
    await settle()

    expect((wrapper.vm as any).showCookieSnackbar).toBe(true)
    expect(hasAcceptedCookiePolicy(localStorage.getItem(COOKIE_CONSENT_STORAGE_KEY))).toBe(false)
  })

  it("logs out and redirects to home when the active route requires auth", async () => {
    const wrapper = mount(App)
    await settle()

    mockRoute.meta.requiresAuth = true

    const mockFetch = vi.fn().mockResolvedValue({ok: true})
    vi.stubGlobal("fetch", mockFetch)

    await (wrapper.vm as any).logOut()

    expect(mockStore.commit).toHaveBeenCalledWith("logout")
    expect(mockFetch).toHaveBeenCalledWith(
      // Same-origin, under /api -- the shape production and development share.
      expect.stringContaining("/api/auth/logout"),
      expect.objectContaining({method: "POST", credentials: "include"}),
    )
    expect(mockGoto).toHaveBeenCalledWith("/")
  })

  it("unlocks the konami-code snackbar and alert", async () => {
    const wrapper = mount(App)
    await settle()

    const sequence = [
      "ArrowUp",
      "ArrowUp",
      "ArrowDown",
      "ArrowDown",
      "ArrowLeft",
      "ArrowRight",
      "ArrowLeft",
      "ArrowRight",
      "b",
      "a",
      "Enter",
    ]
    for (const key of sequence) {
      globalThis.dispatchEvent(new KeyboardEvent("keydown", {key}))
    }

    expect((wrapper.vm as any).poggers).toBe(true)
    expect(mockAlert).toHaveBeenCalledWith("BIG SITECIE ENERGY")
  })
})
