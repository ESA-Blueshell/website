<script lang="ts" setup>
/* One list, two readings, and the cca2 code the api stores either way. */
import {computed, onMounted} from "vue"
import CountryFlag from "@/components/island/CountryFlag.vue"
import SearchPicker from "@/components/island/SearchPicker.vue"
import {allCountriesSorted, cca2Map, countriesWithFlagSorted, partsFor} from "@/composables/countries"

defineOptions({name: "CountryPicker"})

const {
  reading = "country",
  placeholder = "",
  testidPrefix,
  disabled = false,
  controlId = undefined,
  labelledBy = undefined,
} = defineProps<{
  /** What a row says: the country's name, or what somebody from it is called. */
  reading?: "country" | "nationality"
  placeholder?: string
  testidPrefix: string
  disabled?: boolean
  controlId?: string
  labelledBy?: string
}>()

const picked = defineModel<string | null>({default: null})

/* Most of the people filling these forms are here, so the field opens on the Netherlands rather
   than on nothing. It is answered rather than assumed: the value is written down. */
onMounted(() => {
  if (picked.value == null || picked.value === "") picked.value = "NL"
})

const say = (code: string): string => {
  const country = cca2Map.get(code.toUpperCase())
  if (!country) return code
  return reading === "nationality"
    ? country.demonyms?.eng?.m || country.name.common
    : country.name.common
}

const options = computed(() => {
  const source = reading === "nationality" ? countriesWithFlagSorted : allCountriesSorted
  return source.map(country => ({
    key: country.cca2,
    label: say(country.cca2),
    flag: country.cca2,
    // Every name, demonym, code and spelling, so "dutch", "nl" and "holland" all find NL.
    terms: partsFor(country),
  }))
})

const chosen = computed<string>(() => (picked.value ? say(picked.value) : ""))
</script>

<template>
  <search-picker
    :control-id="controlId"
    :labelled-by="labelledBy"
    :disabled="disabled"
    :options="options"
    :placeholder="chosen || placeholder || (reading === 'nationality' ? 'Search nationalities' : 'Search countries')"
    :selected-key="picked"
    :testid-prefix="testidPrefix"
    @pick="picked = $event"
  >
    <template #lead="{option}">
      <country-flag
        :code="option?.key ?? picked ?? 'NL'"
        :size="25"
      />
    </template>
  </search-picker>
</template>
