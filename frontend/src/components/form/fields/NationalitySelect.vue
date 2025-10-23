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

const props = defineProps<{ modelValue?: string | null; label?: string }>()
const emit = defineEmits<{ "update:modelValue": [value: string | null] }>()

const selectedCountry = ref<string | null>(props.modelValue ?? null)
const searchText = ref<string>("")

const countryItems = computed<Country[]>(() =>
  [...countries].filter((c: Country) => c.flag).sort((a, b) => {
    const ta = (a.demonyms?.eng?.m || a.name.common).toLowerCase()
    const tb = (b.demonyms?.eng?.m || b.name.common).toLowerCase()
    return ta.localeCompare(tb)
  }),
)

const displayName = (c: Country) => c.flag + " " + (c.demonyms?.eng?.m || c.name?.common || c.name?.official)
const deburrLower = (s: string) => s.normalize("NFD").replaceAll(/\p{M}/gu, "").toLowerCase()
const partsFor = (c: Country): string[] =>
  [c.demonyms?.eng?.m, c.demonyms?.eng?.f, c.name?.common, c.name?.official, c.cca2, c.cca3, c.cioc, ...(c.altSpellings ?? [])]
    .filter(Boolean)
    .map(s => deburrLower(String(s)!))

const cca2Map = new Map<string, Country>(countries.map(c => [c.cca2.toUpperCase(), c]))
const isValidCca2 = (v?: string | null) => !!v && v.length === 2 && cca2Map.has(v.toUpperCase())

const findTopMatch = (query: string): Country | null => {
  const q = deburrLower(query.trim())
  if (!q) return null
  const items = countryItems.value
  const exact = items.find(c =>
    [c.cca2, c.cca3, c.cioc].filter(Boolean).some(code => deburrLower(String(code)) === q) || partsFor(c).includes(q),
  )
  if (exact) return exact
  const starts = items.find(c => partsFor(c).some(p => p.startsWith(q)))
  if (starts) return starts
  return items.find(c => partsFor(c).some(p => p.includes(q))) ?? null
}

const customFilter = (_itemText: string, queryText: string, item: InternalItem<Country>) => {
  const c = item.raw
  const q = deburrLower(queryText.trim())
  if (!q) return true
  return partsFor(c).some(p => p.includes(q))
}

const normalizeIncomingValue = (incoming: string | null | undefined) => {
  if (!incoming || !incoming.trim()) return
  if (isValidCca2(incoming)) {
    const code = incoming.toUpperCase()
    if (selectedCountry.value !== code) {
      selectedCountry.value = code
      const c = cca2Map.get(code)
      if (c) searchText.value = displayName(c)
    }
    return
  }
  searchText.value = incoming
  const match = findTopMatch(incoming)
  if (match && selectedCountry.value !== match.cca2) {
    selectedCountry.value = match.cca2
  }
}

watch(selectedCountry, (newVal) => emit("update:modelValue", newVal ?? null))
watch(() => props.modelValue, (newVal) => {
  if (newVal !== selectedCountry.value) normalizeIncomingValue(newVal ?? null)
}, {immediate: true})
onMounted(() => normalizeIncomingValue(props.modelValue ?? null))
</script>
