<script lang="ts" setup>
import BaseModal from "./BaseModal.vue"

defineOptions({name: "ConfirmationDialog"})

interface Props {
  modelValue: boolean
  title: string
  message: string
  confirmLabel?: string
  confirmColor?: string
  /** Prefix for button testids (${testid}-confirm-btn, ${testid}-cancel-btn).
   * Also used as the dialog's data-testid unless dialogTestid is provided. */
  testid?: string
  /** Override for the dialog's data-testid. Defaults to testid. */
  dialogTestid?: string
}

withDefaults(defineProps<Props>(), {
  confirmLabel: "Confirm",
  confirmColor: "red",
  testid: "confirmation-dialog",
  dialogTestid: undefined,
})

const emit = defineEmits<{
  (e: "update:modelValue", value: boolean): void
  (e: "confirm"): void
}>()
</script>

<template>
  <base-modal
    :model-value="modelValue"
    :title="title"
    :testid="dialogTestid ?? testid"
    max-width="400"
    :scrollable="false"
    show-save
    :save-label="confirmLabel"
    :save-color="confirmColor"
    :save-testid="`${testid}-confirm-btn`"
    show-cancel
    :cancel-testid="`${testid}-cancel-btn`"
    @update:model-value="(v) => emit('update:modelValue', v)"
    @save="emit('confirm')"
  >
    {{ message }}
  </base-modal>
</template>
