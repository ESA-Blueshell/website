import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"
import {createMemoryHistory, createRouter} from "vue-router"
import App from "@/App.vue"
import {
  COOKIE_CONSENT_STORAGE_KEY,
  hasAcceptedCookiePolicy,
} from "@/config/policies"
import {settle} from "../helpers/testUtils"
import {forgetGames} from "@/domains/esports/island/useGames"
import {forgetCommittees} from "@/domains/committees"
import {startTheme} from "@/plugins/theme"

const {
  mockDisplay,
  mockRoute,
  mockStore,
  mockGoto,
  mockFindUserById,
  mockHandleNetworkError,
  mockAlert,
  matchMediaState,
} = vi.hoisted(() => {
  return {
    mockDisplay: {
      mdAndDown: {value: false},
    },
    mockRoute: {
      path: "/",
      meta: {
        requiresAuth: false,
      },
    },
    mockStore: {
      state: {
        statusSnackbarMessage: "",
        statusSnackbarAction: null as {label: string; to: string} | null,
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
      // The bar asks the width the same way it asks the theme, and hands its entries to the
      // drawer once the window is too narrow to carry them.
      narrow: false,
    },
  }
})

vi.mock("vuetify", async (importOriginal) => {
  const {withVuetify} = await import("../helpers/testUtils")
  return withVuetify(importOriginal, {
    useDisplay: () => mockDisplay,
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
  twoFactorStanding: vi.fn(async () => ({data: null})),
  findCasualGames: vi.fn(async () => ({data: []})),
  SignInStatus: {SIGNED_IN: "SIGNED_IN", TWO_FACTOR_REQUIRED: "TWO_FACTOR_REQUIRED"},
  // The real one composes the page's own origin with /api; logOut() reads it.
  apiUrl: (path: string) => `${globalThis.location.origin}/api${path}`,
}))

// The esports menu lists what the records report as currently played, so a navbar case has to say
// which games there are.
vi.mock("@/domains/games/adapters/games", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/domains/games/adapters/games")>()),
  loadCasualGames: vi.fn(async () => [
    {code: "GEOGUESSR", name: "GeoGuessr", slug: "geoguessr", accent: null, banner: null, icon: null, intro: null, sortIndex: 5, inCompetition: true},
    {code: "TRACKMANIA", name: "Trackmania", slug: "trackmania", accent: null, banner: null, icon: null, intro: null, sortIndex: 6, inCompetition: true},
    {code: "CSGO", name: "CS:GO", slug: "counter-strike-global-offensive", accent: null, banner: null, icon: null, intro: null, sortIndex: 7, inCompetition: false},
  ]),
}))

