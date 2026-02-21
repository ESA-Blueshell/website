<template>
  <v-main>
    <top-banner title="Account Activation" />

    <div
      class="mx-auto my-10"
      style="max-width: 600px"
    >
      <v-card class="pa-6 text-center">
        <div
          v-if="loading"
          class="d-flex align-center justify-center"
        >
          <v-progress-circular
            class="mr-3"
            indeterminate
            size="32"
          />
          <p class="text-subtitle-1 mb-0">
            Please wait a moment, we’re activating your account.
          </p>
        </div>

        <div v-else-if="succeeded">
          <v-icon
            class="mb-2"
            color="success"
            size="48"
          >
            mdi-check-circle
          </v-icon>
          <p class="text-subtitle-1">
            Account activated! You will be redirected to the login page.
          </p>
        </div>

        <!-- Error -->
        <v-alert
          v-else
          type="warning"
          variant="tonal"
        >
          <div>{{ errorMessage || defaultErrorMessage }}</div>
          <div class="mt-2">
            You will be redirected to the login page.
          </div>
        </v-alert>
      </v-card>
    </div>
  </v-main>
</template>

<script lang="ts" setup>
import {onMounted, ref} from "vue"
import {useRoute, useRouter} from "vue-router"
import TopBanner from "@/components/common/banners/TopBanner.vue"
import {userActivate} from "@/services/api"
import {$handleNetworkError} from "@/plugins/handleNetworkError.ts"
import {clearStoredRecoveryToken, loadRecoveryTokenFromRoute} from "@/plugins/recoveryToken"

const route = useRoute()
const router = useRouter()

const loading = ref(true)
const succeeded = ref(false)
const errorMessage = ref<string | null>(null)
const defaultErrorMessage =
  "We couldn’t verify your activation link. It may be invalid, expired, or already used."
const RECOVERY_TOKEN_STORAGE_KEY = "recovery:user-activation:token"

function redirectToLogin(ms = 2000) {
  window.setTimeout(() => router.push({name: "login"}), ms)
}

onMounted(async () => {
  const token = loadRecoveryTokenFromRoute(route, router, RECOVERY_TOKEN_STORAGE_KEY)

  if (!token) {
    loading.value = false
    clearStoredRecoveryToken(RECOVERY_TOKEN_STORAGE_KEY)
    errorMessage.value = defaultErrorMessage
    redirectToLogin(2500)
    return
  }

  try {
    const resp = await userActivate({body: {token}, throwOnError: true})
    clearStoredRecoveryToken(RECOVERY_TOKEN_STORAGE_KEY)
    succeeded.value = true
    const redirect = resp.data!.path
    window.setTimeout(
      () => router.push({name: "login", query: {redirect}}),
      1500,
    )
  } catch (e: unknown) {
    $handleNetworkError(e)
    errorMessage.value = defaultErrorMessage
    redirectToLogin(2500)
  } finally {
    loading.value = false
  }
})
</script>

<style lang="scss" scoped>
.v-card {
  border-radius: 12px;
}
</style>
