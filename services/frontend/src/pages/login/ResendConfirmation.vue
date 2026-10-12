<template>
  <account-frame
    :crumb="{label: 'Login', to: '/login'}"
    heading="Confirm your account"
    island-content
    :tabs="false"
  >
    <task-layout aside-title="Forgot your password?">
      <div
        v-if="!succeeded"
        class="island-form"
        data-testid="resend-confirmation-form-state"
      >
        <p>
          Enter your username, and we’ll email you a new link to confirm your address.
          You need it before you can sign in.
        </p>
        <form
          class="island-form"
          data-testid="resend-confirmation-form"
          @submit.prevent="onSubmit"
        >
          <form-control
            v-model="form.username"
            autocomplete="username"
            data-testid="resend-confirmation-username-field"
            :error-messages="errorsOf('username')"
            label="Username"
            @blur="touch('username')"
          />
          <div class="form-save">
            <cut-button
              tone="solid"
              submit
              :disabled="loading"
              data-testid="resend-confirmation-submit-btn"
            >
              Send confirmation mail
            </cut-button>
          </div>
        </form>
      </div>
      <div
        v-else
        data-testid="resend-confirmation-success-state"
      >
        <p>
          If an account with that username is still waiting to be confirmed, you’ll receive an
          email with a fresh link. Didn’t get it? Check your spam folder or try again later.
        </p>
      </div>

      <template #aside>
        <p>We will email you a link to reset it.</p>
        <p>
          <router-link :to="`/login/forgor?username=${form.username}`">
            Send a reset email
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
import {onMounted, ref} from "vue"
import {useRoute} from "vue-router"
import FormControl from "@/components/island/FormControl.vue"
import {useFormChecks} from "@/composables/useFormChecks"
import {required} from "@/utils/checks"
import {resendActivation} from "@/domains/recovery"
import {$handleNetworkError} from "@/plugins/handleNetworkError"

/**
 * Asking for the confirmation link again, from outside the signup form.
 *
 * The form's own last step could already do this, and it was the only place that could: an
 * applicant who closed it and came back after the link expired had no way to ask for another, and
 * login answers a confirmed-looking wrong password either way. Says the same thing whether or not
 * the account exists, like the password reset beside it, so this cannot be used to find out who has
 * an account.
 */
const route = useRoute()
const loading = ref(false)
const succeeded = ref(false)

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
  try {
    const result = await resendActivation(form.value.username)
    if (result.outcome === "rate-limited") {
      $handleNetworkError(result.cause)
      return
    }
    succeeded.value = true
  } finally {
    loading.value = false
  }
}
</script>
