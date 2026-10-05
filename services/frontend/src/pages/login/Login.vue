<template>
  <v-main>
    <top-banner :title="stepUpMode ? 'Confirm it is you' : 'Login'" />

    <div class="login">
      <notice-box
        v-if="refusal"
        testid="login-refusal"
        tone="warning"
      >
        <p>{{ refusal }}</p>
      </notice-box>

      <form
        v-if="step === 'code'"
        class="island-form"
        data-testid="login-code-form"
        @submit.prevent="submitCode"
      >
        <p>
          {{ useBackupCode
            ? "Enter one of your backup codes. Each works once."
            : "Enter the six-digit code from your authenticator app." }}
        </p>
        <form-control
          v-model="code"
          :autocomplete="useBackupCode ? 'off' : 'one-time-code'"
          autofocus
          data-testid="login-code-field"
          :inputmode="useBackupCode ? 'text' : 'numeric'"
          :label="useBackupCode ? 'Backup code' : 'Code'"
        />
        <check-box
          v-if="!stepUpMode"
          v-model="trustThisBrowser"
          label="Trust this browser for 30 days"
          testid="login-trust-browser"
        />
        <div class="login__aside">
          <cut-button
            small
            testid="login-use-backup-code-btn"
            tone="quiet"
            @click="useBackupCode = !useBackupCode"
          >
            {{ useBackupCode ? "Use the authenticator app" : "Use a backup code" }}
          </cut-button>
        </div>
        <div class="form-save panel-acts--split">
          <cut-button
            v-if="!stepUpMode"
            testid="login-code-back-btn"
            @click="backToPassword"
          >
            Back
          </cut-button>
          <cut-button
            :disabled="!code.trim() || loading"
            submit
            testid="login-code-submit-btn"
            tone="solid"
          >
            {{ loading ? "Verifying" : "Verify" }}
          </cut-button>
        </div>
      </form>

      <form
        v-else
        class="island-form"
        data-testid="login-form"
        @submit.prevent="login"
      >
        <form-control
          v-model="username"
          autocomplete="username"
          data-testid="login-username-field"
          label="Username"
        />
        <form-control
          v-model="password"
          autocomplete="current-password"
          data-testid="login-password-field"
          kind="password"
          label="Password"
        />
        <div class="login__aside">
          <!--
            An account still waiting on its confirmation link cannot be told apart from a
            wrong password here, on purpose. Offering the way out beside the other one is
            what keeps that from being a dead end.
          -->
          <cut-button
            :href="`/login/confirm?username=${username}`"
            small
            testid="login-resend-confirmation-btn"
            tone="quiet"
          >
            Didn't get your confirmation mail?
          </cut-button>
          <cut-button
            :href="`/login/forgor?username=${username}`"
            small
            testid="login-forgot-password-btn"
            tone="quiet"
          >
            Forgot password?
          </cut-button>
        </div>
        <div class="form-save panel-acts--split">
          <cut-button
            href="/account/create"
            testid="login-create-account-btn"
          >
            Create account
          </cut-button>
          <cut-button
            :disabled="!valid || loading"
            submit
            testid="login-submit-btn"
            tone="solid"
          >
            {{ loading ? "Signing in" : "Login" }}
          </cut-button>
        </div>
      </form>
    </div>
  </v-main>
</template>

<script lang="ts" setup>
import {computed, onMounted, ref} from "vue"
import {useRoute, useRouter} from "vue-router"
import {useStore} from "vuex"
import TopBanner from "@/components/common/banners/TopBanner.vue"
import {$handleNetworkError} from "@/plugins/handleNetworkError.js"
import {answerChallenge, type LoginResponse, signIn, stepUp} from "@/domains/auth"
import {resolveLoginRedirect} from "@/utils/loginRedirect"
import type {State} from "@/plugins/store"
import CheckBox from "@/components/island/CheckBox.vue"
import CutButton from "@/components/island/CutButton.vue"
import FormControl from "@/components/island/FormControl.vue"
import NoticeBox from "@/components/island/NoticeBox.vue"

const router = useRouter()
const route = useRoute()
const store = useStore<State>()

const username = ref<string>("")
const password = ref<string>("")
const loading = ref<boolean>(false)
const step = ref<"password" | "code">("password")
const code = ref<string>("")
const useBackupCode = ref<boolean>(false)
const trustThisBrowser = ref<boolean>(false)
const refusal = ref<string | null>(null)

const stepUpMode = computed(() => route.query.stepUp === "1" && store.getters.isLoggedIn)

/* Both are needed before anything is sent; the api says whether they are right. */
const valid = computed(() => username.value.trim() !== "" && password.value !== "")

/**
 * A reader who is already signed in has no form to fill, so the page steps out of their way —
 * unless they were sent here by a refusal. A `redirect` means something they asked for came back
 * 401, and bouncing them to the account page would take away the one form that repairs it.
 */
onMounted(() => {
  if (stepUpMode.value) {
    step.value = "code"
    return
  }
  if (route.query.redirect) return
  if (store.getters.isLoggedIn) {
    router.replace("/account")
  }
})

const redirectOnward = async (login?: LoginResponse) => {
  // Targets outside the SPA need a full browser navigation — Vue Router's `push` only handles SPA
  // routes. This is the path Vault's OIDC flow takes: after login the browser goes back to the
  // authorize URL so Spring can emit the code.
  const {target, offSpa} = resolveLoginRedirect(route.query.redirect?.toString(), globalThis.location.origin)
  if (offSpa) {
    globalThis.location.assign(target)
    return
  }
  // Asked once, straight after signing in, and only of somebody it is a choice for.
  if (login?.twoFactor?.offered) {
    await router.replace({path: "/account/two-factor", query: {redirect: target}})
    return
  }
  // Replace, so the login page the reader was bounced through leaves no entry behind them.
  await router.replace(target)
}

const signedIn = async (login: LoginResponse) => {
  store.commit("setLogin", login)
  await redirectOnward(login)
}

const login = async () => {
  if (!valid.value || loading.value) return
  loading.value = true
  refusal.value = null
  const result = await signIn(username.value, password.value)
  loading.value = false

  if (result.outcome === "signed-in") {
    await signedIn(result.login)
  } else if (result.outcome === "two-factor") {
    step.value = "code"
  } else if (result.outcome === "rejected") {
    store.commit("setStatusSnackbarMessage", "Incorrect login credentials. Please double check your username and password.")
  } else if (result.outcome === "refused") {
    refusal.value = result.reason
  } else {
    $handleNetworkError(result.cause)
  }
}

const submitCode = async () => {
  loading.value = true
  refusal.value = null
  if (stepUpMode.value) {
    const proved = await stepUp({code: code.value.trim()})
    loading.value = false
    if (proved.ok) await redirectOnward()
    else refusal.value = proved.reason
    return
  }
  const result = await answerChallenge(code.value.trim(), trustThisBrowser.value)
  loading.value = false
  if (result.outcome === "signed-in") {
    await signedIn(result.login)
  } else if (result.outcome === "refused") {
    refusal.value = result.reason
    code.value = ""
    if (result.code === "ChallengeExpired") backToPassword()
  } else {
    $handleNetworkError(result.cause)
  }
}

const backToPassword = () => {
  step.value = "password"
  code.value = ""
  password.value = ""
}
</script>

<style scoped>
.login {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  max-width: 500px;
  margin: 2.5rem auto;
  padding-inline: 1rem;
}

.login__aside {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 0.4rem;
}
</style>
