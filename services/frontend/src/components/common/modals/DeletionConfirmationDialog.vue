<script lang="ts" setup>
import ConfirmationDialog from "./ConfirmationDialog.vue"

defineOptions({name: "DeletionConfirmationDialog"})

interface Props {
  modelValue?: boolean
  title?: string
  message?: string
}

withDefaults(defineProps<Props>(), {
  modelValue: false,
  title: "",
  message: "",
})

const emit = defineEmits<{
  (e: "update:modelValue", value: boolean): void
  (e: "confirm"): void
}>()

// Exposed so that unit tests can call wrapper.vm.confirm() directly (matches old API)
function confirm() {
  emit("confirm")
}

defineExpose({confirm})
</script>

<template>
  <confirmation-dialog
    :model-value="modelValue"
    :title="title"
    :message="message"
    confirm-label="Delete"
    testid="deletion-confirmation"
    dialog-testid="deletion-confirmation-dialog"
    @update:model-value="(v) => emit('update:modelValue', v)"
    @confirm="confirm"
  />
</template>
