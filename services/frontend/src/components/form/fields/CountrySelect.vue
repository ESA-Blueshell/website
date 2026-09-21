<script lang="ts" setup>
/**
 * The country a form asks for, kept as the cca2 code the api stores.
 *
 * A code that arrives as a name, which older records hold, is matched to a country on the way
 * in, so the field reads as chosen rather than as empty.
 */
import {computed} from "vue"
import IslandControl from "@/components/island/IslandControl.vue"
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
  <island-control
    v-model="code"
    kind="country"
    :label="label ?? 'Country'"
    :testid="testId"
  />
</template>
