<template>
  <account-frame
    heading="Confirm your email address"
    island-content
    :tabs="false"
  >
    <task-layout data-testid="confirm-email">
      <loading-spinner v-if="loading" />
      <p
        v-else-if="confirmed"
        data-testid="confirm-email-done"
      >
        Your account now uses this address.
      </p>
      <p
        v-else
        data-testid="confirm-email-refused"
      >
        {{ reason }}
      </p>
    </task-layout>
  </account-frame>
</template>

<script lang="ts" setup>
import AccountFrame from "@/components/common/AccountFrame.vue"
import TaskLayout from "@/components/island/TaskLayout.vue"
import {onMounted, ref} from "vue"
import {useRoute, useRouter} from "vue-router"
import LoadingSpinner from "@/components/island/LoadingSpinner.vue"
import {confirmNewEmail} from "@/domains/auth"
import {loadRecoveryTokenFromRoute} from "@/plugins/recoveryToken"

const route = useRoute()
const router = useRouter()

const loading = ref(true)
const confirmed = ref(false)
const reason = ref("That link does not work any more.")

onMounted(async () => {
  const token = loadRecoveryTokenFromRoute(route, router, "recovery:email-change:token")
  if (token) {
    const result = await confirmNewEmail(token)
    confirmed.value = result.ok
    if (!result.ok) reason.value = result.reason
  }
  loading.value = false
})
</script>
