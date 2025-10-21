<!-- CountrySelect.vue -->
<template>
  <v-autocomplete
    v-model="selectedCountry"
    v-model:search="searchText"
    :custom-filter="customFilter"
    :item-title="displayName"
    :items="countryItems"
    :label="label ?? 'Country'"
    clearable
    item-value="cca2"
  />
</template>

<script lang="ts" setup>
import {computed, onMounted, ref, watch} from "vue"
import countries, {type Country} from "world-countries"
import type {InternalItem} from "vuetify"

// We still pass cca2 to the parent
const props = defineProps<{ modelValue?: string | null; label?: string }>()
const emit = defineEmits<{ "update:modelValue": [value: string | null] }>()

// === State ===
const selectedCountry = ref<string | null>(props.modelValue ?? null) // holds cca2 or null
const searchText = ref<string>("")

// === Data / helpers ===
const countryItems = computed<Country[]>(() =>
  [...countries].sort((a, b) => {
    const ta = (a.name?.common || a.name.official).toLowerCase()
    const tb = (b.name?.common || b.name.official).toLowerCase()
    return ta.localeCompare(tb)
  }),
)

// Show flag + common (keeps "Country" display distinct from "Nationality")
const displayName = (c: Country) => c.flag + " " + (c.name?.common || c.name?.official)

const deburrLower = (s: string) =>
  s.normalize("NFD").replaceAll(/\p{M}/gu, "").toLowerCase()

const partsFor = (c: Country): string[] =>
  [
    // allow searching by nationality words too, to help migrations
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
    .map(s => deburrLower(String(s)!))

// Fast cca2 validation
const cca2Map = new Map<string, Country>(countries.map(c => [c.cca2.toUpperCase(), c]))
const isValidCca2 = (v?: string | null) => !!v && v.length === 2 && cca2Map.has(v.toUpperCase())

// Same matcher priority as your Nationality select
const findTopMatch = (query: string): Country | null => {
  const q = deburrLower(query.trim())
  if (!q) return null
  const items = countryItems.value

  // 1) exact (codes, names, demonyms, alt spellings)
  const exact = items.find(c =>
    [c.cca2, c.cca3, c.cioc].filter(Boolean).some(code => deburrLower(String(code)) === q) ||
    partsFor(c).includes(q),
  )
  if (exact) return exact

  // 2) startsWith
  const starts = items.find(c => partsFor(c).some(p => p.startsWith(q)))
  if (starts) return starts

  // 3) includes
  const includes = items.find(c => partsFor(c).some(p => p.includes(q)))
  return includes ?? null
}

// Vuetify filter (diacritic-insensitive)
const customFilter = (_itemText: string, queryText: string, item: InternalItem<Country>) => {
  const c = item.raw
  const q = deburrLower(queryText.trim())
  if (!q) return true
  return partsFor(c).some(p => p.includes(q))
}

// === Normalization for migration ===
// Accepts either cca2 or free text (e.g. "Netherlands", "Dutch").
const normalizeIncomingValue = (incoming: string | null | undefined) => {
  if (!incoming || !incoming.trim()) return

  // Already a cca2? select as-is and reflect text
  if (isValidCca2(incoming)) {
    const code = incoming.toUpperCase()
    if (selectedCountry.value !== code) {
      selectedCountry.value = code
      const c = cca2Map.get(code)
      if (c) searchText.value = displayName(c)
    }
    return
  }

  // Not cca2 -> treat as query, pick best match
  searchText.value = incoming
  const match = findTopMatch(incoming)
  if (match && selectedCountry.value !== match.cca2) {
    selectedCountry.value = match.cca2
  }
}

watch(selectedCountry, (newVal) => {
  emit("update:modelValue", newVal ?? null)
  if (!newVal) searchText.value = ""
})

watch(
  () => props.modelValue,
  (newVal) => {
    if (newVal !== selectedCountry.value) {
      normalizeIncomingValue(newVal ?? null)
    }
  },
  {immediate: true},
)

// Ensure we also normalize on mount for any late-propagated values
onMounted(() => {
  normalizeIncomingValue(props.modelValue ?? null)
})
</script>
