import {readonly, ref} from "vue"

const THEME_STORAGE_KEY = "esa-blueshell.nl:darkMode"
const SYSTEM_PREFERS_DARK = "(prefers-color-scheme: dark)"

export type ThemeName = "dark" | "light"

const current = ref<ThemeName>("light")

/** The theme the site shows. Vuetify's theme follows it: see plugins/vuetify. */
export const siteTheme = readonly(current)

const storedChoice = (): string | null => {
  try {
    return localStorage.getItem(THEME_STORAGE_KEY)
  } catch {
    return null
  }
}

const systemPreference = (): MediaQueryList | null => {
  try {
    return globalThis.matchMedia(SYSTEM_PREFERS_DARK)
  } catch {
    return null
  }
}

/** The stored choice, or the system's preference when the visitor has never chosen. */
const settledTheme = (): ThemeName => {
  const stored = storedChoice()
  const dark = stored === null ? systemPreference()?.matches === true : stored === "true"
  return dark ? "dark" : "light"
}

/** Vuetify's theme class stops at the app root. island.css reads the document's mark
 *  instead, so it also reaches the dialogs, which portal to the body. */
const showTheme = (name: ThemeName): void => {
  current.value = name
  document.documentElement.dataset.theme = name
}

/** Settles the theme before Vuetify exists, so no cold load flashes, then follows the
 *  system's preference for as long as the visitor has stored no choice of their own. */
export const startTheme = (): ThemeName => {
  showTheme(settledTheme())
  systemPreference()?.addEventListener("change", () => showTheme(settledTheme()))
  return current.value
}

/** The visitor's own choice. Shown first, so a browser that refuses to store it still switches. */
export const chooseTheme = (name: ThemeName): void => {
  showTheme(name)
  try {
    localStorage.setItem(THEME_STORAGE_KEY, String(name === "dark"))
  } catch {
    // The choice lasts this visit only.
  }
}
