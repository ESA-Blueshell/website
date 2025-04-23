<template>
  <v-app>
    <v-app-bar theme="dark">
      <v-btn
        v-if="display.mdAndDown"
        class="ml-2"
        icon="mdi-menu"
        @click="drawer = !drawer"
      />

      <router-link to="/">
        <img
          src="./assets/topbarlogo.png"
          alt="Blueshell logo"
          style="max-height: 64px;width: 100%"
          class="mr-2"
        >
      </router-link>

      <div
        v-if="display.lgAndUp"
        style="height: 90%"
      >
        <v-btn
          class="bar-button"
          to="/"
        >
          Home
        </v-btn>
        <v-btn
          class="bar-button"
          to="/membership"
        >
          Membership
        </v-btn>
        <v-menu
          :open-on-hover="true"
          open-delay="0"
          :offset="3"
        >
          <template #activator="{ props }">
            <v-btn
              class="bar-button"
              v-bind="props"
              to="/aboutus"
            >
              Association
              <v-icon>mdi-chevron-down</v-icon>
            </v-btn>
          </template>
          <v-list>
            <v-list-item to="/aboutus">
              About us
            </v-list-item>
            <v-list-item to="/board">
              Board
            </v-list-item>
            <v-list-item to="/committees">
              Committees
            </v-list-item>
            <v-list-item to="/blogs">
              Newsletters
            </v-list-item>
            <v-list-item to="/documents">
              Documents
            </v-list-item>
          </v-list>
        </v-menu>


        <v-menu
          :open-on-hover="true"
          open-delay="0"
          :offset="3"
        >
          <template #activator="{ props }">
            <v-btn
              class="bar-button"
              v-bind="props"
              to="/events"
            >
              events
              <v-icon>mdi-chevron-down</v-icon>
            </v-btn>
          </template>
          <v-list>
            <v-list-item to="/events">
              Events
            </v-list-item>
            <v-list-item
              v-if="isLoggedIn && isActive"
              to="/events/manage"
            >
              Manage events
            </v-list-item>
            <v-list-item
              to="/events/circuitShowdown"
            >
              Circuit Showdown
            </v-list-item>
          </v-list>
        </v-menu>


        <v-menu
          :open-on-hover="true"
          open-delay="0"
          :offset="3"
        >
          <template #activator="{ props }">
            <v-btn
              class="bar-button"
              v-bind="props"
              to="/esports/competitive-scene"
            >
              Esports
              <v-icon>mdi-chevron-down</v-icon>
            </v-btn>
          </template>
          <v-list>
            <v-list-item to="/esports/competitive-scene">
              Competitive scene
            </v-list-item>
            <v-list-item to="/esports/league-of-legends">
              League of Legends
            </v-list-item>
            <v-list-item to="/esports/counter-strike-2">
              Counter Strike 2
            </v-list-item>
            <v-list-item to="/esports/valorant">
              Valorant
            </v-list-item>
            <v-list-item to="/esports/rocketleague">
              Rocket League
            </v-list-item>
            <v-list-item to="/esports/trackmania">
              Trackmania
            </v-list-item>
          </v-list>
        </v-menu>
        <v-menu
          :open-on-hover="true"
          open-delay="0"
          :offset="3"
        >
          <template #activator="{ props }">
            <v-btn
              class="bar-button"
              v-bind="props"
              to="/partners/become-a-partner"
            >
              Partners
              <v-icon>mdi-chevron-down</v-icon>
            </v-btn>
          </template>
          <v-list>
            <v-list-item to="/partners/become-a-partner">
              Become a partner!
            </v-list-item>
            <v-list-item to="/partners/el-nino">
              El Niño – Digital Development
            </v-list-item>
          </v-list>
        </v-menu>
        <v-btn
          class="bar-button"

          to="/contact"
        >
          Contact
        </v-btn>
      </div>

      <v-spacer />

      <div style="height: 90%;display: flex;align-items: center;flex-wrap: nowrap;">
        <!--  Dark mode toggle    -->
        <v-btn
          class="mr-2"
          :icon="isDarkMode ? 'mdi-moon-waxing-crescent' : 'mdi-white-balance-sunny'"
          :color="isDarkMode ? 'accent' : 'white'"
          :class="{'roll-on': isDarkMode,'roll-off': !isDarkMode }"
          @click="toggleDarkMode"
        />

        <!-- LOGIN BUTTON/ACCOUNT DROPDOWN MENU -->
        <v-btn
          v-if="!isLoggedIn"
          class="bar-button ma-0 mr-2"
          to="/login"
        >
          Log In
        </v-btn>
        <v-menu
          v-else
          :offset="3"
        >
          <template #activator="{ props }">
            <v-btn
              class="bar-button ma-0 mr-2"
              variant="text"
              v-bind="props"
            >
              <v-icon size="x-large">
                mdi-account
              </v-icon>
            </v-btn>
          </template>
          <v-list>
            <v-list-item to="/account">
              Account
            </v-list-item>
            <v-list-item
              v-if="isBoard"
              to="/members/manage"
            >
              Manage members
            </v-list-item>
            <v-list-item
              v-if="isBoard"
              to="/committees/manage"
            >
              Manage committees
            </v-list-item>
            <v-list-item
              v-if="isBoard || isActive"
              to="/events/manage"
            >
              Manage events
            </v-list-item>
            <v-list-item @click="logOut">
              Log Out
            </v-list-item>
          </v-list>
        </v-menu>
      </div>
    </v-app-bar>

    <v-navigation-drawer
      v-model="drawer"
      temporary
    >
      <v-list
        nav
        class="pa-2"
      >
        <v-list-item to="/">
          Home
        </v-list-item>
        <v-list-item to="/membership">
          Membership
        </v-list-item>
        <v-list-group>
          <!-- why the fuck do list-groups not get a bottom margin but list items do what the fuck it's like they don't want us to use them in a navbar aaaaa -->
          <template #activator="{ props }">
            <v-list-item v-bind="props">
              Association
            </v-list-item>
          </template>

          <v-list-item to="/aboutus">
            About
          </v-list-item>
          <v-list-item to="/board">
            Board
          </v-list-item>
          <v-list-item to="/committees">
            Committees
          </v-list-item>
          <v-list-item to="/documents">
            Documents
          </v-list-item>
          <v-divider class="mb-1" />
        </v-list-group>


        <v-list-group>
          <template #activator="{ props }">
            <v-list-item v-bind="props">
              Events
            </v-list-item>
          </template>
          <v-list-item to="/events">
            Events
          </v-list-item>
          <v-list-item
            v-if="isLoggedIn && isActive"
            to="/events/manage"
          >
            Manage events
          </v-list-item>
          <v-list-item
            to="/events/circuitShowdown"
          >
            Circuit Showdown
          </v-list-item>
          <v-divider class="mb-1" />
        </v-list-group>

        <v-list-group>
          <template #activator="{ props }">
            <v-list-item v-bind="props">
              Esports
            </v-list-item>
          </template>
          <v-list-item to="/esports/competitive-scene">
            Competitive scene
          </v-list-item>
          <v-list-item to="/esports/league-of-legends">
            League of Legends
          </v-list-item>
          <v-list-item to="/esports/counter-strike-2">
            Counter Strike 2
          </v-list-item>
          <v-list-item to="/esports/valorant">
            Valorant
          </v-list-item>
          <v-list-item to="/esports/rocketleague">
            Rocket League
          </v-list-item>
          <v-list-item to="/esports/trackmania">
            Trackmania
          </v-list-item>
          <v-divider class="mb-1" />
        </v-list-group>

        <v-list-group>
          <template #activator="{ props }">
            <v-list-item v-bind="props">
              Partners
            </v-list-item>
          </template>
          <v-list-item to="/partners/become-a-partner">
            Become a partner!
          </v-list-item>
          <v-list-item to="/partners/el-nino">
            El Niño – Digital Development
          </v-list-item>
          <v-divider dark />
        </v-list-group>

        <v-list-item to="/contact">
          Contact
        </v-list-item>
      </v-list>

      <template #append>
        <v-btn
          icon="mdi-email"
          href="mailto:board@blueshell.utwente.nl"
          variant="plain"
          style="width: calc(100%/3)"
        />
        <v-btn
          icon="mdi-instagram"
          href="https://www.instagram.com/esablueshell/"
          target="_blank"
          variant="plain"
          style="width: calc(100%/3)"
        />
        <v-btn
          icon="mdi-facebook"
          href="https://www.facebook.com/BlueshellEsports/"
          target="_blank"
          variant="plain"
          style="width: calc(100%/3)"
        />
        <v-btn
          icon="mdi-twitch"
          href="https://www.twitch.tv/blueshellesports"
          target="_blank"
          variant="plain"
          style="width: calc(100%/3)"
        />
        <v-btn
          icon="mdi-twitter"
          href="https://twitter.com/BlueshellESA"
          target="_blank"
          variant="plain"
          style="width: calc(100%/3)"
        />
        <v-btn
          icon="mdi-linkedin"
          href="https://www.linkedin.com/company/blueshell-esports"
          target="_blank"
          variant="plain"
          style="width: calc(100%/3)"
        />
      </template>
    </v-navigation-drawer>


    <router-view />

    <bs-footer />


    <v-snackbar
      v-model="poggers"
      rounded
      timeout="105000"
    >
      <audio
        v-if="poggers"
        controls
        autoplay
      >
        <source
          src="./assets/blueshellanthem.mp3"
          type="audio/mpeg"
        >
      </audio>
    </v-snackbar>

    <v-snackbar
      v-model="statusSnackbarMessage"
      timeout="10000"
    >
      <!-- eslint-disable-next-line vue/no-v-html -->
      <span v-html="DOMPurify.sanitize(statusSnackbarMessage)" />
      <template #actions>
        <v-btn
          color="blue"
          variant="text"
          @click="statusSnackbarMessage = ''"
        >
          Close
        </v-btn>
      </template>
    </v-snackbar>

    <!-- Cookie snackbar -->
    <v-snackbar
      v-model="showCookieSnackbar"
      timeout="-1"
    >
      We're using cookies to keep you logged in. You can read more about how we use cookies in our
      <a
        href="https://esa-blueshell.nl/api/download/bsCookiePolicy.pdf"
        class="text-decoration-none"
        target="_blank"
      >Cookie Policy</a>.

      <template #actions>
        <v-btn
          color="primary"
          variant="text"
          @click="acceptCookies"
        >
          Got it
        </v-btn>
      </template>
    </v-snackbar>
  </v-app>
