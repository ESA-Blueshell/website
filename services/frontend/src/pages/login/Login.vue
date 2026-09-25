<template>
  <v-main>
    <top-banner :title="stepUpMode ? 'Confirm it is you' : 'Login'" />

    <div class="mx-3">
      <v-alert
        v-if="refusal"
        class="mx-auto mt-10"
        data-testid="login-refusal"
        style="max-width: 500px"
        type="warning"
        variant="tonal"
      >
        {{ refusal }}
      </v-alert>

      <v-form
        v-if="step === 'code'"
        ref="codeForm"
        class="mx-auto mt-10"
        data-testid="login-code-form"
        style="max-width: 500px"
        @submit.prevent="submitCode"
      >
        <p class="mb-4">
          {{ useBackupCode
            ? "Enter one of your backup codes. Each works once."
            : "Enter the six-digit code from your authenticator app." }}
        </p>
        <v-row>
          <v-text-field
            v-model="code"
            :autocomplete="useBackupCode ? 'off' : 'one-time-code'"
            :inputmode="useBackupCode ? 'text' : 'numeric'"
            :label="useBackupCode ? 'Backup code' : 'Code'"
            autofocus
            data-testid="login-code-field"
          />
        </v-row>
        <v-row v-if="!stepUpMode">
          <v-checkbox
            v-model="trustThisBrowser"
            data-testid="login-trust-browser"
            hide-details
            label="Trust this browser for 30 days"
          />
        </v-row>
        <v-row class="justify-end">
          <v-btn
            data-testid="login-use-backup-code-btn"
            size="small"
            variant="text"
            @click="useBackupCode = !useBackupCode"
          >
            {{ useBackupCode ? "use the authenticator app" : "use a backup code" }}
          </v-btn>
        </v-row>
        <v-row class="mb-3">
          <v-col cols="auto">
            <v-btn
              v-if="!stepUpMode"
              data-testid="login-code-back-btn"
              variant="outlined"
              @click="backToPassword"
            >
              Back
            </v-btn>
          </v-col>
          <v-spacer />
          <v-col cols="auto">
            <v-btn
              :disabled="!code.trim()"
              :loading="loading"
              color="primary"
              data-testid="login-code-submit-btn"
              type="submit"
            >
              Verify
            </v-btn>
          </v-col>
        </v-row>
      </v-form>

      <v-form
        v-else
        ref="form"
        v-model="valid"
        class="mx-auto mt-10"
        data-testid="login-form"
        style="max-width: 500px"
        @submit.prevent
      >
        <v-row>
          <v-text-field
            ref="usernameField"
            v-model="username"
            :input-props="{ 'data-testid': 'login-username-input' }"
            :rules="usernameRules"
            data-testid="login-username-field"
            label="Username"
            required
            @keydown.enter="login"
          />
        </v-row>
        <v-row>
          <v-text-field
            v-model="password"
            :append-inner-icon="showPass ? 'mdi-eye' : 'mdi-eye-off'"
            :input-props="{ 'data-testid': 'login-password-input' }"
            :rules="passwordRules"
            :type="showPass ? 'text' : 'password'"
            data-testid="login-password-field"
            hide-details
            label="Password"
            required
            @keydown.enter="login"
            @click:append-inner="showPass = !showPass"
          />
        </v-row>
        <v-row class="justify-end">
          <!--
            An account still waiting on its confirmation link cannot be told apart from a
            wrong password here, on purpose. Offering the way out beside the other one is
            what keeps that from being a dead end.
          -->
          <v-btn
            :to="`login/confirm?username=${username}`"
            data-testid="login-resend-confirmation-btn"
            size="small"
            variant="text"
          >
            didn't get your confirmation mail?
          </v-btn>
          <v-btn
            :to="`login/forgor?username=${username}`"
            data-testid="login-forgot-password-btn"
            size="small"
            variant="text"
          >
            forgot password?
          </v-btn>
        </v-row>
        <v-row class="mb-3">
          <v-col cols="auto">
            <v-btn
              color="accent"
              data-testid="login-create-account-btn"
              to="account/create"
              variant="outlined"
            >
              Create Account
            </v-btn>
          </v-col>
          <v-spacer />
          <v-col cols="auto">
            <v-btn
              :disabled="!valid"
              :loading="loading"
              color="primary"
              data-testid="login-submit-btn"
              @click="login"
            >
              Login
            </v-btn>
          </v-col>
        </v-row>
      </v-form>
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
import type {VForm} from "vuetify/components"

const router = useRouter()
const route = useRoute()
const store = useStore<State>()

const form = ref<VForm>()
const usernameField = ref()
const username = ref<string>("")
const password = ref<string>("")
const valid = ref<boolean>(false)
const loading = ref<boolean>(false)
const showPass = ref<boolean>(false)
const step = ref<"password" | "code">("password")
const code = ref<string>("")
const useBackupCode = ref<boolean>(false)
const trustThisBrowser = ref<boolean>(false)
const refusal = ref<string | null>(null)

const stepUpMode = computed(() => route.query.stepUp === "1" && store.getters.isLoggedIn)

const usernameRules = [
  (v: string) => !!v || "Username is required",
]

const passwordRules = [
  (v: string) => !!v || "Password is required",
]

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
  if (!form.value || !(await form.value.validate()).valid) return
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
