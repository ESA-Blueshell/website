<template>
  <v-dialog
    :model-value="modelValue"
    max-width="440"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <v-card
      data-testid="step-up-dialog"
      title="Confirm it is you"
    >
      <v-form @submit.prevent="submit">
        <v-card-text>
          <p class="mb-3">
            {{ twoFactorOn
              ? (useBackupCode ? "Enter one of your backup codes." : "Enter the code from your authenticator app.")
              : "Enter your password." }}
          </p>
          <v-text-field
            v-model="proof"
            :autocomplete="twoFactorOn ? 'one-time-code' : 'current-password'"
            data-testid="step-up-field"
            :inputmode="twoFactorOn && !useBackupCode ? 'numeric' : 'text'"
            :label="twoFactorOn ? (useBackupCode ? 'Backup code' : 'Code') : 'Password'"
            :type="twoFactorOn ? 'text' : 'password'"
            autofocus
          />
          <v-alert
            v-if="error"
            type="error"
            variant="tonal"
          >
            {{ error }}
          </v-alert>
        </v-card-text>
        <v-card-actions>
          <v-btn
            v-if="twoFactorOn"
            size="small"
            variant="text"
            @click="useBackupCode = !useBackupCode"
          >
            {{ useBackupCode ? "use the authenticator app" : "use a backup code" }}
          </v-btn>
          <v-spacer />
          <v-btn
            variant="text"
            @click="emit('update:modelValue', false)"
          >
            Cancel
          </v-btn>
          <v-btn
            :disabled="!proof"
            :loading="busy"
            color="primary"
            data-testid="step-up-submit-btn"
            type="submit"
          >
            Confirm
          </v-btn>
        </v-card-actions>
      </v-form>
    </v-card>
  </v-dialog>
</template>

<script lang="ts" setup>
import {ref, watch} from "vue"
import {stepUp} from "../adapters/auth"

const props = defineProps<{ modelValue: boolean; twoFactorOn: boolean }>()
const emit = defineEmits<{ "update:modelValue": [open: boolean]; proved: [] }>()

const proof = ref("")
const useBackupCode = ref(false)
const busy = ref(false)
const error = ref<string | null>(null)

watch(() => props.modelValue, open => {
  if (!open) return
  proof.value = ""
  error.value = null
})

const submit = async () => {
  busy.value = true
  const result = await stepUp(props.twoFactorOn ? {code: proof.value.trim()} : {password: proof.value})
  busy.value = false
  if (!result.ok) {
    error.value = result.reason
    return
  }
  emit("update:modelValue", false)
  emit("proved")
}
</script>
