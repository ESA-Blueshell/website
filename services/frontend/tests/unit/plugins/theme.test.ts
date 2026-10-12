import {beforeEach, describe, expect, it, vi} from "vitest"
import {chooseTheme, siteTheme, startTheme} from "@/plugins/theme"

const STORED = "esa-blueshell.nl:darkMode"

const system = {dark: false, changed: [] as (() => void)[]}

const systemPrefers = (dark: boolean) => {
  system.dark = dark
  for (const tell of system.changed) tell()
}

describe("the site's theme", () => {
  beforeEach(() => {
    localStorage.clear()
    system.dark = false
    system.changed = []
    vi.stubGlobal("matchMedia", (query: string) => ({
      media: query,
      get matches() {
        return system.dark
      },
      addEventListener: (_event: string, tell: () => void) => system.changed.push(tell),
    }))
  })

  it("starts on the stored choice, whatever the system prefers", () => {
    localStorage.setItem(STORED, "true")

    expect(startTheme()).toBe("dark")
    expect(siteTheme.value).toBe("dark")
    expect(document.documentElement.dataset.theme).toBe("dark")

    localStorage.setItem(STORED, "false")
    system.dark = true

    expect(startTheme()).toBe("light")
  })

  it("starts on the system's preference when nothing is stored", () => {
    system.dark = true

    expect(startTheme()).toBe("dark")
    expect(document.documentElement.dataset.theme).toBe("dark")
    expect(localStorage.getItem(STORED)).toBeNull()
  })

  // Only "true" means dark. Nothing else writes the key.
  it("reads anything else stored as light", () => {
    localStorage.setItem(STORED, "yes")
    system.dark = true

    expect(startTheme()).toBe("light")
  })

  it("starts light where the browser answers neither question", () => {
    vi.stubGlobal("matchMedia", undefined)
    vi.spyOn(Storage.prototype, "getItem").mockImplementation(() => {
      throw new Error("storage is blocked")
    })

    expect(startTheme()).toBe("light")
  })

  it("shows the visitor's choice, on the document too, and stores it", () => {
    startTheme()

    chooseTheme("dark")

    expect(siteTheme.value).toBe("dark")
    expect(document.documentElement.dataset.theme).toBe("dark")
    expect(localStorage.getItem(STORED)).toBe("true")

    chooseTheme("light")

    expect(siteTheme.value).toBe("light")
    expect(document.documentElement.dataset.theme).toBe("light")
    expect(localStorage.getItem(STORED)).toBe("false")
  })

  it("still switches in a browser that refuses to store the choice", () => {
    startTheme()
    vi.spyOn(Storage.prototype, "setItem").mockImplementation(() => {
      throw new Error("storage is blocked")
    })

    chooseTheme("dark")

    expect(siteTheme.value).toBe("dark")
    expect(document.documentElement.dataset.theme).toBe("dark")
  })

  it("follows a change of the system's preference until the visitor chooses", () => {
    startTheme()

    systemPrefers(true)
    expect(siteTheme.value).toBe("dark")
    expect(document.documentElement.dataset.theme).toBe("dark")

    chooseTheme("light")
    systemPrefers(false)
    systemPrefers(true)
    expect(siteTheme.value).toBe("light")
    expect(document.documentElement.dataset.theme).toBe("light")
  })
})
