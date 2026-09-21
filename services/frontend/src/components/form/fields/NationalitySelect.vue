<script lang="ts" setup>
/**
 * The nationality a form asks for, kept as the cca2 code of the country it belongs to.
 *
 * Same list as the country field and the same matching for a record that holds a name, said the
 * way a person is described rather than the way a place is.
 */
import {computed} from "vue"
import IslandControl from "@/components/island/IslandControl.vue"
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
  <island-control
    v-model="code"
    kind="nationality"
    :label="label ?? 'Nationality'"
    :testid="testId"
  />
</template>
