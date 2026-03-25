<template>
  <v-app>
    <v-app-bar theme="dark">
      <v-btn
        v-if="display.mdAndDown.value"
        class="ml-2"
        icon="mdi-menu"
        @click="drawer = !drawer"
      />

      <router-link to="/">
        <img
          alt="Blueshell logo"
          class="mr-2"
          src="@/assets/topbarlogo.png"
          style="max-height: 64px;width: 100%"
        >
      </router-link>

      <div
        v-if="!display.mdAndDown.value"
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
          :offset="3"
          :open-on-hover="true"
          open-delay="0"
        >
          <template #activator="{ props }">
            <v-btn
              class="bar-button"
              to="/aboutus"
              v-bind="props"
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


        <v-btn
          class="bar-button"
          to="/events"
        >
          Events
        </v-btn>
        <v-menu
          :offset="3"
          :open-on-hover="true"
          open-delay="0"
        >
          <template #activator="{ props }">
            <v-btn
              class="bar-button"
              to="/esports/competitive-scene"
              v-bind="props"
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
            <v-list-item to="/esports/geoguessr">
              Geoguessr
            </v-list-item>
          </v-list>
        </v-menu>
        <v-menu
          :offset="3"
          :open-on-hover="true"
          open-delay="0"
        >
          <template #activator="{ props }">
            <v-btn
              class="bar-button"
              to="/partners/become-a-partner"
              v-bind="props"
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
            <v-list-item to="/partners/marketing-maatwerk">
              Marketing Maatwerk
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
          :class="{'roll-on': isDarkMode,'roll-off': !isDarkMode }"
          :color="isDarkMode ? 'accent' : 'white'"
          :icon="isDarkMode ? 'mdi-moon-waxing-crescent' : 'mdi-white-balance-sunny'"
          class="mr-2"
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
          v-if="isBoard || isAdmin"
          :offset="3"
        >
          <template #activator="{ props }">
            <v-btn
              class="bar-button ma-0 mr-2"
              v-bind="props"
              variant="text"
            >
              <v-icon size="x-large">
                custom:account-multiple-edit
              </v-icon>
            </v-btn>
          </template>

          <v-list>
            <v-list-item
              v-if="isBoard"
              to="/addresses/manage"
            >
              Manage addresses
            </v-list-item>
            <v-list-item
              v-if="isBoard"
              to="/recovery/manage"
            >
              Manage account recovery
            </v-list-item>
            <v-list-item
              v-if="isBoard"
              to="/committees/manage"
            >
              Manage committees
            </v-list-item>
            <v-list-item
              v-if="isBoard"
              to="/contributions/manage"
            >
              Manage contributions
            </v-list-item>
            <v-list-item
              v-if="isBoard"
              to="/members/manage"
            >
              Manage members
            </v-list-item>
            <v-list-item
              v-if="isAdmin"
              to="/management/jobs"
            >
              Manage jobs
            </v-list-item>
            <v-list-item
              v-if="isAdmin"
              to="/management/emails"
            >
              Manage emails
            </v-list-item>
            <v-list-item
              v-if="isAdmin"
              to="/management/infra"
            >
              Infrastructure
            </v-list-item>
          </v-list>
        </v-menu>
        <v-menu
          v-if="isLoggedIn"
          :offset="3"
        >
          <template #activator="{ props }">
            <v-btn
              class="bar-button ma-0 mr-2"
              v-bind="props"
              variant="text"
            >
              <v-icon
                size="x-large"
              >
                mdi-account
              </v-icon>
            </v-btn>
          </template>
          <v-list>
            <v-list-item to="/account">
              Account
            </v-list-item>
            <v-list-item :to="{ name: 'editAddress', params: { id: login.addressId } }">
              Address
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
        class="pa-2"
        nav
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
          <v-list-item to="/blogs">
            Newsletters
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
          <v-list-item to="/esports/geoguessr">
            Geoguessr
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
          <v-list-item to="/partners/marketing-maatwerk">
            Marketing Maatwerk
          </v-list-item>
          <v-divider dark />
        </v-list-group>

        <v-list-item to="/contact">
          Contact
        </v-list-item>
      </v-list>

      <template #append>
        <v-btn
          href="mailto:board@blueshell.utwente.nl"
          icon="mdi-email"
          style="width: calc(100%/3)"
          variant="plain"
        />
        <v-btn
          href="https://www.instagram.com/esablueshell/"
          icon="mdi-instagram"
          style="width: calc(100%/3)"
          target="_blank"
          variant="plain"
        />
        <v-btn
          href="https://www.facebook.com/BlueshellEsports/"
          icon="mdi-facebook"
          style="width: calc(100%/3)"
          target="_blank"
          variant="plain"
        />
        <v-btn
          href="https://www.twitch.tv/blueshellesports"
          icon="mdi-twitch"
          style="width: calc(100%/3)"
          target="_blank"
          variant="plain"
        />
        <v-btn
          href="https://twitter.com/BlueshellESA"
          icon="mdi-twitter"
          style="width: calc(100%/3)"
          target="_blank"
          variant="plain"
        />
        <v-btn
          href="https://www.linkedin.com/company/blueshell-esports"
          icon="mdi-linkedin"
          style="width: calc(100%/3)"
          target="_blank"
          variant="plain"
        />
      </template>
    </v-navigation-drawer>


    <router-view />

    <footer-banner />


    <v-snackbar
      v-model="poggers"
      rounded
      timeout="105000"
    >
      <audio
        v-if="poggers"
        autoplay
        controls
      >
        <source
          src="@/assets/blueshellanthem.mp3"
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
        :href="activeCookiePolicyUrl"
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

