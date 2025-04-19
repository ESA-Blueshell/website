import {createApp} from 'vue'
import App from './App.vue'
import router from './plugins/router.js'
import store from './plugins/store.js'
import Axios from 'axios';
import VueSignaturePad from 'vue-signature-pad';
import {registerPlugins} from "@/plugins/index.js";
import axios from 'axios'
import VueAxios from 'vue-axios'

import 'flag-icons/css/flag-icons.min.css';
import 'v-phone-input/dist/v-phone-input.css';
import {createVPhoneInput} from 'v-phone-input';

const vPhoneInput = createVPhoneInput({
  countryIconMode: 'svg',
});

Axios.defaults.baseURL = "https://localhost/api"
// Axios.defaults.baseURL = "https://esa-blueshell.nl/api/"


const app = createApp(App)
app.use(store)
app.use(router)
app.use(VueAxios, axios)
app.use(VueSignaturePad)
app.use(vPhoneInput)

registerPlugins(app)
app.mount('#app')
