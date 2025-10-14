<template>
  <v-select
    v-model="selected"
    :items="memberTypeOptions"
    :rules="[requiredRule]"
    item-title="text"
    item-value="value"
    label="Member Type"
  />
</template>

<script lang="ts" setup>
import {ref, watch} from "vue"
import {MemberType} from "@/services/api"

// Props & emits
const props = withDefaults(defineProps<{
  modelValue?: string
}>(), {
  modelValue: MemberType.ALUMNI,
})

const emit = defineEmits<{
  (e: "update:modelValue", value: string): void
}>()

// Local state mirrors v-model
const selected = ref(props.modelValue)

// Build select options from enum
const memberTypeOptions = Object.values(MemberType).map((type: MemberType) => ({
  text: `${type.charAt(0)}${type.slice(1).toLowerCase()}`,
  value: type,
}))

// Validation
const requiredRule = (value: MemberType) => !!value || "Member type is required"

// Keep prop and local state in sync (both directions)
watch(selected, (val) => emit("update:modelValue", val))
watch(() => props.modelValue, (val) => {
  if (val !== selected.value) selected.value = val
})
</script>
