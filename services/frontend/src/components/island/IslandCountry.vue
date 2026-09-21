<script lang="ts" setup>
/**
 * A country or the nationality that goes with it: one list, two readings, and the cca2 code
 * the api stores either way.
 */
import {computed, onMounted} from "vue"
import CountryFlag from "@/components/island/CountryFlag.vue"
import IslandPicker from "@/components/island/IslandPicker.vue"
import {allCountriesSorted, cca2Map, countriesWithFlagSorted, partsFor} from "@/composables/countries"

defineOptions({name: "IslandCountry"})

const {
  reading = "country",
  placeholder = "",
  testidPrefix,
  disabled = false,
} = defineProps<{
  /** What a row says: the country's name, or what somebody from it is called. */
  reading?: "country" | "nationality"
  placeholder?: string
  testidPrefix: string
  disabled?: boolean
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
    // Both names, both demonyms, every code and every spelling the record carries, so France is
    // found by "french" and the Netherlands by "dutch", "nl" or "holland".
    terms: partsFor(country),
  }))
})

/* What the shut field shows, which is the row's own wording rather than the stored code. */
const chosen = computed<string>(() => (picked.value ? say(picked.value) : ""))
</script>

<template>
  <island-picker
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
  </island-picker>
</template>
