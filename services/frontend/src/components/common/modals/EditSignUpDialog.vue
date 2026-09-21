<script lang="ts" setup>
import {ref} from "vue"
import BaseModal from "./BaseModal.vue"
import EventSignUpForm from "@/components/form/EventSignUpForm.vue"
import type {EventResponse, EventSignUpResponse} from "@/domains/events"

defineOptions({name: "EditSignUpDialog"})

defineProps<{
  modelValue: boolean;
  event: EventResponse;
  signUp: EventSignUpResponse;
}>()

const emit = defineEmits<{
  (e: "update:modelValue", value: boolean): void
  (e: "saved", signUp: EventSignUpResponse): void
}>()

const formRef = ref<InstanceType<typeof EventSignUpForm>>()

function onSaved(updated: EventSignUpResponse): void {
  emit("saved", updated)
  emit("update:modelValue", false)
}
</script>

<template>
  <base-modal
    :model-value="modelValue"
    title="Edit sign-up"
    testid="edit-signup-dialog"
    fullscreen-mobile
    @update:model-value="(v) => emit('update:modelValue', v)"
  >
    <event-sign-up-form
      ref="formRef"
      board-edit
      :event="event"
      :initial-sign-up="signUp"
      @update:sign-up="onSaved"
    />
  </base-modal>
</template>
