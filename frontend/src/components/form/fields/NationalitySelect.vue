<template>
  <v-autocomplete
    v-model="selectedCountry"
    v-model:search="searchText"
    :custom-filter="customFilter"
    :item-title="displayName"
    :items="countryItems"
    :label="label ?? 'Nationality'"
    clearable
    item-value="cca2"
  />
</template>

<script lang="ts" setup>
import {computed, onMounted, ref, watch} from "vue"
import countries, {type Country} from "world-countries"
import type {InternalItem} from "vuetify"

// Props / emits unchanged: we still pass the 2-letter code (cca2) to parent
const props = defineProps<{ modelValue?: string | null; label?: string }>()
const emit = defineEmits<{ "update:modelValue": [value: string | null] }>()

// === State ===
const selectedCountry = ref<string | null>(props.modelValue ?? null) // holds cca2 (or null)
const searchText = ref<string>("") // bound to the autocomplete's search field

// === Data / helpers ===
const countryItems = computed<Country[]>(() =>
  [...countries].filter((c: Country) => c.flag).sort((a, b) => {
    const ta = (a.demonyms?.eng?.m || a.name.common).toLowerCase()
    const tb = (b.demonyms?.eng?.m || b.name.common).toLowerCase()
    return ta.localeCompare(tb)
  }),
)

const displayName = (c: Country) =>
  c.flag + " " + (c.demonyms?.eng?.m || c.name?.common || c.name?.official)

const deburrLower = (s: string) =>
  s.normalize("NFD").replaceAll(/\p{M}/gu, "").toLowerCase()

const partsFor = (c: Country): string[] =>
  [
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

// Map for quick cca2 validation
const cca2Map = new Map<string, Country>(countries.map(c => [c.cca2.toUpperCase(), c]))

const isValidCca2 = (v?: string | null) =>
  !!v && v.length === 2 && cca2Map.has(v.toUpperCase())

// Same matching spirit as customFilter, but with a priority:
// 1) exact code/common/official/demonym/alt spelling
// 2) startsWith
// 3) includes
const findTopMatch = (query: string): Country | null => {
  const q = deburrLower(query.trim())
  if (!q) return null
  const items = countryItems.value

  const exact = items.find(c =>
    [c.cca2, c.cca3, c.cioc].filter(Boolean).some(code => deburrLower(String(code)) === q) ||
    partsFor(c).includes(q),
  )
  if (exact) return exact

  const starts = items.find(c => partsFor(c).some(p => p.startsWith(q)))
  if (starts) return starts

  const includes = items.find(c => partsFor(c).some(p => p.includes(q)))
  return includes ?? null
}

// Your filter (unchanged in behavior)
const customFilter = (_itemText: string, queryText: string, item: InternalItem<Country>) => {
  const c = item.raw
  const q = deburrLower(queryText.trim())
  if (!q) return true

  return partsFor(c).some(p => p.includes(q))
}

// === Normalization logic ===
// If modelValue is NOT a cca2 code (e.g., it's "Netherlands"),
// put it into the search field, pick the top match, and set selectedCountry to the match's cca2.
const normalizeIncomingValue = (incoming: string | null | undefined) => {
  if (!incoming || !incoming.trim()) return

  // already a valid cca2? just select it
  if (isValidCca2(incoming)) {
    const code = incoming.toUpperCase()
    if (selectedCountry.value !== code) {
      selectedCountry.value = code
      // also reflect the display text in the input (nice UX)
      const c = cca2Map.get(code)
      if (c) searchText.value = displayName(c)
    }
    return
  }

  // not a cca2 -> treat as a query
  searchText.value = incoming
  const match = findTopMatch(incoming)
  if (match) {
    // Selecting the top result will immediately emit its cca2 upwards
    if (selectedCountry.value !== match.cca2) {
      selectedCountry.value = match.cca2
    }
  }
}

// === Wiring ===
watch(selectedCountry, (newVal) => {
  // Emit cca2 (or null) to parent
  emit("update:modelValue", newVal ?? null)
})

watch(
  () => props.modelValue,
  (newVal) => {
    // Keep local state in sync with parent, and normalize if needed
    if (newVal !== selectedCountry.value) {
      normalizeIncomingValue(newVal ?? null)
    }
  },
  {immediate: true},
)

// If you want to force normalization once on mount even when parent
// hasn't propagated yet, you can keep this; it's safe/no-op otherwise.
onMounted(() => {
  normalizeIncomingValue(props.modelValue ?? null)
})
</script>
