<script lang="ts" setup>
/* Kept as the cca2 code; an older record holding a name or a lowercase code is matched to a
   country on the way in, and the match is written back so the api never receives the original. */
import {computed, watch} from "vue"
import FormControl from "@/components/island/FormControl.vue"
import {allCountriesSorted, findTopMatch, isValidCca2} from "@/composables/countries"

const props = defineProps<{modelValue?: string | null; label?: string; testId?: string}>()
const emit = defineEmits<{"update:modelValue": [value: string | null]}>()

const matched = computed<string | null>(() => {
  const incoming = props.modelValue
  if (!incoming || !incoming.trim()) return null
  if (isValidCca2(incoming)) return incoming.toUpperCase()
  return findTopMatch(incoming, allCountriesSorted)?.cca2 ?? null
})

// An unmatched value is left to the picker, which opens on the Netherlands instead.
watch(matched, value => {
  if (value && value !== props.modelValue) emit("update:modelValue", value)
}, {immediate: true})

const code = computed<string | null>({
  get: () => matched.value,
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
