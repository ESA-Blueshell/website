<template>
  <v-main>
    <top-banner title="Activate Member Account" />

    <div
      class="mx-auto my-10"
      style="max-width: 600px"
    >
      <v-card class="pa-6">
        <Form
          ref="formRef"
          v-slot="{ meta }"
          as="form"
          data-testid="activate-member-form"
          @submit="onSubmit"
        >
          <v-row>
            <v-col cols="12">
              <VvField
                v-model="form.username"
                :component-props="{ label: 'Username', autocomplete: 'username', 'data-testid': 'activate-member-username-field' }"
                name="username"
                rules="required|alphaNum"
              />
            </v-col>
          </v-row>
          <v-row>
            <v-col cols="12">
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
            </v-col>
          </v-row>

          <v-row>
            <v-col cols="12">
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
            </v-col>
          </v-row>

          <v-row
            align="center"
            class="mt-2"
            justify="end"
          >
            <v-btn
              :disabled="!meta.valid || loading"
              :loading="loading"
              color="primary"
              data-testid="activate-member-submit-btn"
              type="submit"
            >
              Activate Member
            </v-btn>
          </v-row>

          <v-alert
            v-if="errorMessage"
            class="mt-4"
            data-testid="activate-member-error-alert"
            type="error"
            variant="tonal"
          >
            {{ errorMessage }}
          </v-alert>

          <v-alert
            v-if="succeeded"
            class="mb-2"
            data-testid="activate-member-success-alert"
            type="success"
          >
            Account activated! You will be redirected to the login page.
          </v-alert>
        </Form>
      </v-card>
    </div>
  </v-main>
</template>

<script lang="ts" setup>
import {onMounted, ref} from "vue"
import {useRoute, useRouter} from "vue-router"
import {Form} from "vee-validate"
import TopBanner from "@/components/common/banners/TopBanner.vue"
import VvField from "@/components/form/fields/VvField.vue"
import {memberActivate, type MemberActivationRequest} from "@/services/api"
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
    await memberActivate({body: form.value, throwOnError: true})
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
