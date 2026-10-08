<template>
  <v-main>
    <top-banner title="Reset Password" />

    <div
      class="mx-auto my-10"
      style="max-width: 600px"
    >
      <div
        class="island-panel"
      >
        <form
          data-testid="reset-password-form"
          @submit.prevent="onSubmit"
        >
          <form-control
            v-model="form.password"
            autocomplete="new-password"
            data-testid="reset-password-new-password-field"
            :error-messages="errorsOf('password')"
            kind="password"
            label="New Password"
            @blur="touch('password')"
          />
          <form-control
            v-model="passwordAgain"
            autocomplete="new-password"
            data-testid="reset-password-repeat-password-field"
            :error-messages="errorsOf('passwordAgain')"
            kind="password"
            label="Repeat New Password"
            @blur="touch('passwordAgain')"
          />

          <div class="form-save">
            <cut-button
              tone="solid"
              submit
              :disabled="loading"
              data-testid="reset-password-submit-btn"
            >
              Reset Password
            </cut-button>
          </div>

          <notice-box
            v-if="errorMessage"
            tone="danger"
            testid="reset-password-error-alert"
          >
            {{ errorMessage }}
          </notice-box>

          <div
            v-if="succeeded"
            class="mt-6"
            data-testid="reset-password-success-state"
          >
            <p class="text-subtitle-1">
              Your password has been reset successfully.
              <RouterLink
                :to="{ name: 'login' }"
                class="text-decoration-none"
              >
                Sign in
              </RouterLink>
            </p>
          </div>
        </form>
      </div>
    </div>
  </v-main>
</template>

<script lang="ts" setup>
import CutButton from "@/components/island/CutButton.vue"
import NoticeBox from "@/components/island/NoticeBox.vue"
import {onMounted, ref} from "vue"
import {useRoute, useRouter} from "vue-router"
import TopBanner from "@/components/common/banners/TopBanner.vue"
import FormControl from "@/components/island/FormControl.vue"
import {type PasswordResetRequest, setNewPassword} from "@/domains/recovery"
import {clearStoredRecoveryToken, loadRecoveryTokenFromRoute} from "@/plugins/recoveryToken"
import {reportRefusal, useFormChecks} from "@/composables/useFormChecks"
import {matches, maxChars, minChars, required, strongPassword} from "@/utils/checks"

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const succeeded = ref(false)
const errorMessage = ref<string | null>(null)

const passwordAgain = ref<string>("")
const RECOVERY_TOKEN_STORAGE_KEY = "recovery:password-reset:token"

const form = ref<PasswordResetRequest>({
  password: "",
  token: "",
})

const checks = useFormChecks(() => ({
  password: {value: () => form.value.password, checks: [required, minChars(8), maxChars(100), strongPassword]},
  passwordAgain: {value: () => passwordAgain.value, checks: [required, matches(() => form.value.password)]},
}))
const {errorsOf, touch, attempt} = checks

onMounted(() => {
  const resolvedToken = loadRecoveryTokenFromRoute(route, router, RECOVERY_TOKEN_STORAGE_KEY)
  form.value.token = resolvedToken

  if (!resolvedToken) {
    clearStoredRecoveryToken(RECOVERY_TOKEN_STORAGE_KEY)
    router.replace({name: "home"})
    return
  }
})

async function onSubmit() {
  if (!attempt()) return
  loading.value = true
  errorMessage.value = null

  try {
    await setNewPassword(form.value)
    clearStoredRecoveryToken(RECOVERY_TOKEN_STORAGE_KEY)
    succeeded.value = true
  } catch (e: unknown) {
    if (!reportRefusal(checks, e)) {
      errorMessage.value = "We couldn't reset your password. The link may be invalid or expired."
    }
  } finally {
    loading.value = false
  }
}
</script>

<style lang="scss" scoped>
.v-card {
  border-radius: 12px;
}
</style>
