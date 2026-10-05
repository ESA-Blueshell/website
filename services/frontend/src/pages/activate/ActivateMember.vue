<template>
  <v-main>
    <top-banner title="Activate Member Account" />

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
          data-testid="activate-member-form"
          @submit="onSubmit"
        >
          <VvField
            v-model="form.username"
            :component-props="{ label: 'Username', autocomplete: 'username', 'data-testid': 'activate-member-username-field' }"
            name="username"
            rules="required|alphaNum"
          />

          
          <VvField
            v-model="form.password"
            :component-props="{
              label: 'Password',
              autocomplete: 'new-password',
              'data-testid': 'activate-member-password-field',
              ...passwordFieldProps
            }"
            name="password"
            rules="required|minChars:8|maxChars:100|hasLower|hasUpper|hasNumber|hasSpecial"
          />

          <VvField
            v-model="passwordAgain"
            :component-props="{
              label: 'Repeat Password',
              autocomplete: 'new-password',
              'data-testid': 'activate-member-repeat-password-field',
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
              data-testid="activate-member-submit-btn"
            >
              Activate Member
            </cut-button>
          </div>

          <notice-box
            v-if="errorMessage"
            tone="danger"
            testid="activate-member-error-alert"
          >
            {{ errorMessage }}
          </notice-box>

          <notice-box
            v-if="succeeded"
            tone="info"
            testid="activate-member-success-alert"
          >
            Account activated! You will be redirected to the login page.
          </notice-box>
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
import {activateMember, type MemberActivationRequest} from "@/domains/recovery"
import {clearStoredRecoveryToken, loadRecoveryTokenFromRoute} from "@/plugins/recoveryToken"
import {announceAccountActivation} from "@/plugins/signupContinuation"
import {handleSubmitError, usePasswordToggle, useVeeForm} from "@/composables/formUtils"

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const succeeded = ref(false)
const errorMessage = ref<string | null>(null)

const form = ref<MemberActivationRequest>({
  username: "",
  password: "",
  token: "",
})

const passwordAgain = ref("")
const RECOVERY_TOKEN_STORAGE_KEY = "recovery:member-activation:token"

const {formRef} = useVeeForm()
const {passwordFieldProps} = usePasswordToggle()

onMounted(() => {
  form.value.token = loadRecoveryTokenFromRoute(route, router, RECOVERY_TOKEN_STORAGE_KEY)

  if (!form.value.token) {
    clearStoredRecoveryToken(RECOVERY_TOKEN_STORAGE_KEY)
    router.replace({name: "home"})
  }
})

function redirectToLogin(ms = 2000) {
  window.setTimeout(() => router.push({name: "login"}), ms)
}

async function onSubmit() {
  loading.value = true
  errorMessage.value = null

  try {
    await activateMember(form.value)
    clearStoredRecoveryToken(RECOVERY_TOKEN_STORAGE_KEY)
    succeeded.value = true
    announceAccountActivation(form.value.username)
    redirectToLogin(2500)
  } catch (e: unknown) {
    if (!handleSubmitError(formRef.value, e)) {
      errorMessage.value = "We couldn't activate your membership. The link may be invalid or expired."
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
