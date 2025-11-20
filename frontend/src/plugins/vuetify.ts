import "@mdi/font/css/materialdesignicons.css"
import "@/styles/main.scss"

import type {VuetifyOptions} from "vuetify"
import {createVuetify} from "vuetify"
import * as directives from "vuetify/directives"

import {aliases as mdiAliases, mdi} from "vuetify/iconsets/mdi"
import {customAliases, customIconSet} from "@/plugins/icons/custom"

const vuetifyConfig: VuetifyOptions = {
  locale: {
    locale: "en",
  },
  icons: {
    defaultSet: "mdi",
    aliases: {
      ...mdiAliases,
      ...customAliases,
    },
    sets: {
      mdi,
      custom: customIconSet,
    },
  },
  theme: {
    variations: {
      colors: ["primary"],
      lighten: 1,
      darken: 1,
    },
    themes: {
      light: {
        dark: false,
        colors: {
          primary: "#3387FA",
          accent: "#000000",
          error: "#ff0022",
          anchor: "#3387FA",
          wallpaper: "#1E1E1E",
        },
      },
      dark: {
        dark: true,
        colors: {
          primary: "#3387FA",
          accent: "#A8FF00",
          error: "#ff0022",
          anchor: "#3387FA",
          wallpaper: "#343434",
          background: "#1E1E1E",
        },
      },
    },
  },
}

export default createVuetify({
  ...vuetifyConfig,
  directives,
})
