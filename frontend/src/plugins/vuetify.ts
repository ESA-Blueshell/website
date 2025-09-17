import '@mdi/font/css/materialdesignicons.css'
import '@/styles/main.scss'
import { createVuetify } from 'vuetify'
import type { VuetifyOptions } from 'vuetify'

const vuetifyConfig: VuetifyOptions = {
  locale: {
    locale: "en",
  },
  theme: {
    variations: {
      colors: ['primary'],
      lighten: 1,
      darken: 1,
    },

    themes: {
      light: {
        dark: false,
        colors: {
          primary: '#3387FA',
          accent: '#000000',
          error: '#ff0022',
          anchor: '#3387FA',
          wallpaper: '#1E1E1E',
        },
      },
      dark: {
        dark: true,
        colors: {
          primary: '#3387FA',
          accent: '#A8FF00',
          error: '#ff0022',
          anchor: '#3387FA',
          wallpaper: '#343434',
        },
      }
    },
  },
}

export default createVuetify(vuetifyConfig)
