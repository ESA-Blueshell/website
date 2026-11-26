<script lang="ts" setup>
/* The country field's list and matching, read as what somebody from there is called. */
import FormControl from "@/components/island/FormControl.vue"
import {countriesWithFlagSorted} from "@/composables/countries"
import {useMatchedCountry} from "@/composables/matchedCountry"

const props = defineProps<{modelValue?: string | null; label?: string; testId?: string}>()
const emit = defineEmits<{"update:modelValue": [value: string | null]}>()

const code = useMatchedCountry(() => props.modelValue, value => emit("update:modelValue", value), countriesWithFlagSorted)
</script>

<template>
  <form-control
    v-model="code"
    kind="nationality"
    :label="label ?? 'Nationality'"
    :testid="testId"
  />
</template>
