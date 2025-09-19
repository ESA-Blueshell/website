import { createApp } from 'vue'
import App from './App.vue'
import router from './plugins/router'
import store from './plugins/store'
import Axios from 'axios'
import VueSignaturePad from 'vue-signature-pad'
import { registerPlugins } from '@/plugins'
import axios from 'axios'
import VueAxios from 'vue-axios'

Axios.defaults.baseURL = 'https://localhost/api'

const app = createApp(App)
app.use(store)
app.use(router)
app.use(VueAxios, axios)
app.use(VueSignaturePad)
registerPlugins(app)
app.mount('#app')
