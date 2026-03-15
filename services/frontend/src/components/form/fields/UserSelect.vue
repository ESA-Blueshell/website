<script lang="ts" setup>
import {ref, watch} from "vue"
import type {UserDetailResponse} from "@/services/api"
import {VAutocomplete} from "vuetify/components"

type Rule = (v: UserDetailResponse | undefined) => true | string

const props = defineProps<{
  modelValue?: number | undefined
  users: UserDetailResponse[]
  rules?: Rule[]
  label?: string
}>()
const emit = defineEmits<{ "update:modelValue": [value: number | undefined] }>()

const selectedUser = ref<UserDetailResponse | undefined>(props.users.find((u) => u.id == props.modelValue))
const inputRef = ref<InstanceType<typeof VAutocomplete> | null>(null)

watch(
  () => props.modelValue,
  (val) => {
    if (!val) {
      selectedUser.value = undefined
      return
    }
    selectedUser.value = props.users.find((u) => u.id === val)
  },
  {immediate: true},
)

watch(selectedUser, (val) => emit("update:modelValue", val?.id))

watch(
  () => props.users,
  (list) => {
    if (!selectedUser.value) return
    const match = list.find((u) => u.id === selectedUser.value?.id)
    selectedUser.value = match
  },
)

const itemTitle = (u: UserDetailResponse) => (u?.discord ? `${u.fullName} (${u.discord})` : u?.fullName)

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
    :rules="rules ?? [(v: UserDetailResponse | undefined) => !!v || 'Select a user']"
    auto-select-first
    clearable
    hide-details="auto"
    hide-no-data
    item-value="id"
    return-object
  />
</template>

<style lang="scss" scoped>
</style>
