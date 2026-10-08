<template>
  <div
    class="island-panel"
    data-testid="email-confirm-step"
  >
    <h2 class="island-panel__title">
      Confirm your email address
    </h2>

    <notice-box
      tone="info"
    >
      Open the link we sent to <strong>{{ email }}</strong> to confirm your address.
      {{ confirmationConsequence }}
    </notice-box>

    <div
      v-if="correcting"
      data-testid="email-confirm-correct-form"
    >
      <form-control
        v-model="correctedEmail"
        data-testid="email-confirm-address-field"
        :error-messages="errorsOf('email')"
        kind="email"
        label="Email address"
        @blur="touch('email')"
      />
      <div class="panel-acts">
        <cut-button
          tone="quiet"
          :disabled="submitting"
          data-testid="email-confirm-address-cancel-btn"
          @click="correcting = false"
        >
          Cancel
        </cut-button>
        <cut-button
          tone="solid"
          :disabled="submitting"
          data-testid="email-confirm-address-submit-btn"
          @click="correctEmailAddress"
        >
          Send to this address
        </cut-button>
      </div>
    </div>

    <div
      v-else
      class="panel-acts panel-acts--split"
    >
      <cut-button
        tone="plain"
        data-testid="email-confirm-back-btn"
        @click="emit('back')"
      >
        Previous
      </cut-button>
      <cut-button
        tone="plain"
        :disabled="submitting"
        data-testid="email-confirm-correct-btn"
        @click="startCorrecting"
      >
        Wrong address?
      </cut-button>
      <cut-button
        tone="plain"
        :disabled="submitting"
        data-testid="email-confirm-resend-btn"
        @click="resend"
      >
        Send it again
      </cut-button>
    </div>
  </div>
</template>

<script lang="ts" setup>
import CutButton from "@/components/island/CutButton.vue"
import NoticeBox from "@/components/island/NoticeBox.vue"
import {ref} from "vue"
import FormControl from "@/components/island/FormControl.vue"
import {correctSignupEmail, resendActivation} from "@/domains/recovery"
import store from "@/plugins/store"
import {$handleNetworkError} from "@/plugins/handleNetworkError"
import {reportRefusal, useFormChecks} from "@/composables/useFormChecks"
import {email as emailCheck, required} from "@/utils/checks"

const {
  email,
  username,
  continuationToken = undefined,
  confirmationConsequence = "",
} = defineProps<{
  email: string
  username: string
  /**
   * The continuation token for the not-yet-confirmed account. Absent once the
   * address is confirmed, which is when corrections stop.
   */
  continuationToken?: string
  /** What confirming brings about, which differs per flow. */
  confirmationConsequence?: string
}>()

const emit = defineEmits<{
  (e: "email-corrected", email: string): void
  (e: "back"): void
}>()

const correcting = ref(false)
const correctedEmail = ref("")
const submitting = ref(false)

const checks = useFormChecks(() => ({
  email: {value: () => correctedEmail.value, checks: [required, emailCheck]},
}))
const {errorsOf, touch} = checks

async function withSubmitting(action: () => Promise<void>) {
  try {
    submitting.value = true
    await action()
  } finally {
    submitting.value = false
  }
}

function startCorrecting() {
  correctedEmail.value = email
  correcting.value = true
}

const correctEmailAddress = () => withSubmitting(async () => {
  if (!checks.attempt()) return
  if (!continuationToken) {
    store.commit("setStatusSnackbarMessage", "this signup expired, so sign in or start again")
    return
  }
  try {
    await correctSignupEmail(continuationToken, correctedEmail.value)
    correcting.value = false
    store.commit("setStatusSnackbarMessage", `Confirmation sent to ${correctedEmail.value}`)
    emit("email-corrected", correctedEmail.value)
  } catch (e) {
    reportRefusal(checks, e)
  }
})

const resend = () => withSubmitting(async () => {
  // No account to name means this panel was reached without a signup behind it, which
  // no press mends. Said out loud, because a button that answers nothing reads as broken.
  if (!username) {
    store.commit("setStatusSnackbarMessage", "there is no account to confirm here, so start again")
    return
  }
  const result = await resendActivation(username)
  if (result.outcome === "rate-limited") {
    $handleNetworkError(result.cause)
    return
  }
  store.commit("setStatusSnackbarMessage", `Confirmation sent to ${email}`)
})
</script>
