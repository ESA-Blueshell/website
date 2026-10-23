<script lang="ts" setup>
import FormControl from "@/components/island/FormControl.vue"
import {allCountriesSorted} from "@/composables/countries"
import {useMatchedCountry} from "@/composables/matchedCountry"

const props = defineProps<{modelValue?: string | null; label?: string; testId?: string}>()
const emit = defineEmits<{"update:modelValue": [value: string | null]}>()

const code = useMatchedCountry(() => props.modelValue, value => emit("update:modelValue", value), allCountriesSorted)
</script>

<template>
  <form-control
    v-model="code"
    kind="country"
    :label="label ?? 'Country'"
    :testid="testId"
  />
</template>