// The committees menu lists the committees running now; an archived one is not offered.
vi.mock("@/domains/committees/adapters/committees", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/domains/committees/adapters/committees")>()),
  loadCommittees: vi.fn(async () => [
    {id: 1, name: "LanCie", slug: "lancie", archived: false},
    {id: 2, name: "OldCie", slug: "oldcie", archived: true},
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

// The management menu draws its entries where it stands rather than in an overlay elsewhere, so
// they are read off the bar itself. The drawer carries the same entries for a narrow screen and
// keeps them in the document while it is shut, which is why this looks in the bar alone.
const managementDestinations = (wrapper: ReturnType<typeof mount>) =>
  wrapper
    .findAll("header.site-bar a[href]")
    .map((link) => link.attributes("href"))
    .filter((to): to is string => Boolean(to?.startsWith("/management")))

describe("App navbar behavior", () => {
  // Two cases count a toast's time down on fake timers.
  afterEach(() => {
    vi.useRealTimers()
  })

  beforeEach(() => {
    forgetGames()
    forgetCommittees()
    vi.clearAllMocks()
    localStorage.clear()
    matchMediaState.narrow = false
    matchMediaState.dark = false

    mockStore.state.statusSnackbarMessage = ""
    mockStore.state.statusSnackbarAction = null
    mockStore.getters.isLoggedIn = true
    mockStore.getters.isBoard = true
    mockStore.getters.isAdmin = true
    mockStore.getters.getLogin = {userId: 42, addressId: 7}
    mockStore.commit.mockImplementation((mutation: string, payload?: unknown) => {
      if (mutation === "setStatusSnackbarMessage") {
        mockStore.state.statusSnackbarMessage = String(payload ?? "")
      }
    })

    mockRoute.path = "/"
    mockRoute.meta.requiresAuth = false

    mockFindUserById.mockResolvedValue({
      data: {
        id: 42,
        roles: ["MEMBER", "BOARD"],
      },
    })

    vi.stubGlobal("alert", mockAlert)
    vi.stubGlobal("matchMedia", vi.fn().mockImplementation((query: string) => ({
      matches: query.includes("max-width")
        ? matchMediaState.narrow
        : query.includes("dark") ? matchMediaState.dark : matchMediaState.light,
      media: query,
      onchange: null,
      addListener: vi.fn(),
      removeListener: vi.fn(),
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      dispatchEvent: vi.fn(),
    })))
    startTheme()
  })

  it("keeps a section's link beside its menu button, never inside it", async () => {
    const wrapper = await mountWithLinks()

    for (const section of ["association", "committees", "esports"]) {
      const button = wrapper.get(`[data-testid='nav-${section}-more']`)
      const link = wrapper.get(`[data-testid='nav-${section}']`)
      expect(button.element.tagName).toBe("BUTTON")
      expect(button.element.contains(link.element)).toBe(false)
      expect(button.find("a, button, [tabindex]").exists()).toBe(false)
      expect(button.attributes("aria-expanded")).toBe("false")
    }
  })

  it("shows full desktop navigation and management links for board/admin users", async () => {
    const wrapper = await mountWithLinks()

    expect(destinations(wrapper)).not.toContain("/login")

    // A section's pages are drawn once it is opened, which is what a reader does to reach them.
    await wrapper.get("[data-testid='nav-esports-more']").trigger("click")
    await settle()

    expect(destinations(wrapper)).toContain("/esports/geoguessr")
    // Trackmania was played this season or last and is offered; CS:GO is history and is not.
    expect(destinations(wrapper)).toContain("/esports/trackmania")
    expect(destinations(wrapper)).not.toContain("/esports/counter-strike-global-offensive")

    await wrapper.get("[data-testid='nav-association-more']").trigger("click")
    await settle()

    expect(destinations(wrapper)).toContain("/blogs")

    await wrapper.get("[data-testid='nav-committees-more']").trigger("click")
    await settle()

    expect(destinations(wrapper)).toContain("/committees/lancie")
    expect(destinations(wrapper)).not.toContain("/committees/oldcie")
    expect(managementDestinations(wrapper)).not.toContain("/management/jobs")

    await wrapper.get("[data-testid='nav-management']").trigger("click")
    await settle()

    expect(managementDestinations(wrapper)).toContain("/management/jobs")
    // A board is edited on the page it is read on, so the management entry is gone from here.
    expect(managementDestinations(wrapper)).not.toContain("/management/boards")
  })

  // The mark cannot be Vuetify's own active class: an entry that opens a menu addresses one
  // page of its section, so a reader standing on a game would leave Esports unmarked.
  it("marks the section the reader is in, from a page under it", async () => {
    mockRoute.path = "/esports/trackmania"

    const wrapper = await mountWithLinks()

    const marked = wrapper.findAll("a.bar-button--here").map((item) => item.attributes("href"))
    expect(marked).toEqual(["/esports"])
  })

  it("marks home only on home", async () => {
    const wrapper = await mountWithLinks()

    const marked = wrapper.findAll("a.bar-button--here").map((item) => item.attributes("href"))
    expect(marked).toEqual(["/"])
  })

  // The bar and the drawer render one declaration, so a narrow screen reaches the same pages.
  it("offers a drawer carrying the same key esports and association links", async () => {
    matchMediaState.narrow = true

    const wrapper = await mountWithLinks()

    expect(wrapper.find("[data-testid='nav-drawer']").exists()).toBe(false)

    await wrapper.get("[data-testid='nav-menu-toggle']").trigger("click")
    await settle()

    expect(wrapper.find("[data-testid='nav-drawer']").exists()).toBe(true)
    // The sections open folded, so their pages are drawn once somebody unfolds them.
    expect(destinations(wrapper)).not.toContain("/blogs")
    expect(destinations(wrapper)).not.toContain("/aboutus")
    expect(destinations(wrapper)).toContain("/membership")

    await wrapper.get("[data-testid='nav-drawer-association-more']").trigger("click")
    await wrapper.get("[data-testid='nav-drawer-esports-more']").trigger("click")

    expect(destinations(wrapper)).toContain("/blogs")
    expect(destinations(wrapper)).toContain("/esports/geoguessr")
    expect(destinations(wrapper)).toContain("/esports/trackmania")
    expect(wrapper.get("[data-testid='nav-drawer-esports-more']").attributes("aria-expanded"))
      .toBe("true")

    await wrapper.get("[data-testid='nav-drawer-esports-more']").trigger("click")
    expect(destinations(wrapper)).not.toContain("/esports/geoguessr")

    // Following a page closes the drawer, whether it is a section or a page under one.
    await wrapper.get("[data-testid='nav-drawer'] a[href='/blogs']").trigger("click")
    expect(wrapper.find("[data-testid='nav-drawer']").exists()).toBe(false)
    await wrapper.get("[data-testid='nav-menu-toggle']").trigger("click")
    await settle()
    await wrapper.get("[data-testid='nav-drawer'] a[href='/membership']").trigger("click")
    expect(wrapper.find("[data-testid='nav-drawer']").exists()).toBe(false)
  })

  it("opens the drawer on the section the reader is in, unfolded", async () => {
    matchMediaState.narrow = true
    mockRoute.path = "/esports/trackmania"

    const wrapper = await mountWithLinks()
    await wrapper.get("[data-testid='nav-menu-toggle']").trigger("click")
    await settle()

    expect(destinations(wrapper)).toContain("/esports/geoguessr")
    expect(destinations(wrapper)).not.toContain("/blogs")
  })

  // The icons in the corner were a second copy of the drawer's management and account groups.
  it("folds the account and management out of the right edge, from one icon", async () => {
    matchMediaState.narrow = true

    const wrapper = await mountWithLinks()
    expect(wrapper.find("[data-testid='nav-management']").exists()).toBe(false)

    await wrapper.get("[data-testid='nav-menu-toggle']").trigger("click")
    await settle()

    expect(destinations(wrapper).filter(to => to?.startsWith("/management"))).toHaveLength(0)
    expect(destinations(wrapper)).not.toContain("/account")

    await wrapper.get("[data-testid='nav-account']").trigger("click")
    await settle()

    // One overlay at a time: the side panel takes the drawer's place.
    expect(wrapper.find("[data-testid='nav-drawer']").exists()).toBe(false)
    const panel = wrapper.get("[data-testid='nav-side-panel']")
    const offered = panel.findAll("a[href]").map(link => link.attributes("href"))
    expect(offered.slice(0, 4)).toEqual(["/account", "/account/security", "/account/games", "/account/addresses/7"])
    expect(offered).toContain("/management/jobs")
    expect(wrapper.get("[data-testid='nav-account']").attributes("aria-expanded")).toBe("true")

    await panel.get("a[href='/account']").trigger("click")
    expect(wrapper.find("[data-testid='nav-side-panel']").exists()).toBe(false)
    await wrapper.get("[data-testid='nav-account']").trigger("click")
    await settle()
    await wrapper.get("[data-testid='nav-side-panel'] a[href='/management/jobs']").trigger("click")
    expect(wrapper.find("[data-testid='nav-side-panel']").exists()).toBe(false)

    // A second press on the icon folds the panel away again, and so does the scrim.
    await wrapper.get("[data-testid='nav-account']").trigger("click")
    await settle()
    await wrapper.get("[data-testid='nav-account']").trigger("click")
    expect(wrapper.find("[data-testid='nav-side-panel']").exists()).toBe(false)

    await wrapper.get("[data-testid='nav-account']").trigger("click")
    await wrapper.get("[data-testid='nav-drawer-scrim']").trigger("click")
    expect(wrapper.find("[data-testid='nav-side-panel']").exists()).toBe(false)
    expect(wrapper.find("[data-testid='nav-drawer-scrim']").exists()).toBe(false)
  })

  it("leaves management out of the panel for a member with none", async () => {
    matchMediaState.narrow = true
    mockStore.getters.isBoard = false
    mockStore.getters.isAdmin = false

    const wrapper = await mountWithLinks()
    await wrapper.get("[data-testid='nav-account']").trigger("click")
    await settle()

    expect(wrapper.get("[data-testid='nav-side-panel']").text()).not.toContain("Management")
  })

  it("lets the side panel go once the window is wide enough for the bar's own menus", async () => {
    matchMediaState.narrow = true

    const wrapper = await mountWithLinks()
    await wrapper.get("[data-testid='nav-account']").trigger("click")
    await settle()
    expect(wrapper.find("[data-testid='nav-side-panel']").exists()).toBe(true)

    // The width is asked through matchMedia, so its change listener is what a resize calls.
    const widths = (window.matchMedia as ReturnType<typeof vi.fn>).mock.results
      .map(result => result.value as {media: string; addEventListener: ReturnType<typeof vi.fn>})
      .filter(media => media.media.includes("max-width"))
    matchMediaState.narrow = false
    for (const media of widths) {
      for (const [, listener] of media.addEventListener.mock.calls) listener({matches: false})
    }
    await settle()

    expect(wrapper.find("[data-testid='nav-side-panel']").exists()).toBe(false)
    expect(wrapper.find("[data-testid='nav-drawer-scrim']").exists()).toBe(false)
  })

  it("logs out from the account panel and lets it go on Escape", async () => {
    matchMediaState.narrow = true

    const wrapper = await mountWithLinks()
    await wrapper.get("[data-testid='nav-account']").trigger("click")
    await settle()
    await wrapper.get("[data-testid='nav-side-panel']").trigger("keydown", {key: "Escape"})
    expect(wrapper.find("[data-testid='nav-side-panel']").exists()).toBe(false)

    vi.stubGlobal("fetch", vi.fn().mockResolvedValue({ok: true}))
    await wrapper.get("[data-testid='nav-account']").trigger("click")
    await settle()
    await wrapper.get("[data-testid='nav-log-out']").trigger("click")
    await settle()

    expect(wrapper.find("[data-testid='nav-side-panel']").exists()).toBe(false)
    expect(mockStore.commit).toHaveBeenCalledWith("logout")
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

  it("switches the theme from the bar, on the document too, and stores the choice", async () => {
    const wrapper = mount(App)
    await settle()

    await wrapper.get("button[aria-label='Switch to the dark theme']").trigger("click")

    expect(document.documentElement.dataset.theme).toBe("dark")
    expect(localStorage.getItem("esa-blueshell.nl:darkMode")).toBe("true")

    await wrapper.get("button[aria-label='Switch to the light theme']").trigger("click")

    expect(document.documentElement.dataset.theme).toBe("light")
    expect(localStorage.getItem("esa-blueshell.nl:darkMode")).toBe("false")
  })

  it("shows a status message as a toast, cleared with its Close button", async () => {
    // The store here is a plain object, so the message is in place before the page reads it.
    mockStore.state.statusSnackbarMessage = "Saved <b>twice</b><img src=x onerror=alert(1)>"
    const wrapper = mount(App)
    await settle()

    const status = wrapper.get("[data-testid='status-toast']")
    expect(status.attributes("role")).toBe("status")
    expect(status.text()).toContain("Saved twice")
    expect(status.find("b").exists()).toBe(true)
    expect(status.find("img").attributes("onerror")).toBeUndefined()
    expect(status.find("[data-testid='status-toast-action']").exists()).toBe(false)

    await status.get("[data-testid='status-toast-close']").trigger("click")

    expect(mockStore.state.statusSnackbarMessage).toBe("")
  })

  it("clears a status message by itself after ten seconds", async () => {
    vi.useFakeTimers()
    mockStore.state.statusSnackbarMessage = "Saved"
    mount(App)
    await settle()

    vi.advanceTimersByTime(9_999)
    expect(mockStore.state.statusSnackbarMessage).toBe("Saved")
    vi.advanceTimersByTime(1)
    expect(mockStore.state.statusSnackbarMessage).toBe("")
  })

  it("offers a status message's action, which clears the message and goes where it leads", async () => {
    mockStore.state.statusSnackbarMessage = "Your session has ended."
    mockStore.state.statusSnackbarAction = {label: "Log in", to: "/login"}
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{path: "/:pathMatch(.*)*", component: {template: "<div />"}}],
    })
    await router.push("/")
    const wrapper = mount(App, {global: {plugins: [router], stubs: {RouterLink: false}}})
    await settle()

    const action = wrapper.get("[data-testid='status-toast-action']")
    expect(action.text()).toBe("Log in")
    await action.trigger("click")
    await settle()

    expect(mockStore.commit).toHaveBeenCalledWith("clearStatusSnackbar")
    expect(router.currentRoute.value.path).toBe("/login")
  })

  it("shows the cookie notice until Got it is pressed, then keeps it away", async () => {
    // The toast leaves through a transition, which a test has no frame to wait out.
    const wrapper = mount(App, {global: {stubs: {transition: true}}})
    await settle()

    const notice = wrapper.get("[data-testid='cookie-toast']")
    expect(notice.text()).toContain("We're using cookies to keep you logged in.")
    expect(notice.get("a").text()).toBe("Cookie Policy")
    expect(notice.get("a").attributes("target")).toBe("_blank")

    await notice.get("[data-testid='cookie-toast-accept']").trigger("click")

    expect(hasAcceptedCookiePolicy(localStorage.getItem(COOKIE_CONSENT_STORAGE_KEY))).toBe(true)
    expect(wrapper.find("[data-testid='cookie-toast']").exists()).toBe(false)

    const again = mount(App)
    await settle()
    expect(again.find("[data-testid='cookie-toast']").exists()).toBe(false)
  })

  it("does not treat legacy cookie-consent key as accepted for the active policy", async () => {
    localStorage.setItem(COOKIE_CONSENT_STORAGE_KEY, "true")
    const wrapper = mount(App)
    await settle()

    expect(wrapper.find("[data-testid='cookie-toast']").exists()).toBe(true)
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

  it("plays the anthem in a toast on the konami code, with an alert, until the anthem is over", async () => {
    vi.useFakeTimers()
    // The toast leaves through a transition, which a test has no frame to wait out.
    const wrapper = mount(App, {global: {stubs: {transition: true}}})
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

    await settle()

    expect(wrapper.get("[data-testid='anthem-toast'] audio").attributes("autoplay")).toBeDefined()
    expect(mockAlert).toHaveBeenCalledWith("BIG SITECIE ENERGY")

    vi.advanceTimersByTime(105_000)
    await settle()
    expect(wrapper.find("[data-testid='anthem-toast']").exists()).toBe(false)
  })
})
