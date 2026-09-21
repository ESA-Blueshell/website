<script lang="ts" setup>
/* Kept as the cca2 code; an older record holding a name is matched to a country on the way in. */
import {computed} from "vue"
import FormControl from "@/components/island/FormControl.vue"
import {allCountriesSorted, findTopMatch, isValidCca2} from "@/composables/countries"

const props = defineProps<{modelValue?: string | null; label?: string; testId?: string}>()
const emit = defineEmits<{"update:modelValue": [value: string | null]}>()

const code = computed<string | null>({
  get: () => {
    const incoming = props.modelValue
    if (!incoming || !incoming.trim()) return null
    if (isValidCca2(incoming)) return incoming.toUpperCase()
    return findTopMatch(incoming, allCountriesSorted)?.cca2 ?? null
  },
  set: value => emit("update:modelValue", value),
})
</script>

<template>
  <form-control
    v-model="code"
    kind="country"
    :label="label ?? 'Country'"
    :testid="testId"
  />
</template>
