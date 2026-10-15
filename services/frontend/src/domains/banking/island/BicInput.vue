<script lang="ts" setup>
import {BIC_SHAPE, bicGroups, bicSample} from "../bankAccount"
import ShapedInput from "./ShapedInput.vue"

/** A BIC in its shape, with the country of the IBAN beside it already shown in its template. */
defineOptions({name: "BicInput", inheritAttrs: false})

const {country = null} = defineProps<{
  /** The IBAN's country, which a BIC for the same bank carries in its fifth and sixth letters. */
  country?: string | null
}>()

const value = defineModel<string>({default: ""})
</script>

<template>
  <shaped-input
    v-model="value"
    :group-of="bicGroups"
    :sample-of="() => bicSample(country)"
    :shape-of="() => BIC_SHAPE"
    v-bind="$attrs"
  >
    <template #lead>
      BIC
    </template>
  </shaped-input>
</template>
