<template>
  <v-autocomplete
    v-model="selectedCountry"
    :items="countryItems"
    :item-title="displayName"
    item-value="cca2"
    :label="label ?? 'Country'"
    clearable
    :custom-filter="customFilter"
  />
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import countries, { type Country } from 'world-countries'
import type { InternalItem } from 'vuetify'

// Props / emits unchanged: we still pass the 2-letter code (cca2) to parent
const props = defineProps<{ modelValue?: string | null; label?: string }>()
const emit = defineEmits<{ 'update:modelValue': [value: string | null] }>()

const selectedCountry = ref<string | null>(props.modelValue ?? null)

// Use countries as-is (optionally sort for nicer UX)
const countryItems = computed<Country[]>(() =>
  [...countries].sort((a, b) => {
    const ta =  a.name.common.toLowerCase()
    const tb = b.name.common.toLowerCase()
    return ta.localeCompare(tb)
  })
)

// Show demonym if present; otherwise the common name
const displayName = (c: Country) => c.flag + ' ' + (c.name?.common || c.name?.official)

// Search by demonym, common/official name, or code (cca2/cca3/cioc)
const customFilter = (_itemText: string, queryText: string, item: InternalItem<Country>) => {
  const c = item.raw
  const q = queryText.trim().toLowerCase()
  if (!q) return true

  const parts = [
    c.demonyms?.eng?.m,
    c.demonyms?.eng?.f,
    c.name?.common,
    c.name?.official,
    c.cca2,
    c.cca3,
    c.cioc,
    ...(c.altSpellings ?? []),
  ]
    .filter(Boolean)
    .map(s => String(s).toLowerCase())

  return parts.some(p => p.includes(q))
}

// v-model wiring unchanged
watch(selectedCountry, (newVal) => {
  emit('update:modelValue', newVal)
})

watch(() => props.modelValue, (newVal) => {
  if (newVal !== selectedCountry.value) {
    selectedCountry.value = newVal ?? null
  }
})
</script>
