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

const logoImg = (darkMode: boolean) =>
  shallowMount(SiteBar, {
    props: {darkMode},
    global: {
      stubs: {
        RouterLink: {template: "<a><slot /></a>"},
        VAppBar: passthrough,
        VNavigationDrawer: passthrough,
      },
    },
  }).find("img")

const logoOf = (darkMode: boolean): string | undefined => logoImg(darkMode).attributes("src")

describe("the site bar's logo", () => {
  beforeEach(() => vi.clearAllMocks())

  /** One lockup, drawn to sit on any ground, so the theme does not choose between copies. */
  it("draws the one wordmark whatever the bar is drawn on", () => {
    expect(logoOf(true)).toContain("topbarlogo")
    expect(logoOf(false)).toBe(logoOf(true))
  })

  /** Drawn 38px tall, so it is fetched at that height for the screen's density, not at 5154px across. */
  it("offers the wordmark at the bar's height for each density", () => {
    const img = logoImg(false)

    expect(img.attributes("src")).toContain("topbarlogo-38")
    expect(img.attributes("srcset")).toMatch(/topbarlogo-76[^,]* 2x, [^,]*topbarlogo-114[^,]* 3x$/)
  })
})
