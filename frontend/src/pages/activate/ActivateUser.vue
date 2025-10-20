<template>
  <div
    class="d-flex align-center justify-center"
    style="min-height: 100vh;"
  >
    <div
      class="mx-auto text-center"
      style="max-width: 520px; width: 100%; padding: 16px;"
    >
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
        <p class="text-subtitle-1">
          Account activated! You will be redirected to the login page.
        </p>
      </div>

      <v-alert
        v-else
        type="warning"
      >
        <div>{{ errorMessage || defaultErrorMessage }}</div>
        <div class="mt-2">
          You will be redirected to the login page.
        </div>
      </v-alert>
    </div>
  </div>
</template>

<script lang="ts" setup>
import {onMounted, ref} from "vue"
import {useRoute, useRouter} from "vue-router"
import {userActivate, type UserActivationRequest} from "@/services/api"
import {$handleNetworkError} from "@/plugins/handleNetworkError.ts"

const route = useRoute()
const router = useRouter()

const loading = ref(true)
const succeeded = ref(false)
const errorMessage = ref<string | null>(null)

function redirectToLogin(ms = 2000) {
  window.setTimeout(() => router.push({name: "login"}), ms)
}

onMounted(async () => {
  const token = (route.query.token as string) || ""
  const username = (route.query.username as string) || ""

  if (!token || !username) {
    loading.value = false
    errorMessage.value = "We couldn’t verify your activation link. It may be invalid, expired, or already used."
    redirectToLogin(2500)
    return
  }

  const payload: UserActivationRequest = {token, username}

  try {
    await userActivate({body: payload, throwOnError: true})
    succeeded.value = true
    redirectToLogin(1500)
  } catch (e: unknown) {
    $handleNetworkError(e)
    errorMessage.value = "We couldn’t verify your activation link. It may be invalid, expired, or already used."
    redirectToLogin(2500)
  } finally {
    loading.value = false
  }
})
</script>
