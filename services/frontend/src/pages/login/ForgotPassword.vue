<template>
  <account-frame
    :crumb="{label: 'Login', to: '/login'}"
    heading="Forgot password"
    island-content
    :tabs="false"
  >
    <task-layout aside-title="Haven't confirmed your email yet?">
      <div
        v-if="!succeeded"
        class="island-form"
        data-testid="forgot-password-form-state"
      >
        <p>Enter your username, and we’ll email you a link to reset your password.</p>
        <notice-box
          v-if="failed"
          tone="warning"
          testid="forgot-password-failed-alert"
        >
          We could not send that just now, so no email is on its way. Please try again.
        </notice-box>
        <form
          class="island-form"
          data-testid="forgot-password-form"
          @submit.prevent="onSubmit"
        >
          <form-control
            v-model="form.username"
            autocomplete="username"
            data-testid="forgot-password-username-field"
            :error-messages="errorsOf('username')"
            label="Username"
            @blur="touch('username')"
          />
          <div class="form-save">
            <cut-button
              tone="solid"
              submit
              :disabled="loading"
              data-testid="forgot-password-submit-btn"
            >
              Send reset mail
            </cut-button>
          </div>
        </form>
      </div>
      <div
        v-else
        data-testid="forgot-password-success-state"
      >
        <p>
          If an account with that username exists, you’ll receive an email with a password reset link.
          Didn’t get it? Check your spam folder or try again later.
        </p>
      </div>

      <template #aside>
        <p>You need to click the link in the email we sent you before you can log in.</p>
        <p>
          Can't find that email? <router-link :to="`/login/confirm?username=${form.username}`">
            Send a new confirmation email
          </router-link>
        </p>
      </template>
    </task-layout>
  </account-frame>
</template>

<script lang="ts" setup>
import AccountFrame from "@/components/common/AccountFrame.vue"
import TaskLayout from "@/components/island/TaskLayout.vue"
import CutButton from "@/components/island/CutButton.vue"
import NoticeBox from "@/components/island/NoticeBox.vue"
import {onMounted, ref} from "vue"
import {useRoute} from "vue-router"
import FormControl from "@/components/island/FormControl.vue"
import {useFormChecks} from "@/composables/useFormChecks"
import {required} from "@/utils/checks"
import {requestPasswordReset} from "@/domains/recovery"

const route = useRoute()
const loading = ref(false)
const succeeded = ref(false)
/** The request itself did not get through, which is not the same as an unknown username. */
const failed = ref(false)

const form = ref({username: ""})
const {errorsOf, touch, attempt} = useFormChecks(() => ({
  username: {value: () => form.value.username, checks: [required]},
}))

onMounted(() => {
  const q = route.query.username
  if (typeof q === "string") form.value.username = q
})

const onSubmit = async () => {
  if (!attempt()) return
  loading.value = true
  failed.value = false
  try {
    // Deliberately vague about whether the account exists — but only about that.
    // A server that could not take the request has not sent anything.
    await requestPasswordReset(form.value.username)
    succeeded.value = true
  } catch {
    failed.value = true
  } finally {
    loading.value = false
  }
}
</script>
