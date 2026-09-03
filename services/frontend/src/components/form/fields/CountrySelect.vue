<script lang="ts" setup>
import {computed, onMounted, ref, watch} from "vue"
import type {Country} from "world-countries"
import type {InternalItem} from "vuetify"
import {
  allCountriesSorted,
  cca2Map,
  customFilterForCountry,
  displayCountry,
  findTopMatch,
  isValidCca2,
} from "@/composables/countries"

const props = defineProps<{ modelValue?: string | null; label?: string }>()
const emit = defineEmits<{ "update:modelValue": [value: string | null] }>()

const selectedCountry = ref<string | null>(props.modelValue ?? null)
const searchText = ref<string>("")

const countryItems = computed<Country[]>(() => allCountriesSorted)
const displayName = (c: Country) => displayCountry(c)
const customFilter = (_itemText: string, queryText: string, item: InternalItem<Country>) =>
  customFilterForCountry(_itemText, queryText, item)

const normalizeIncomingValue = (incoming: string | null | undefined) => {
  // Keeping the old selection while the form holds nothing shows a filled field
  // that fails its own required rule, which reads as a validation error nobody
  // can act on.
  if (!incoming || !incoming.trim()) {
    selectedCountry.value = null
    searchText.value = ""
    return
  }
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
  const match = findTopMatch(incoming, countryItems.value)
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
    if (newVal !== selectedCountry.value) normalizeIncomingValue(newVal ?? null)
  },
  {immediate: true},
)

onMounted(() => {
  normalizeIncomingValue(props.modelValue ?? null)
})
</script>

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
