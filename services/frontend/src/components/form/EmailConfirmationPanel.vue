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

    <Form
      v-if="correcting"
      ref="formRef"
      as="div"
      data-testid="email-confirm-correct-form"
    >
      <VvField
        v-model="correctedEmail"
        :component-props="{ type: 'email', 'data-testid': 'email-confirm-address-field' }"
        label="Email address"
        name="email"
        rules="required|email"
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
    </Form>

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
import {Form} from "vee-validate"
import VvField from "@/components/form/fields/VvField.vue"
import {correctSignupEmail, resendActivation} from "@/domains/recovery"
import store from "@/plugins/store"
import {$handleNetworkError} from "@/plugins/handleNetworkError"
import {handleSubmitError, useVeeForm} from "@/composables/formUtils"

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

const {formRef, validate} = useVeeForm()

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
  if (!(await validate())) return
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
    handleSubmitError(formRef.value, e)
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