</template>

<script lang="ts">
import {ref, computed, onMounted} from 'vue'
import {useStore} from 'vuex'
import {useRouter, useRoute} from 'vue-router'
import {useTheme, useDisplay} from 'vuetify'
import axios from 'axios'
import FooterBanner from "@/components/banners/FooterBanner.vue";
import {$goto} from "@/plugins/goto";
import {$handleNetworkError} from "@/plugins/handleNetworkError";
import DOMPurify from "dompurify";

export default {
  components: {BsFooter: FooterBanner},
  setup() {
    const drawer = ref(false);
    const poggers = ref(false);
    const showCookieSnackbar = ref(false);
    const store = useStore();
    const router = useRouter();
    const route = useRoute();
    const theme = useTheme();
    const display = useDisplay();

    const statusSnackbarMessage = computed({
      get: () => store.state.statusSnackbarMessage,
      set: (message) => store.commit('setStatusSnackbarMessage', message)
    });

    const isLoggedIn = computed(() => store.getters.isLoggedIn);
    const isActive = computed(() => store.getters.isActive);
    const isBoard = computed(() => store.getters.isBoard);
    const login = computed(() => store.getters.getLogin);

    const isDarkMode = computed(() => theme.global.current.value.dark);

    const checkPrefersColorScheme = () => {
      if (window.matchMedia('(prefers-color-scheme: dark)').matches) {
        setDarkMode(true)
      } else if (window.matchMedia('(prefers-color-scheme: light)').matches) {
        setDarkMode(false)
      }
    }

    const setDarkMode = (dark: boolean) => {
      localStorage.setItem('esa-blueshell.nl:darkMode', dark.toString())
      theme.global.name.value = dark ? 'dark' : 'light'
    }

    const toggleDarkMode = () => {
      setDarkMode(!theme.global.current.value.dark)
    }

    const logOut = () => {
      document.cookie = 'login=;expires=Thu, 01 Jan 1970 00:00:01 GMT'
      store.commit('logout')
      if (route.meta.requiresAuth) {
        $goto('/')
      }
    }

    const acceptCookies = () => {
      localStorage.setItem('esa-blueshell.nl:cookiesAccepted', 'true')
      showCookieSnackbar.value = false
    }

    onMounted(() => {
      if (localStorage.getItem('esa-blueshell.nl:cookiesAccepted') !== 'true') {
        showCookieSnackbar.value = true;
      }

      const loginData = login.value;
      if (loginData) {
        axios
          .get(`users/${loginData.userId}`, {headers: {'Authorization': `Bearer ${loginData.token}`}})
          .then(response => {
            store.commit('setRoles', response.data.roles)
          })
          .catch(e => {
            if (e.response?.status === 401) {
              store.commit('statusSnackbarMessage', 'Login expired. You have been logged out.')
              store.commit('logout')
              if (route.meta.requiresAuth) {
                $goto('/')
              }
            } else {
              $handleNetworkError(e)
            }
          })
      }

      let keysPressed: string[] = [];
      window.addEventListener('keydown', event => {
        if (event.key) {
          const key = event.key.toLowerCase();
          keysPressed.push(key);
          if (keysPressed.toString().endsWith("arrowup,arrowup,arrowdown,arrowdown,arrowleft,arrowright,arrowleft,arrowright,b,a,enter")) {
            poggers.value = true;
            alert("BIG SITECIE ENERGY")
          }
        }
      });

      if (!localStorage.getItem('esa-blueshell.nl:darkMode')) {
        checkPrefersColorScheme();
      }
      window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', () => checkPrefersColorScheme())

      theme.global.name.value = localStorage.getItem('esa-blueshell.nl:darkMode') === 'true' ? 'dark' : 'light'
    });

    return {
      drawer,
      poggers,
      showCookieSnackbar,
      statusSnackbarMessage,
      display,
      isLoggedIn,
      isActive,
      isBoard,
      isDarkMode,
      toggleDarkMode,
      logOut,
      acceptCookies,
      theme,
      DOMPurify
    }
  },
}
</script>

<style>

.v-btn.bar-button {
  margin: 0 2px;
  height: 100% !important;
}

.v-footer > .v-btn {
  margin: 0 2px;
}

.roll-off {
  animation: rotate-out 0.5s both;
}

.roll-on {
  animation: rotate-in 0.5s both;
}

@keyframes rotate-in {
  0% {
    transform: rotate(-45deg);
  }
  50% {
    transform: rotate(22.5deg);
  }
  100% {
    transform: rotate(0);
  }
}


@keyframes rotate-out {
  0% {
    transform: rotate(-45deg);
  }
  50% {
    transform: rotate(22.5deg);
  }
  100% {
    transform: rotate(0);
  }
}


</style>
