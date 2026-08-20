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

    <v-form
      v-if="correcting"
      data-testid="email-confirm-correct-form"
      @submit.prevent="correctEmailAddress"
    >
      <v-text-field
        v-model="correctedEmail"
        data-testid="email-confirm-address-field"
        label="Email address"
        type="email"
      />
      <v-row
        align="center"
        justify="end"
      >
        <v-col cols="auto">
          <v-btn
            variant="text"
            @click="correcting = false"
          >
            Cancel
          </v-btn>
        </v-col>
        <v-col cols="auto">
          <v-btn
            :loading="submitting"
            color="primary"
            data-testid="email-confirm-address-submit-btn"
            type="submit"
          >
            Send to this address
          </v-btn>
        </v-col>
      </v-row>
    </v-form>

    <v-row
      v-else
      align="center"
    >
      <v-col cols="auto">
        <v-btn
          data-testid="email-confirm-correct-btn"
          variant="outlined"
          @click="startCorrecting"
        >
          Wrong address?
        </v-btn>
      </v-col>
      <v-col cols="auto">
        <v-btn
          :loading="submitting"
          data-testid="email-confirm-resend-btn"
          variant="outlined"
          @click="resend"
        >
          Send it again
        </v-btn>
      </v-col>
      <v-spacer />
      <v-col cols="auto">
        <v-btn
          color="primary"
          data-testid="email-confirm-sign-in-btn"
          prepend-icon="mdi-login"
          @click="$goto('/login')"
        >
          Sign in
        </v-btn>
      </v-col>
    </v-row>

    <template v-if="!correcting">
      <v-divider class="my-4" />
      <div class="d-flex flex-wrap align-center">
        <span class="text-body-2 text-medium-emphasis mr-2">Something else to change?</span>
        <v-btn
          data-testid="email-confirm-change-details-btn"
          size="small"
          variant="text"
          @click="emit('change-details')"
        >
          Your details
        </v-btn>
        <v-btn
          v-if="canChangeAddress"
          data-testid="email-confirm-change-address-btn"
          size="small"
          variant="text"
          @click="emit('change-address')"
        >
          Your address
        </v-btn>
      </div>
    </template>
  </v-card>
</template>

<script lang="ts" setup>
import {ref} from "vue"
import {correctEmail, resendUserActivation} from "@/services/api"
import store from "@/plugins/store"
import {$handleNetworkError} from "@/plugins/handleNetworkError"
import {$goto} from "@/plugins/goto"

const {
  email,
  username,
  continuationToken = undefined,
  canChangeAddress = false,
  confirmationConsequence = "",
} = defineProps<{
  email: string
  username: string
  /**
   * The continuation token for the not-yet-confirmed account. Absent once the
   * address is confirmed, which is when corrections stop.
   */
  continuationToken?: string
  canChangeAddress?: boolean
  /** What confirming brings about, which differs per flow. */
  confirmationConsequence?: string
}>()

const emit = defineEmits<{
  (e: "email-corrected", email: string): void
  (e: "change-details"): void
  (e: "change-address"): void
}>()

const correcting = ref(false)
const correctedEmail = ref("")
const submitting = ref(false)

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
  if (!continuationToken || !correctedEmail.value) return
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
    $handleNetworkError(e)
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
