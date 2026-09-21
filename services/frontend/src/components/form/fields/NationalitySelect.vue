<script lang="ts" setup>
/* The country field's list and matching, read as what somebody from there is called. */
import {computed} from "vue"
import FormControl from "@/components/island/FormControl.vue"
import {countriesWithFlagSorted, findTopMatch, isValidCca2} from "@/composables/countries"

const props = defineProps<{modelValue?: string | null; label?: string; testId?: string}>()
const emit = defineEmits<{"update:modelValue": [value: string | null]}>()

const code = computed<string | null>({
  get: () => {
    const incoming = props.modelValue
    if (!incoming || !incoming.trim()) return null
    if (isValidCca2(incoming)) return incoming.toUpperCase()
    return findTopMatch(incoming, countriesWithFlagSorted)?.cca2 ?? null
  },
  set: value => emit("update:modelValue", value),
})
</script>

<template>
  <form-control
    v-model="code"
    kind="nationality"
    :label="label ?? 'Nationality'"
    :testid="testId"
  />
</template>
