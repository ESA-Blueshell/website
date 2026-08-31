export const THEME_STORAGE_KEY = "esa-blueshell.nl:darkMode"

export type ThemeName = "dark" | "light"

/** Which theme to paint first. Settled before Vuetify exists, so no cold load flashes. */
export const initialThemeName = (stored: string | null, prefersDark: boolean): ThemeName => {
  const dark = stored === null ? prefersDark : stored === "true"
  return dark ? "dark" : "light"
}

/** Vuetify's theme class stops at the app root. island.css reads this instead, so it
 *  also reaches the dialogs, which portal to the body. */
export const markDocumentTheme = (name: ThemeName): void => {
  document.documentElement.dataset.theme = name
}
