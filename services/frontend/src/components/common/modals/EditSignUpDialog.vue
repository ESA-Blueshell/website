<script lang="ts" setup>
import ModalDialog from "@/components/island/ModalDialog.vue"
import EventSignUpForm from "@/components/form/EventSignUpForm.vue"
import type {EventResponse, EventSignUpResponse} from "@/domains/events"

defineOptions({name: "EditSignUpDialog"})

defineProps<{
  modelValue: boolean
  event: EventResponse
  signUp: EventSignUpResponse
}>()

const emit = defineEmits<{
  (e: "update:modelValue", value: boolean): void
  (e: "saved", signUp: EventSignUpResponse): void
}>()

function onSaved(updated: EventSignUpResponse): void {
  emit("saved", updated)
  emit("update:modelValue", false)
}
</script>

<template>
  <modal-dialog
    :open="modelValue"
    testid="edit-signup-dialog"
    title="Edit sign-up"
    @update:open="emit('update:modelValue', $event)"
  >
    <event-sign-up-form
      board-edit
      :event="event"
      :initial-sign-up="signUp"
      @update:sign-up="onSaved"
    />
  </modal-dialog>
</template>
