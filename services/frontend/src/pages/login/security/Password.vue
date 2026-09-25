<template>
  <account-frame
    :crumb="SECURITY"
    eyebrow="Security"
    heading="Password"
    island-content
  >
    <task-layout
      aside-title="Forgot it?"
      data-testid="security-password"
    >
      <form
        class="security-task__form"
        @submit.prevent="withStepUp(change)"
      >
        <form-field
          v-slot="field"
          label="Current password"
          testid="security-current-password-field"
        >
          <text-input
            v-model="currentPassword"
            autocomplete="current-password"
            :control-id="field.controlId"
            type="password"
          />
        </form-field>
        <form-field
          v-slot="field"
          label="New password"
          testid="security-new-password-field"
        >
          <text-input
            v-model="newPassword"
            autocomplete="new-password"
            :control-id="field.controlId"
            type="password"
          />
        </form-field>
        <div class="security-task__acts">
          <cut-button
            :disabled="!currentPassword || !newPassword"
            submit
            testid="security-change-password-btn"
            tone="solid"
          >
            Change password
          </cut-button>
          <span class="security-task__note">Every other sign-in ends.</span>
        </div>
      </form>

      <template #aside>
        <p>
          Sign out and use Forgot password on the login page.<template v-if="address">
            The link goes to {{ address }}.
          </template>
        </p>
      </template>
    </task-layout>

    <step-up-dialog
      v-model="stepUpOpen"
      :two-factor-on="twoFactorOn"
      @proved="stepUpProved"
    />
  </account-frame>
</template>

<script lang="ts" setup>
import {onMounted, ref} from "vue"
import {useStore} from "vuex"
import AccountFrame from "@/components/common/AccountFrame.vue"
import CutButton from "@/components/island/CutButton.vue"
import FormField from "@/components/island/FormField.vue"
import TaskLayout from "@/components/island/TaskLayout.vue"
import TextInput from "@/components/island/TextInput.vue"
import {readEmailAddress, readTwoFactor, savePassword, StepUpDialog, useStepUp, type Written} from "@/domains/auth"
import type {TypedStore} from "@/plugins/store"

const SECURITY = {label: "Security", to: "/account/security"}

const store = useStore() as TypedStore
const tell = (message: string) => store.commit("setStatusSnackbarMessage", message)
const {open: stepUpOpen, attempt: withStepUp, proved: stepUpProved} = useStepUp(tell)

const currentPassword = ref("")
const newPassword = ref("")
const twoFactorOn = ref(false)
const address = ref("")

const change = async (): Promise<Written<unknown>> => {
  const result = await savePassword(currentPassword.value, newPassword.value)
  if (result.ok) {
    currentPassword.value = ""
    newPassword.value = ""
    tell("Your password is changed. Every other sign-in has ended.")
  }
  return result
}

onMounted(async () => {
  const [standing, email] = await Promise.all([readTwoFactor(), readEmailAddress()])
  twoFactorOn.value = standing?.on === true
  address.value = email?.email ?? ""
})
</script>

<style scoped>
.security-task__form {
  display: flex;
  flex-direction: column;
  gap: 1.1rem;
}

.security-task__acts {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.6rem 1rem;
  padding-top: 0.4rem;
}

.security-task__note {
  font-size: 0.85rem;
  color: var(--color-ash);
}
</style>
