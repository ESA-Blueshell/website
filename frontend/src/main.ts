import {createApp} from "vue"
import App from "./App.vue"
import router from "./plugins/router"
import VueSignaturePad from "vue-signature-pad"
import vuetify from "@/plugins/vuetify.ts"
import {loadFonts} from "@/plugins/webfontloader.ts"
import "@/plugins/validation.ts"
import store from "@/plugins/store"
import {readJsonCookie} from "@/plugins/cookies"
import type {Login} from "@/services/api"

const app = createApp(App)
app.use(store)
app.use(router)
app.use(VueSignaturePad)
app.use(vuetify)
loadFonts()
app.mount("#app")

window.addEventListener("storage", (e) => {
  if (e.key !== "auth:ping") return
  const login = readJsonCookie<Login>("login") || null
  store.commit("setLoginState", login)
})
