<script lang="ts" setup>
import {ref, watch} from "vue"
import CheckBox from "@/components/island/CheckBox.vue"
import CutButton from "@/components/island/CutButton.vue"
import ModalDialog from "@/components/island/ModalDialog.vue"

defineOptions({name: "RemoveSignUpDialog"})

const {modelValue, personName = ""} = defineProps<{
  modelValue: boolean
  personName?: string
}>()

const emit = defineEmits<{
  (e: "update:modelValue", value: boolean): void
  (e: "confirm", notify: boolean): void
}>()

// Silence is the default: a board member who wants the email asks for it.
const notify = ref(false)

watch(() => modelValue, (open: boolean) => {
  if (open) notify.value = false
})
</script>

<template>
  <modal-dialog
    :open="modelValue"
    testid="remove-signup-dialog"
    title="Remove sign-up"
    @update:open="emit('update:modelValue', $event)"
  >
    <p class="remove-signup__question">
      Remove {{ personName || "this sign-up" }} from the sign-ups? Their answers are kept.
    </p>
    <check-box
      v-model="notify"
      label="Email them about it"
      testid="remove-signup-notify"
    />

    <template #footer>
      <div class="remove-signup__actions">
        <cut-button
          testid="remove-signup-cancel-btn"
          tone="quiet"
          @click="emit('update:modelValue', false)"
        >
          Cancel
        </cut-button>
        <cut-button
          testid="remove-signup-confirm-btn"
          tone="danger"
          @click="emit('confirm', notify)"
        >
          Remove
        </cut-button>
      </div>
    </template>
  </modal-dialog>
</template>

<style scoped>
.remove-signup__actions {
  display: flex;
  justify-content: flex-end;
  gap: 0.6rem;
  padding: 0.9rem 1.25rem 1.1rem;
  border-top: 1px solid var(--color-hairline);
}

.remove-signup__question {
  margin-bottom: 1rem;
  color: var(--color-ash);
}
</style>
