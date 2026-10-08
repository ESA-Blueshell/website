<template>
  <modal-dialog
    cancel-testid="step-up-cancel-btn"
    :open="modelValue"
    testid="step-up-dialog"
    title="Confirm it is you"
    @update:open="emit('update:modelValue', $event)"
  >
    <form
      id="step-up-form"
      class="step-up"
      @submit.prevent="submit"
    >
      <p>
        {{ twoFactorOn
          ? (useBackupCode ? "Enter one of your backup codes." : "Enter the code from your authenticator app.")
          : "Enter your password." }}
      </p>
      <form-control
        v-model="proof"
        :autocomplete="twoFactorOn ? 'one-time-code' : 'current-password'"
        data-testid="step-up-field"
        :inputmode="twoFactorOn && !useBackupCode ? 'numeric' : 'text'"
        :kind="twoFactorOn ? 'text' : 'password'"
        :label="twoFactorOn ? (useBackupCode ? 'Backup code' : 'Code') : 'Password'"
      />
      <notice-box
        v-if="error"
        tone="danger"
      >
        <p>{{ error }}</p>
      </notice-box>
      <cut-button
        v-if="twoFactorOn"
        small
        testid="step-up-backup-toggle"
        tone="quiet"
        @click="useBackupCode = !useBackupCode"
      >
        {{ useBackupCode ? "Use the authenticator app" : "Use a backup code" }}
      </cut-button>
    </form>
    <template #footer>
      <cut-button
        :disabled="!proof || busy"
        form="step-up-form"
        submit
        testid="step-up-submit-btn"
        tone="solid"
      >
        {{ busy ? "Confirming" : "Confirm" }}
      </cut-button>
    </template>
  </modal-dialog>
</template>

<script lang="ts" setup>
import ModalDialog from "@/components/island/ModalDialog.vue"
import FormControl from "@/components/island/FormControl.vue"
import CutButton from "@/components/island/CutButton.vue"
import NoticeBox from "@/components/island/NoticeBox.vue"
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

<style scoped>
.step-up {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 0.8rem;
}

.step-up > :deep(.island-field) {
  align-self: stretch;
}
</style>
