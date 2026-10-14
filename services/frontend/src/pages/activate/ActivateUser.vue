<template>
  <account-frame
    heading="Account activation"
    island-content
    :tabs="false"
  >
    <task-layout>
      <div
        v-if="loading"
        class="site-waiting activate__waiting"
        data-testid="activate-user-loading-state"
      >
        <loading-spinner />
        <p>
          Please wait a moment, we’re activating your account.
        </p>
      </div>

      <div
        v-else-if="succeeded"
        class="site-waiting activate__waiting"
        data-testid="activate-user-success-state"
      >
        <done-tick />
        <p>
          {{ !membershipStarted
            ? "Account confirmed! You will be redirected to the login page."
            : collectedByIncasso
              ? "Account confirmed and your membership has started. We collect your first contribution by incasso and email you at least 14 days before. You will be redirected to the login page."
              : "Account confirmed and your membership has started. You will be redirected to the login page." }}
        </p>
      </div>

      <!-- Error -->
      <notice-box
        v-else
        tone="warning"
        testid="activate-user-error-alert"
      >
        <div>{{ errorMessage }}</div>
        <div>
          You will be redirected to the login page.
        </div>
      </notice-box>
    </task-layout>
  </account-frame>
</template>

<script lang="ts" setup>
import AccountFrame from "@/components/common/AccountFrame.vue"
import TaskLayout from "@/components/island/TaskLayout.vue"
import {onMounted, ref} from "vue"
import {useRoute, useRouter} from "vue-router"
import DoneTick from "@/components/island/DoneTick.vue"
import LoadingSpinner from "@/components/island/LoadingSpinner.vue"
import NoticeBox from "@/components/island/NoticeBox.vue"
import {activateUser} from "@/domains/recovery"
import {$handleNetworkError} from "@/plugins/handleNetworkError.ts"
import {clearStoredRecoveryToken, loadRecoveryTokenFromRoute} from "@/plugins/recoveryToken"
import {announceAccountActivation} from "@/plugins/signupContinuation"

const route = useRoute()
const router = useRouter()

const loading = ref(true)
const succeeded = ref(false)
const membershipStarted = ref(false)
const collectedByIncasso = ref(false)
const errorMessage =
  "We couldn’t verify your activation link. It may be invalid, expired, or already used."
const RECOVERY_TOKEN_STORAGE_KEY = "recovery:user-activation:token"

function redirectToLogin(ms: number) {
  window.setTimeout(() => router.push({name: "login"}), ms)
}

onMounted(async () => {
  const token = loadRecoveryTokenFromRoute(route, router, RECOVERY_TOKEN_STORAGE_KEY)

  // js/user-controlled-bypass flags this guard: nothing is authorised here, the token is only
  // ever validated by the API and this branch just renders the error state for a tokenless link.
  if (!token) {
    loading.value = false
    clearStoredRecoveryToken(RECOVERY_TOKEN_STORAGE_KEY)
    redirectToLogin(2500)
    return
  }

  try {
    const activated = await activateUser(token)
    clearStoredRecoveryToken(RECOVERY_TOKEN_STORAGE_KEY)
    succeeded.value = true
    membershipStarted.value = activated.membershipStarted
    collectedByIncasso.value = activated.collectedByIncasso
    // A signup form left open in another tab is holding a token this just spent.
    announceAccountActivation()
    window.setTimeout(() => router.push({name: "login"}), 1500)
  } catch (e: unknown) {
    $handleNetworkError(e)
    redirectToLogin(2500)
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.activate__waiting {
  justify-content: flex-start;
}
</style>
