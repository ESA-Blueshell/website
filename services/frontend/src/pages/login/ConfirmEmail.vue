<template>
  <main class="site-main">
    <top-banner title="Confirm your email address" />
    <div class="site-gutter">
      <div
        class="site-column"
        data-testid="confirm-email"
        style="max-width: 600px"
      >
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
      </div>
    </div>
  </main>
</template>

<script lang="ts" setup>
import {onMounted, ref} from "vue"
import {useRoute, useRouter} from "vue-router"
import TopBanner from "@/components/common/banners/TopBanner.vue"
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
