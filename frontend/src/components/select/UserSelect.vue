<script lang="ts" setup>
import {ref, watch} from "vue"
import type {AdvancedUser} from "@/lib"
import {VAutocomplete} from "vuetify/components"

type Rule = (v: AdvancedUser | undefined) => true | string

const props = defineProps<{
  /** Bind the selected user object */
  modelValue?: number | undefined
  /** List of available users */
  users: AdvancedUser[]
  /** Optional Vuetify-style rules coming from parent */
  rules?: Rule[]
  /** Optional label */
  label?: string
}>()

const emit = defineEmits<{
  "update:modelValue": [value: AdvancedUser | undefined]
}>()

/**
 * Internal selection mirrors the v-model. We use return-object on v-autocomplete,
 * so the value is the full SimpleUser.
 */
const selectedUser = ref<AdvancedUser | undefined>(props.users.find((u: AdvancedUser) => u.id == props.modelValue))
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
    selectedUser.value = props.users.find(u => u.id === val)
  },
  {immediate: true},
)

/** Emit full user back to parent whenever selection changes */
watch(selectedUser, (val) => {
  emit("update:modelValue", val)
})

/** If the users list changes, keep selection pointing at the matching instance (or clear) */
watch(
  () => props.users,
  (list) => {
    if (!selectedUser.value) return
    const match = list.find(u => u.id === selectedUser.value?.id)
    if (!match) selectedUser.value = undefined
    else if (match !== selectedUser.value) selectedUser.value = match
  },
)

/** Title renderer */
const itemTitle = (u: AdvancedUser) =>
  u?.discord ? `${u.fullName} (${u.discord})` : u?.fullName

/** Expose a small API so parent can validate/focus/reset */
function validate() {
  return inputRef.value?.validate?.()
}

function resetValidation() {
  inputRef.value?.resetValidation?.()
}

function focus() {
  inputRef.value?.focus?.()
}

defineExpose({validate, resetValidation, focus})
</script>

<template>
  <v-autocomplete
    ref="inputRef"
    v-model="selectedUser"
    :item-title="itemTitle"
    :items="users"
    :label="label ?? 'User name'"
    :rules="rules ?? [(v: AdvancedUser | undefined) => !!v || 'Select a user']"
    auto-select-first
    clearable
    hide-details="auto"
    hide-no-data
    item-value="id"
    return-object
  />
</template>

<style lang="scss" scoped>
/* add styles if needed */
</style>
