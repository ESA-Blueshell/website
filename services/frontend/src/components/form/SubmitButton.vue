<script lang="ts" setup>
/* A form's save button; whether the save worked is said in the message bar, not on the button. */
import CutButton from "@/components/island/CutButton.vue"

defineOptions({name: "SubmitButton"})

const {text = "Submit", workingText = "Saving", loading = false, disabled = false, type = "button"} = defineProps<{
  text?: string
  workingText?: string
  loading?: boolean
  disabled?: boolean
  type?: "button" | "submit" | "reset"
}>()

const emit = defineEmits<{(e: "click"): void}>()
</script>

<template>
  <cut-button
    :disabled="disabled || loading"
    :submit="type === 'submit'"
    tone="solid"
    @click="emit('click')"
  >
    <slot>{{ loading ? workingText : text }}</slot>
  </cut-button>
</template>
