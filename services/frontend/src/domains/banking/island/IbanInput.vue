<script lang="ts" setup>
import {computed} from "vue"
import CountryFlag from "@/components/island/CountryFlag.vue"
import {ibanCountry, ibanSample, ibanShape, ibanShapeFor, inFours} from "../bankAccount"
import ShapedInput from "./ShapedInput.vue"

/**
 * An IBAN in its country's shape, its flag at the left once the first two letters are in. One field
 * rather than a box per character, as banks and payment forms do: an IBAN is 15 to 34 characters
 * long and is usually pasted whole.
 */
defineOptions({name: "IbanInput", inheritAttrs: false})

const value = defineModel<string>({default: ""})
const country = computed(() => ibanCountry(value.value))
const known = computed(() => country.value != null && ibanShape(country.value) != null)
</script>

<template>
  <shaped-input
    v-model="value"
    :group-of="inFours"
    :sample-of="ibanSample"
    :shape-of="ibanShapeFor"
    v-bind="$attrs"
  >
    <template #lead>
      <country-flag
        v-if="known"
        :code="country!"
        :size="25"
      />
      <template v-else>
        IBAN
      </template>
    </template>
  </shaped-input>
</template>
