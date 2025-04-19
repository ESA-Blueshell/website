// Styles
import '@mdi/font/css/materialdesignicons.css'
import '@/styles/main.scss'
// Composables
import {createVuetify} from 'vuetify'
import en from 'vuetify/lib/locale/en.mjs'

export default createVuetify({
  locale: {
    locale: en,
  },
  theme: {
    options: {customProperties: true},

    variations: {
      colors: ['primary'],
      lighten: 1
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
})

