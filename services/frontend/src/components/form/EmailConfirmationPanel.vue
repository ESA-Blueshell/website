<template>
  <v-card
    class="pa-6"
    data-testid="email-confirm-step"
  >
    <div class="d-flex align-center mb-2">
      <v-icon
        class="mr-2"
        color="primary"
        size="28"
      >
        mdi-email-fast-outline
      </v-icon>
      <span class="text-h6 font-weight-medium">Confirm your email address</span>
    </div>

    <v-alert
      border="start"
      class="mb-4"
      color="primary"
      variant="tonal"
    >
      Open the link we sent to <strong>{{ email }}</strong> to confirm your address.
      {{ confirmationConsequence }}
    </v-alert>

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
        rules="required|email|noStudentEmail"
      />
      <v-row
        align="center"
        justify="end"
      >
        <v-col cols="auto">
          <v-btn
            :disabled="submitting"
            data-testid="email-confirm-address-cancel-btn"
            variant="text"
            @click="correcting = false"
          >
            Cancel
          </v-btn>
        </v-col>
        <v-col cols="auto">
          <v-btn
            :disabled="submitting"
            :loading="submitting"
            color="primary"
            data-testid="email-confirm-address-submit-btn"
            @click="correctEmailAddress"
          >
            Send to this address
          </v-btn>
        </v-col>
      </v-row>
    </Form>

    <v-row
      v-else
      align="center"
    >
      <v-col cols="auto">
        <v-btn
          data-testid="email-confirm-back-btn"
          variant="outlined"
          @click="emit('back')"
        >
          Previous
        </v-btn>
      </v-col>
      <v-spacer />
      <v-col cols="auto">
        <v-btn
          :disabled="submitting"
          data-testid="email-confirm-correct-btn"
          variant="outlined"
          @click="startCorrecting"
        >
          Wrong address?
        </v-btn>
      </v-col>
      <v-col cols="auto">
        <v-btn
          :disabled="submitting"
          :loading="submitting"
          data-testid="email-confirm-resend-btn"
          variant="outlined"
          @click="resend"
        >
          Send it again
        </v-btn>
      </v-col>
    </v-row>
  </v-card>
</template>

<script lang="ts" setup>
import {ref} from "vue"
import {Form} from "vee-validate"
import VvField from "@/components/form/fields/VvField.vue"
import {correctEmail, resendUserActivation} from "@/services/api"
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
    await correctEmail({
      headers: {"X-Signup-Token": continuationToken},
      body: {email: correctedEmail.value},
      throwOnError: true,
    })
    correcting.value = false
    store.commit("setStatusSnackbarMessage", `Confirmation sent to ${correctedEmail.value}`)
    emit("email-corrected", correctedEmail.value)
  } catch (e) {
    handleSubmitError(formRef.value, e)
  }
})

const resend = () => withSubmitting(async () => {
  if (!username) return
  try {
    await resendUserActivation({path: {username}, throwOnError: true})
    store.commit("setStatusSnackbarMessage", `Confirmation sent to ${email}`)
  } catch (e) {
    $handleNetworkError(e)
  }
})
</script>
