<template>
  <v-main>
    <top-banner title="Login" />

    <div class="mx-3">
      <v-form
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
import {onMounted, ref} from "vue"
import {useRoute, useRouter} from "vue-router"
import {useStore} from "vuex"
import TopBanner from "@/components/common/banners/TopBanner.vue"
import {$handleNetworkError} from "@/plugins/handleNetworkError.js"
import {authenticate, type LoginResponse} from "@/services/api"
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

const usernameRules = [
  (v: string) => !!v || "Username is required",
]

const passwordRules = [
  (v: string) => !!v || "Password is required",
]

onMounted(() => {
  if (!store.getters.tokenExpired) {
    router.push("/account")
  }
})

const login = async () => {
  // Check if form is valid (meaning username and password are not empty)
  if (form.value && (await form.value.validate()).valid) {
    loading.value = true

    const response = await authenticate({
      body: {
        username: username.value,
        password: password.value,
      },
    })

    loading.value = false

    if (response.status === 200) {
      const loginData = response.data as LoginResponse
      store.commit("setLogin", loginData)

      // Targets outside the SPA need a full browser navigation — Vue
      // Router's `push` only handles SPA routes and would silently land on
      // `/` for everything else. This is the path Vault's OIDC flow takes:
      // the popup arrives here with `?redirect=/api/oauth2/authorize?…`,
      // after login we have to bounce the browser back to that authorize
      // URL so Spring can emit the code and Vault's callback fires.
      const {target, offSpa} = resolveLoginRedirect(
        route.query.redirect?.toString(),
        globalThis.location.origin,
      )
      if (offSpa) {
        globalThis.location.assign(target)
      } else {
        await router.push(target)
      }
    } else if (response?.status === 401) {
      store.commit("setStatusSnackbarMessage", "Incorrect login credentials. Please double check your username and password.")
    } else {
      $handleNetworkError(response)
    }
  }
}
</script>
