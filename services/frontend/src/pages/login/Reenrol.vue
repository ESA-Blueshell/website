<template>
  <v-main>
    <top-banner title="Sign in again" />
    <div class="mx-3">
      <v-form
        class="mx-auto mt-10"
        data-testid="reenrol-form"
        style="max-width: 500px"
        @submit.prevent="submit"
      >
        <p class="mb-4">
          An admin reset your two-factor authentication. Sign in with your username and password to set it up
          again.
        </p>
        <v-alert
          v-if="refusal"
          class="mb-4"
          type="warning"
          variant="tonal"
        >
          {{ refusal }}
        </v-alert>
        <v-text-field
          v-model="username"
          data-testid="reenrol-username-field"
          autocomplete="username"
          label="Username"
        />
        <v-text-field
          v-model="password"
          data-testid="reenrol-password-field"
          autocomplete="current-password"
          label="Password"
          type="password"
        />
        <v-btn
          :disabled="!username || !password || !token"
          :loading="loading"
          color="primary"
          data-testid="reenrol-submit-btn"
          type="submit"
        >
          Sign in
        </v-btn>
      </v-form>
    </div>
  </v-main>
</template>

<script lang="ts" setup>
import {onMounted, ref} from "vue"
import {useRoute, useRouter} from "vue-router"
import {useStore} from "vuex"
import TopBanner from "@/components/common/banners/TopBanner.vue"
import {reenrol} from "@/domains/auth"
import {$handleNetworkError} from "@/plugins/handleNetworkError"
import {clearStoredRecoveryToken, loadRecoveryTokenFromRoute} from "@/plugins/recoveryToken"
import type {TypedStore} from "@/plugins/store"

const TOKEN_KEY = "recovery:two-factor-reenrolment:token"

const route = useRoute()
const router = useRouter()
const store = useStore() as TypedStore

const token = ref("")
const username = ref("")
const password = ref("")
const loading = ref(false)
const refusal = ref<string | null>(null)

onMounted(() => {
  token.value = loadRecoveryTokenFromRoute(route, router, TOKEN_KEY)
  if (!token.value) refusal.value = "This page needs the link from your email."
})

const submit = async () => {
  loading.value = true
  refusal.value = null
  const result = await reenrol(token.value, username.value, password.value)
  loading.value = false
  if (result.outcome === "signed-in") {
    clearStoredRecoveryToken(TOKEN_KEY)
    store.commit("setLogin", result.login)
    await router.replace({path: "/account/security", query: {setUp: "1"}})
  } else if (result.outcome === "refused") {
    refusal.value = result.reason
  } else {
    $handleNetworkError(result.cause)
  }
}
</script>
