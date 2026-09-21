<script lang="ts" setup>
import {ref, watch} from "vue"
import BaseModal from "./BaseModal.vue"

defineOptions({name: "RemoveSignUpDialog"})

interface Props {
  modelValue: boolean
  /** Whoever the sign-up names, for the sentence the board member reads. */
  personName?: string
}

const props = withDefaults(defineProps<Props>(), {personName: ""})

const emit = defineEmits<{
  (e: "update:modelValue", value: boolean): void
  (e: "confirm", notify: boolean): void
}>()

// Silence is the default: a board member who wants the email asks for it.
const notify = ref(false)

watch(() => props.modelValue, (open: boolean) => {
  if (open) notify.value = false
})
</script>

<template>
  <base-modal
    :model-value="modelValue"
    title="Remove sign-up"
    testid="remove-signup-dialog"
    max-width="460"
    :scrollable="false"
    show-save
    save-label="Remove"
    save-color="red"
    save-testid="remove-signup-confirm-btn"
    show-cancel
    cancel-testid="remove-signup-cancel-btn"
    @update:model-value="(v) => emit('update:modelValue', v)"
    @save="emit('confirm', notify)"
  >
    <p>
      Remove {{ personName || "this sign-up" }} from the sign-ups? Their answers are kept.
    </p>
    <v-checkbox
      v-model="notify"
      data-testid="remove-signup-notify"
      hide-details
      label="Email them about it"
    />
  </base-modal>
</template>
