import {beforeEach, describe, expect, it, vi} from "vitest"

vi.mock("@mdi/font/css/materialdesignicons.css", () => ({}))
vi.mock("@/styles/main.scss", () => ({}))
vi.mock("@/assets/svgs/discord.svg?component", () => ({
  default: {name: "DiscordSvg", template: "<svg />"},
}))

// The plugin starts the theme as it is imported, so each case imports its own copy,
// and the theme module that copy follows.
const freshSite = async () => {
  vi.resetModules()
  const vuetify = (await import("@/plugins/vuetify")).default
  const {chooseTheme} = await import("@/plugins/theme")
  return {vuetifyTheme: vuetify.theme.global.name, chooseTheme}
}

describe("Vuetify's theme", () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it("starts on the theme the site settled on", async () => {
    localStorage.setItem("esa-blueshell.nl:darkMode", "true")

    const {vuetifyTheme} = await freshSite()

    expect(vuetifyTheme.value).toBe("dark")
    expect(document.documentElement.dataset.theme).toBe("dark")
  })

  it("follows the site's theme when the visitor switches", async () => {
    const {vuetifyTheme, chooseTheme} = await freshSite()
    expect(vuetifyTheme.value).toBe("light")

    chooseTheme("dark")
    expect(vuetifyTheme.value).toBe("dark")

    chooseTheme("light")
    expect(vuetifyTheme.value).toBe("light")
  })
})
