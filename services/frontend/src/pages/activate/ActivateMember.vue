<template>
  <account-frame
    heading="Activate member account"
    island-content
    :tabs="false"
  >
    <task-layout>
      <form
        class="island-form"
        data-testid="activate-member-form"
        @submit.prevent="onSubmit"
      >
        <form-control
          v-model="form.username"
          autocomplete="username"
          data-testid="activate-member-username-field"
          :error-messages="errorsOf('username')"
          label="Username"
          @blur="touch('username')"
        />
        <form-control
          v-model="form.password"
          autocomplete="new-password"
          data-testid="activate-member-password-field"
          :error-messages="errorsOf('password')"
          kind="password"
          label="Password"
          @blur="touch('password')"
        />
        <form-control
          v-model="passwordAgain"
          autocomplete="new-password"
          data-testid="activate-member-repeat-password-field"
          :error-messages="errorsOf('passwordAgain')"
          kind="password"
          label="Repeat Password"
          @blur="touch('passwordAgain')"
        />
        <div class="form-save">
          <cut-button
            data-testid="activate-member-submit-btn"
            :disabled="loading"
            submit
            tone="solid"
          >
            Activate Member
          </cut-button>
        </div>
      </form>
      <notice-box
        v-if="errorMessage"
        testid="activate-member-error-alert"
        tone="danger"
      >
        {{ errorMessage }}
      </notice-box>
      <notice-box
        v-if="succeeded"
        testid="activate-member-success-alert"
        tone="info"
      >
        Account activated! You will be redirected to the login page.
      </notice-box>
    </task-layout>
  </account-frame>
</template>

<script lang="ts" setup>
import AccountFrame from "@/components/common/AccountFrame.vue"
import TaskLayout from "@/components/island/TaskLayout.vue"
import {onMounted, ref} from "vue"
import {useRoute, useRouter} from "vue-router"
import CutButton from "@/components/island/CutButton.vue"
import FormControl from "@/components/island/FormControl.vue"
import NoticeBox from "@/components/island/NoticeBox.vue"
import {activateMember, type MemberActivationRequest} from "@/domains/recovery"
import {clearStoredRecoveryToken, loadRecoveryTokenFromRoute} from "@/plugins/recoveryToken"
import {announceAccountActivation} from "@/plugins/signupContinuation"
import {reportRefusal, useFormChecks} from "@/composables/useFormChecks"
import {matches, maxChars, minChars, required, strongPassword} from "@/utils/checks"

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

const checks = useFormChecks(() => ({
  username: {value: () => form.value.username, checks: [required]},
  password: {value: () => form.value.password, checks: [required, minChars(8), maxChars(100), strongPassword]},
  passwordAgain: {value: () => passwordAgain.value, checks: [required, matches(() => form.value.password)]},
}))
const {errorsOf, touch, attempt} = checks

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
  if (!attempt()) return
  loading.value = true
  errorMessage.value = null

  try {
    await activateMember(form.value)
    clearStoredRecoveryToken(RECOVERY_TOKEN_STORAGE_KEY)
    succeeded.value = true
    announceAccountActivation(form.value.username)
    redirectToLogin(2500)
  } catch (e: unknown) {
    if (!reportRefusal(checks, e)) {
      errorMessage.value = "We couldn't activate your membership. The link may be invalid or expired."
    }
  } finally {
    loading.value = false
  }
}
</script>
