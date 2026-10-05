<template>
  <v-main>
    <top-banner title="Sign in again" />
    <div class="mx-3">
      <form
        v-first-field
        class="island-form"
        data-testid="reenrol-form"
        @submit.prevent="submit"
      >
        <p class="mb-4">
          An admin reset your two-factor authentication. Sign in with your username and password to set it up
          again.
        </p>
        <notice-box
          v-if="refusal"
          tone="warning"
        >
          {{ refusal }}
        </notice-box>
        <form-control
          v-model="username"
          data-testid="reenrol-username-field"
          autocomplete="username"
          label="Username"
        />
        <form-control
          v-model="password"
          kind="password"
          data-testid="reenrol-password-field"
          autocomplete="current-password"
          label="Password"
        />
        <cut-button
          tone="solid"
          submit
          :disabled="!username || !password || !token || loading"
          data-testid="reenrol-submit-btn"
        >
          Sign in
        </cut-button>
      </form>
    </div>
  </v-main>
</template>

<script lang="ts" setup>
import FormControl from "@/components/island/FormControl.vue"
import CutButton from "@/components/island/CutButton.vue"
import NoticeBox from "@/components/island/NoticeBox.vue"
import {vFirstField} from "@/utils/firstField"
import {onMounted, ref} from "vue"
import {useRoute, useRouter} from "vue-router"
import {useStore} from "vuex"
import TopBanner from "@/components/common/banners/TopBanner.vue"
import {reenrol, SECURITY_PAGES} from "@/domains/auth"
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
    await router.replace(SECURITY_PAGES.setUp)
  } else if (result.outcome === "refused") {
    refusal.value = result.reason
  } else {
    $handleNetworkError(result.cause)
  }
}
</script>