<script lang="ts" setup>
import {computed, onMounted, ref} from "vue"
import {useStore} from "vuex"
import {useRoute} from "vue-router"
import {useDisplay, useTheme} from "vuetify"
import FooterBanner from "@/components/common/banners/FooterBanner.vue"
import {$goto} from "@/plugins/goto"
import {$handleNetworkError} from "@/plugins/handleNetworkError"
import {useCookiePolicyConsent} from "@/composables/useCookiePolicyConsent"
import DOMPurify from "dompurify"
import {findUserById, type LoginResponse, type UserDetailResponse} from "@/services/api"

// Reactive state
const drawer = ref<boolean>(false)
const poggers = ref<boolean>(false)
const {
  activeCookiePolicyUrl,
  showCookieSnackbar,
  refreshCookieConsentPrompt,
  acceptCookies,
} = useCookiePolicyConsent()

// Composables
const store = useStore()
const route = useRoute()
const theme = useTheme()
const display = useDisplay()

// Computed properties
const statusSnackbarMessage = computed({
  get: (): string => store.state.statusSnackbarMessage,
  set: (message: string) => store.commit("setStatusSnackbarMessage", message),
})

const isLoggedIn = computed((): boolean => store.getters.isLoggedIn)
const isBoard = computed((): boolean => store.getters.isBoard)
const isAdmin = computed((): boolean => store.getters.isAdmin)
const login = computed(() => store.getters.getLogin)

const isDarkMode = computed((): boolean => theme.global.current.value.dark)

const resolveApiBaseUrl = (): string => {
  if (import.meta.env.VITE_APP_URL) return import.meta.env.VITE_APP_URL
  return `${globalThis.location.origin}/api`
}

// Methods
const checkPrefersColorScheme = (): void => {
  if (globalThis.matchMedia("(prefers-color-scheme: dark)").matches) {
    setDarkMode(true)
  } else if (globalThis.matchMedia("(prefers-color-scheme: light)").matches) {
    setDarkMode(false)
  }
}

const setDarkMode = (dark: boolean): void => {
  localStorage.setItem("esa-blueshell.nl:darkMode", dark.toString())
  theme.change(dark ? "dark" : "light")
}

const toggleDarkMode = (): void => {
  setDarkMode(!theme.global.current.value.dark)
}

const logOut = async (): Promise<void> => {
  try {
    await fetch(`${resolveApiBaseUrl()}/auth/logout`, {
      method: "POST",
      credentials: "include",
    })
  } catch {
    // Ignore network failures and still clear local auth state.
  }

  store.commit("logout")
  if (route.meta.requiresAuth) {
    $goto("/")
  } else {
    globalThis.location.reload()
  }
}

// Lifecycle
onMounted(async () => {
  refreshCookieConsentPrompt()

  const loginData: LoginResponse = login.value
  if (loginData) {
    try {
      const resp = await findUserById({
        path: {
          userId: loginData.userId,
        },
        throwOnError: true,
      })

      const userData: UserDetailResponse = resp.data!
      store.commit("setRoles", userData.roles)
    } catch (e: unknown) {
      $handleNetworkError(e)
    }
  }

  const keysPressed: string[] = []
  globalThis.addEventListener("keydown", (event: KeyboardEvent) => {
    if (event.key) {
      const key = event.key.toLowerCase()
      keysPressed.push(key)
      if (keysPressed.toString().endsWith("arrowup,arrowup,arrowdown,arrowdown,arrowleft,arrowright,arrowleft,arrowright,b,a,enter")) {
        poggers.value = true
        alert("BIG SITECIE ENERGY")
      }
    }
  })

  if (!localStorage.getItem("esa-blueshell.nl:darkMode")) {
    checkPrefersColorScheme()
  }

  globalThis.matchMedia("(prefers-color-scheme: dark)").addEventListener("change", () => checkPrefersColorScheme())

  const savedTheme = localStorage.getItem("esa-blueshell.nl:darkMode")
  theme.change(savedTheme === "true" ? "dark" : "light")
})
</script>

<style lang="scss" scoped>
@use '@/styles/colors' as colors;

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

.pencil-avatar {
  background: rgb(var(--v-theme-surface));
}

.pencil-icon {
  color: white;
}
</style>
