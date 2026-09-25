<template>
  <account-frame
    :crumb="SECURITY_CRUMB"
    eyebrow="Security"
    heading="Email address"
    island-content
  >
    <task-layout
      aside-title="How it moves"
      data-testid="security-email"
    >
      <p
        class="security-task__now"
        data-testid="security-email-now"
      >
        <span class="security-task__now-label">Now</span>
        <span>{{ address?.email }}</span>
      </p>
      <notice-box
        v-if="address?.pendingEmail"
        testid="security-email-pending"
        :title="`Waiting for ${address.pendingEmail}`"
      >
        <p>Follow the link we sent there within 24 hours. Until then your account keeps its old address.</p>
        <cut-button
          class="security-task__again"
          testid="security-email-resend-btn"
          @click="withStepUp(() => move(address!.pendingEmail!))"
        >
          Send the link again
        </cut-button>
      </notice-box>
      <form
        class="security-task__form"
        @submit.prevent="withStepUp(() => move(newEmail.trim()))"
      >
        <form-field
          v-slot="field"
          label="New email address"
          testid="security-new-email-field"
        >
          <text-input
            v-model="newEmail"
            autocomplete="email"
            :control-id="field.controlId"
            type="email"
          />
        </form-field>
        <div class="security-task__acts">
          <cut-button
            :disabled="!newEmail.trim()"
            submit
            testid="security-change-email-btn"
            tone="solid"
          >
            Send confirmation link
          </cut-button>
        </div>
      </form>

      <template #aside>
        <p>
          A link goes to the new address. Your account moves when you follow it. The old address is
          told, with a link to lock the account if this was not you.
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
import NoticeBox from "@/components/island/NoticeBox.vue"
import TaskLayout from "@/components/island/TaskLayout.vue"
import TextInput from "@/components/island/TextInput.vue"
import {
  askToMoveEmail,
  type EmailAddressResponse,
  readEmailAddress,
  readTwoFactor,
  SECURITY_CRUMB,
  StepUpDialog,
  useStepUp,
  type Written,
} from "@/domains/auth"
import type {TypedStore} from "@/plugins/store"

const store = useStore() as TypedStore
const tell = (message: string) => store.commit("setStatusSnackbarMessage", message)
const {open: stepUpOpen, attempt: withStepUp, proved: stepUpProved} = useStepUp(tell)

const address = ref<EmailAddressResponse | null>(null)
const newEmail = ref("")
const twoFactorOn = ref(false)

const move = async (to: string): Promise<Written<unknown>> => {
  const result = await askToMoveEmail(to)
  if (result.ok) {
    tell(`A confirmation link is on its way to ${to}.`)
    newEmail.value = ""
    address.value = await readEmailAddress()
  }
  return result
}

onMounted(async () => {
  const [standing, email] = await Promise.all([readTwoFactor(), readEmailAddress()])
  twoFactorOn.value = standing?.on === true
  address.value = email
})
</script>

<style scoped>
.security-task__now {
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
  padding: 0.9rem 1.1rem;
  background: color-mix(in oklab, var(--color-chalk) 5%, transparent);
}

.security-task__now-label {
  font-size: 0.7rem;
  font-weight: 500;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: var(--color-ash);
}

.security-task__again {
  margin-top: 0.75rem;
}

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
</style>
