import {beforeEach, describe, expect, it, vi} from "vitest"
import {shallowMount} from "@vue/test-utils"
import SiteBar from "@/components/common/SiteBar.vue"

vi.mock("vuetify", async (importOriginal) => ({
  ...(await importOriginal<Record<string, unknown>>()),
  useDisplay: () => ({mdAndDown: {value: false}}),
}))

vi.mock("vuex", async (importOriginal) => ({
  ...(await importOriginal<Record<string, unknown>>()),
  useStore: () => ({getters: {isLoggedIn: false, isBoard: false, isAdmin: false, getLogin: null}}),
}))

vi.mock("@/domains/esports/island/useGames", () => ({
  useGames: () => ({current: {value: []}}),
}))

// The bar marks the section you are on, so it reads the route.
vi.mock("vue-router", async (importOriginal) => ({
  ...(await importOriginal<Record<string, unknown>>()),
  useRoute: () => ({path: "/"}),
}))

// The bar itself has to render, not be stubbed away: the logo is inside it.
const passthrough = {template: "<div><slot /></div>"}

const logoOf = (darkMode: boolean): string | undefined =>
  shallowMount(SiteBar, {
    props: {darkMode},
    global: {
      stubs: {
        RouterLink: {template: "<a><slot /></a>"},
        VAppBar: passthrough,
        VNavigationDrawer: passthrough,
      },
    },
  })
    .find("img")
    .attributes("src")

describe("the site bar's logo", () => {
  beforeEach(() => vi.clearAllMocks())

  /** One lockup, drawn to sit on any ground, so the theme does not choose between copies. */
  it("draws the one wordmark whatever the bar is drawn on", () => {
    expect(logoOf(true)).toContain("topbarlogo")
    expect(logoOf(false)).toBe(logoOf(true))
  })
})
