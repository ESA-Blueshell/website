<script setup lang="ts">
/** One contribution period, said as the years it runs over. */
import {computed, onMounted, ref} from "vue"
import IslandField from "@/components/island/IslandField.vue"
import IslandPicker from "@/components/island/IslandPicker.vue"
import {$handleNetworkError} from "@/plugins/handleNetworkError"
import {type ContributionPeriodResponse, listPeriods} from "@/domains/contribution"

const {
  modelValue = undefined,
  label = "Contribution period",
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

const said = computed<string>(() => {
  const first = Array.isArray(errorMessages) ? errorMessages[0] : errorMessages
  return first ?? ""
})

const emit = defineEmits<{"update:modelValue": [value: number | undefined]}>()

const held = ref<ContributionPeriodResponse[]>([])
const loading = ref(true)

onMounted(async () => {
  try {
    const data = await listPeriods()
    held.value = data.slice()
      .sort((a, b) => (b.startDate ?? "").localeCompare(a.startDate ?? ""))
  } catch (error) {
    $handleNetworkError(error)
  } finally {
    loading.value = false
  }
})

const yearsOf = (period: ContributionPeriodResponse): string => {
  const from = period.startDate ? new Date(period.startDate).getFullYear() : ""
  const until = period.endDate ? new Date(period.endDate).getFullYear() : ""
  if (from && until) return `${from}–${until}`
  return from ? `${from}` : `Period #${period.id}`
}

const options = computed(() => held.value.map(one => ({
  key: String(one.id),
  label: yearsOf(one),
  terms: [one.startDate ?? "", one.endDate ?? ""].filter(said => said !== ""),
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
      empty-note="There are no contribution periods yet."
      :loading="loading"
      :options="options"
      :selected-key="modelValue == null ? null : String(modelValue)"
      :testid-prefix="testid ?? 'period-picker'"
      @pick="emit('update:modelValue', Number($event))"
    />
  </island-field>
</template>
