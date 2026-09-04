<template>
  <v-app>
    <!-- `route.meta.bare` (set on /login, /unauthorized, etc., see
         router.ts) drops the full site chrome. Useful when this SPA
         is loaded into a small popup window (Vault's OIDC flow being
         the canonical case) where the top bar / drawer / footer are
         purely visual noise around the login form. -->
    <site-bar
      v-if="!isBareLayout"
      :dark-mode="isDarkMode"
      @log-out="logOut"
      @toggle-dark-mode="toggleDarkMode"
    />


    <router-view />

    <footer-banner v-if="!isBareLayout" />



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
          v-if="statusSnackbarAction"
          color="primary"
          variant="text"
          @click="handleSnackbarAction(statusSnackbarAction)"
        >
          {{ statusSnackbarAction.label }}
        </v-btn>
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
import {computed, onMounted, ref, watch} from "vue"
import {useStore} from "vuex"
import {useRoute, useRouter} from "vue-router"
import type {SnackbarAction} from "@/plugins/store"
import {useTheme} from "vuetify"
import FooterBanner from "@/components/common/banners/FooterBanner.vue"
import SiteBar from "@/components/common/SiteBar.vue"
import {$goto} from "@/plugins/goto"
import {$handleNetworkError} from "@/plugins/handleNetworkError"
import {initialThemeName, markDocumentTheme, THEME_STORAGE_KEY} from "@/plugins/theme"
import {useCookiePolicyConsent} from "@/composables/useCookiePolicyConsent"
import DOMPurify from "dompurify"
import {apiUrl, findUserById, type LoginResponse, type UserDetailResponse} from "@/services/api"

const poggers = ref<boolean>(false)
const {
  activeCookiePolicyUrl,
  showCookieSnackbar,
  refreshCookieConsentPrompt,
  acceptCookies,
} = useCookiePolicyConsent()

const store = useStore()
const route = useRoute()
const router = useRouter()
const theme = useTheme()

const statusSnackbarMessage = computed({
  get: (): string => store.state.statusSnackbarMessage,
  set: (message: string) => store.commit("setStatusSnackbarMessage", message),
})

const statusSnackbarAction = computed((): SnackbarAction | null => store.state.statusSnackbarAction)

// Bare layout is for the SSO redirect chain only: a route that surfaces solely inside an OIDC
// popup, or a `redirect` at the api's authorize endpoint, meaning the Authorization Server
// bounced an unauthenticated hit through /login. Regular logged-out browsing keeps the chrome.
const isBareLayout = computed((): boolean => {
  if (route.meta.bare === true) return true
  const redirect = route.query?.redirect
  return typeof redirect === "string" && redirect.includes("/oauth2/authorize")
})

// The action button clears this on click, but a direct hop to /login must drop the stale toast too.
watch(
  () => route.path,
  (path) => {
    if (path === "/login") {
      store.commit("clearStatusSnackbar")
    }
  },
)

async function handleSnackbarAction(action: SnackbarAction): Promise<void> {
  store.commit("clearStatusSnackbar")
  await router.push(action.to)
}

const login = computed(() => store.getters.getLogin)

const isDarkMode = computed((): boolean => theme.global.current.value.dark)

// The island's stylesheet reads the theme off the document element: see plugins/theme.
watch(isDarkMode, dark => markDocumentTheme(dark ? "dark" : "light"))

/** An OS change reaches a visitor who has never chosen; a choice of theirs outlives it. */
const followOsTheme = (): void => {
  if (localStorage.getItem(THEME_STORAGE_KEY) !== null) return
  theme.change(initialThemeName(null, globalThis.matchMedia("(prefers-color-scheme: dark)").matches))
}

const setDarkMode = (dark: boolean): void => {
  localStorage.setItem(THEME_STORAGE_KEY, dark.toString())
  theme.change(dark ? "dark" : "light")
}

const toggleDarkMode = (): void => {
  setDarkMode(!theme.global.current.value.dark)
}

const logOut = async (): Promise<void> => {
  try {
    await fetch(apiUrl("/auth/logout"), {
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

  // The theme itself is settled before Vuetify is created, in plugins/theme.
  globalThis.matchMedia("(prefers-color-scheme: dark)").addEventListener("change", followOsTheme)
})
</script>

<style lang="scss" scoped>
@use '@/styles/colors' as colors;

.v-footer > .v-btn {
  margin: 0 2px;
}

.pencil-avatar {
  background: rgb(var(--v-theme-surface));
}

.pencil-icon {
  color: white;
}
</style>
