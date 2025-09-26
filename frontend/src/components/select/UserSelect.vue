<script setup lang="ts">
import { ref, watch } from 'vue'
import type { SimpleUser } from '@/lib'
import {VAutocomplete} from "vuetify/lib/components";

type Rule = (v: SimpleUser | undefined) => true | string

const props = defineProps<{
  /** Bind the selected user object */
  modelValue?: SimpleUser | undefined
  /** List of available users */
  users: SimpleUser[]
  /** Optional Vuetify-style rules coming from parent */
  rules?: Rule[]
  /** Optional label */
  label?: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: SimpleUser | undefined]
}>()

/**
 * Internal selection mirrors the v-model. We use return-object on v-autocomplete,
 * so the value is the full SimpleUser.
 */
const selectedUser = ref<SimpleUser | undefined>(props.modelValue)
const inputRef = ref<InstanceType<typeof VAutocomplete> | null>(null)

/** Keep internal state in sync when parent changes v-model */
watch(
  () => props.modelValue,
  (val) => {
    // If a user object is passed, set it directly.
    // If it's undefined or no longer present in the users list, clear selection.
    if (!val) {
      selectedUser.value = undefined
      return
    }
    // If the exact object exists in the current list, prefer that instance.
    const match = props.users.find(u => u.id === val.id)
    selectedUser.value = match ?? val
  },
  { immediate: true }
)

/** Emit full user back to parent whenever selection changes */
watch(selectedUser, (val) => {
  emit('update:modelValue', val)
})

/** If the users list changes, keep selection pointing at the matching instance (or clear) */
watch(
  () => props.users,
  (list) => {
    if (!selectedUser.value) return
    const match = list.find(u => u.id === selectedUser.value?.id)
    if (!match) selectedUser.value = undefined
    else if (match !== selectedUser.value) selectedUser.value = match
  }
)

/** Title renderer */
const itemTitle = (u: SimpleUser) =>
  u?.discord ? `${u.fullName} (${u.discord})` : u?.fullName

/** Expose a small API so parent can validate/focus/reset */
function validate() {
  // Vuetify inputs expose validate() returning { valid: boolean }
  return inputRef.value?.validate?.()
}
function resetValidation() {
  inputRef.value?.resetValidation?.()
}
function focus() {
  inputRef.value?.focus?.()
}

defineExpose({ validate, resetValidation, focus })
</script>

<template>
  <v-autocomplete
    ref="inputRef"
    v-model="selectedUser"
    :items="users"
    :item-title="itemTitle"
    item-value="id"
    return-object
    :rules="rules ?? [(v: SimpleUser | undefined) => !!v || 'Select a user']"
    hide-details="auto"
    auto-select-first
    clearable
    hide-no-data
    :label="label ?? 'User name'"
  />
</template>

<style scoped lang="scss">
/* add styles if needed */
</style>
