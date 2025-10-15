<template>
  <v-autocomplete
    v-model="selectedCountry"
    :custom-filter="customFilter"
    :item-title="displayName"
    :items="countryItems"
    :label="label ?? 'Country'"
    clearable
    item-value="cca2"
  />
</template>

<script lang="ts" setup>
import {computed, ref, watch} from "vue"
import countries, {type Country} from "world-countries"
import type {InternalItem} from "vuetify"

const props = defineProps<{ modelValue?: string | null; label?: string }>()
const emit = defineEmits<{ "update:modelValue": [value: string | null] }>()

const selectedCountry = ref<string | null>(props.modelValue ?? null)

const countryItems = computed<Country[]>(() =>
  [...countries].sort((a, b) => {
    const ta = a.name.common.toLowerCase()
    const tb = b.name.common.toLowerCase()
    return ta.localeCompare(tb)
  }),
)

// Show demonym if present; otherwise the common name
const displayName = (c: Country) => c.flag + " " + (c.name?.common || c.name?.official)

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
  emit("update:modelValue", newVal)
})

watch(() => props.modelValue, (newVal) => {
  if (newVal !== selectedCountry.value) {
    selectedCountry.value = newVal ?? null
  }
})
</script>
