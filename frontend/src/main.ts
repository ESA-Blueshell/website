import {createApp} from "vue"
import App from "./App.vue"
import router from "./plugins/router"
import VueSignaturePad from "vue-signature-pad"
import vuetify from "@/plugins/vuetify.ts"
import {loadFonts} from "@/plugins/webfontloader.ts"
import "@/plugins/validation.ts"
import store from "@/plugins/store"
import {setupAuthSync} from "@/plugins/authSync"

const app = createApp(App)
app.use(store)
app.use(router)
app.use(VueSignaturePad)
app.use(vuetify)
loadFonts()
app.mount("#app")
setupAuthSync(store)
