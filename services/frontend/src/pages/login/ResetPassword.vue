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
        <Form
          ref="formRef"
          v-slot="{ meta }"
          as="form"
          data-testid="reset-password-form"
          @submit="onSubmit"
        >
          <VvField
            v-model="form.password"
            :component-props="{
              autocomplete: 'new-password',
              label: 'New Password',
              'data-testid': 'reset-password-new-password-field',
              ...passwordFieldProps
            }"
            name="password"
            rules="required|minChars:8|maxChars:100|hasLower|hasUpper|hasNumber|hasSpecial"
          />

          
          <VvField
            v-model="passwordAgain"
            :component-props="{
              autocomplete: 'new-password',
              label: 'Repeat New Password',
              'data-testid': 'reset-password-repeat-password-field',
              ...passwordFieldProps
            }"
            name="passwordAgain"
            rules="required|match:@password"
          />

          <div class="form-save">
            <cut-button
              tone="solid"
              submit
              :disabled="!meta.valid || loading"
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
        </Form>
      </div>
    </div>
  </v-main>
</template>

<script lang="ts" setup>
import CutButton from "@/components/island/CutButton.vue"
import NoticeBox from "@/components/island/NoticeBox.vue"
import {onMounted, ref} from "vue"
import {useRoute, useRouter} from "vue-router"
import {Form} from "vee-validate"
import TopBanner from "@/components/common/banners/TopBanner.vue"
import VvField from "@/components/form/fields/VvField.vue"
import {type PasswordResetRequest, setNewPassword} from "@/domains/recovery"
import {clearStoredRecoveryToken, loadRecoveryTokenFromRoute} from "@/plugins/recoveryToken"
import {handleSubmitError, usePasswordToggle, useVeeForm} from "@/composables/formUtils"

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

const {formRef} = useVeeForm()
const {passwordFieldProps} = usePasswordToggle()

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
  loading.value = true
  errorMessage.value = null

  try {
    await setNewPassword(form.value)
    clearStoredRecoveryToken(RECOVERY_TOKEN_STORAGE_KEY)
    succeeded.value = true
  } catch (e: unknown) {
    if (!handleSubmitError(formRef.value, e)) {
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
