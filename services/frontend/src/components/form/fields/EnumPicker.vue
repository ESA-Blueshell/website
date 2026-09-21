<script setup lang="ts">
/** One of a fixed set of values, said the way a person would rather than the way the api does. */
import {computed} from "vue"
import {firstSaid} from "@/components/form/fields/saidWrong"
import IslandField from "@/components/island/IslandField.vue"
import IslandPicker from "@/components/island/IslandPicker.vue"

const {
  modelValue = undefined,
  values,
  label = "Value",
  required = false,
  disabled = false,
  errorMessages = undefined,
  testid = undefined,
} = defineProps<{
  modelValue?: string | undefined
  values: string[]
  label?: string
  required?: boolean
  disabled?: boolean
  /** What the api or a rule found wrong, in the shape every other field is handed it. */
  errorMessages?: string | string[]
  testid?: string
}>()

const said = computed<string>(() => firstSaid(errorMessages))

const emit = defineEmits<{"update:modelValue": [value: string | undefined]}>()

/** Turn `CONTRIBUTION_PAID` into `Contribution paid` for display. */
const humanize = (value: string): string =>
  value
    .replace(/[._-]+/g, " ")
    .trim()
    .split(/\s+/)
    .filter(Boolean)
    .map((token, idx) =>
      idx === 0
        ? token.charAt(0).toUpperCase() + token.slice(1).toLowerCase()
        : token.toLowerCase(),
    )
    .join(" ")

const options = computed(() => values.map(value => ({
  key: value,
  label: humanize(value),
  // The api's own spelling, so somebody who knows the value finds the row by typing it.
  terms: [value],
})))
</script>

<template>
  <island-field
    :error="said"
    :filled="modelValue != null"
    :label="label"
    :required="required"
    :testid="testid"
    variant="inside"
  >
    <island-picker
      :disabled="disabled"
      :options="options"
      :selected-key="modelValue ?? null"
      :testid-prefix="testid ?? 'enum-picker'"
      @pick="emit('update:modelValue', $event)"
    />
  </island-field>
</template>
