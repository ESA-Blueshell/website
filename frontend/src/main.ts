import {createApp} from "vue"
import App from "./App.vue"
import router from "./plugins/router"
import store from "./plugins/store"
import Axios from "axios"
import axios from "axios"
import VueSignaturePad from "vue-signature-pad"
import VueAxios from "vue-axios"
import vuetify from "@/plugins/vuetify.ts"
import {loadFonts} from "@/plugins/webfontloader.ts"
import "./plugins/localValidation.ts"

Axios.defaults.baseURL = "https://localhost/api"

const app = createApp(App)
app.use(store)
app.use(router)
app.use(VueAxios, axios)
app.use(VueSignaturePad)
app.use(vuetify)
loadFonts()
app.mount("#app")
