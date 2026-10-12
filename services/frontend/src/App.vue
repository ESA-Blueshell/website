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

    <backup-codes-banner />

    <footer-banner v-if="!isBareLayout" />



    <toast-area>
      <toast-notice
        v-model:open="poggers"
        testid="anthem-toast"
        :timeout="105000"
      >
        <audio
          autoplay
          controls
        >
          <source
            src="@/assets/blueshellanthem.mp3"
            type="audio/mpeg"
          >
        </audio>
      </toast-notice>

      <toast-notice
        v-model:open="snackbarOpen"
        testid="status-toast"
        :timeout="10000"
      >
        <!-- eslint-disable-next-line vue/no-v-html -->
        <span v-html="DOMPurify.sanitize(statusSnackbarMessage)" />
        <template #actions>
          <cut-button
            v-if="statusSnackbarAction"
            small
            testid="status-toast-action"
            @click="handleSnackbarAction(statusSnackbarAction)"
          >
            {{ statusSnackbarAction.label }}
          </cut-button>
          <cut-button
            small
            testid="status-toast-close"
            tone="quiet"
            @click="statusSnackbarMessage = ''"
          >
            Close
          </cut-button>
        </template>
      </toast-notice>

      <toast-notice
        :open="showCookieSnackbar"
        testid="cookie-toast"
      >
        We're using cookies to keep you logged in. You can read more about how we use cookies in our
        <a
          class="cookie-policy-link"
          :href="activeCookiePolicyUrl"
          target="_blank"
        >Cookie Policy</a>.

        <template #actions>
          <cut-button
            small
            testid="cookie-toast-accept"
            @click="acceptCookies"
          >
            Got it
          </cut-button>
        </template>
      </toast-notice>
    </toast-area>
  </v-app>
</template>

<script lang="ts" setup>
import {computed, onMounted, ref, watch} from "vue"
import {useStore} from "vuex"
import {useRoute, useRouter} from "vue-router"
import type {SnackbarAction, StoredLogin} from "@/plugins/store"
import FooterBanner from "@/components/common/banners/FooterBanner.vue"
import SiteBar from "@/components/common/SiteBar.vue"
import CutButton from "@/components/island/CutButton.vue"
import ToastArea from "@/components/island/ToastArea.vue"
import ToastNotice from "@/components/island/ToastNotice.vue"
import {$goto} from "@/plugins/goto"
import {$handleNetworkError} from "@/plugins/handleNetworkError"
import {chooseTheme, siteTheme} from "@/plugins/theme"
import {useCookiePolicyConsent} from "@/composables/useCookiePolicyConsent"
import DOMPurify from "dompurify"
import {apiUrl, findUserById, type UserDetailResponse} from "@/services/api"
import {BackupCodesBanner, readTwoFactor} from "@/domains/auth"

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

const statusSnackbarMessage = computed({
  get: (): string => store.state.statusSnackbarMessage,
  set: (message: string) => store.commit("setStatusSnackbarMessage", message),
})

// The toast closes itself on its timeout, which clears the message rather than storing `false`.
const snackbarOpen = computed({
  get: (): boolean => !!store.state.statusSnackbarMessage,
  set: (open: boolean) => {
    if (!open) store.commit("setStatusSnackbarMessage", "")
  },
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

const isDarkMode = computed((): boolean => siteTheme.value === "dark")

const toggleDarkMode = (): void => {
  chooseTheme(isDarkMode.value ? "light" : "dark")
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

  const loginData: StoredLogin | null = login.value
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
      const twoFactor = await readTwoFactor()
      if (twoFactor) store.commit("setTwoFactor", twoFactor)
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
})
</script>

<style scoped>
.cookie-policy-link {
  color: var(--color-brand-ink);
  text-decoration: none;
}
</style>
