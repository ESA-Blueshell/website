<script setup lang="ts">
/** One of the cohorts, with the system it lives in and how many are in it. */
import {computed, onMounted, ref} from "vue"
import {firstSaid} from "@/components/form/fields/saidWrong"
import IslandField from "@/components/island/IslandField.vue"
import IslandPicker from "@/components/island/IslandPicker.vue"
import {$handleNetworkError} from "@/plugins/handleNetworkError"
import {fetchCohortOptions, type CohortOption} from "@/domains/cohorts"

const {
  modelValue = undefined,
  label = "Cohort",
  required = false,
  disabled = false,
  errorMessages = undefined,
  testid = undefined,
} = defineProps<{
  modelValue?: number | undefined
  label?: string
  required?: boolean
  disabled?: boolean
  /** What the api or a rule found wrong, in the shape every other field is handed it. */
  errorMessages?: string | string[]
  testid?: string
}>()

const said = computed<string>(() => firstSaid(errorMessages))

const emit = defineEmits<{"update:modelValue": [value: number | undefined]}>()

const held = ref<CohortOption[]>([])
const loading = ref(true)

onMounted(async () => {
  try {
    held.value = await fetchCohortOptions()
  } catch (error) {
    $handleNetworkError(error)
  } finally {
    loading.value = false
  }
})

const options = computed(() => held.value.map(one => ({
  key: String(one.id),
  label: one.label,
  // Example: "BREVO LIST (12)"
  note: `${one.system} ${one.kind} (${one.memberCount})`,
  terms: [one.system, one.kind],
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
      empty-note="There are no cohorts yet."
      :loading="loading"
      :options="options"
      :selected-key="modelValue == null ? null : String(modelValue)"
      :testid-prefix="testid ?? 'cohort-picker'"
      @pick="emit('update:modelValue', Number($event))"
    />
  </island-field>
</template>
