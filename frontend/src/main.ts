import {createApp} from "vue"
import App from "./App.vue"
import router from "./plugins/router"
import VueSignaturePad from "vue-signature-pad"
import vuetify from "@/plugins/vuetify.ts"
import {loadFonts} from "@/plugins/webfontloader.ts"
import "@/plugins/validation.ts"
import store from "@/plugins/store"
import {setupAuthSync} from "@/plugins/authSync"
import {createVPhoneInput, selectPhoneCountryInput, VPhoneCountryFlagSvg} from "v-phone-input"
import "flag-icons/css/flag-icons.min.css"
import "v-phone-input/styles"

const vPhoneInput = createVPhoneInput({
  ...selectPhoneCountryInput,
  countryDisplayComponent: VPhoneCountryFlagSvg,
})

const app = createApp(App)
app.use(store)
app.use(router)
app.use(VueSignaturePad)
app.use(vuetify)
app.use(vPhoneInput)
loadFonts()
app.mount("#app")
setupAuthSync(store)
