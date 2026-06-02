<script setup lang="ts">
import {onMounted, ref} from "vue"
import {$handleNetworkError} from "@/plugins/handleNetworkError"
import {findContributionPeriods, type ContributionPeriodResponse} from "@/services/api"

defineProps<{
  modelValue?: number | undefined;
  label?: string;
  required?: boolean;
}>()
defineEmits<{ "update:modelValue": [value: number | undefined] }>()

const items = ref<ContributionPeriodResponse[]>([])
const loading = ref(true)

onMounted(async () => {
  try {
    const resp = await findContributionPeriods()
    if (resp.status === 200 && Array.isArray(resp.data)) {
      items.value = (resp.data as ContributionPeriodResponse[])
        .slice()
        .sort((a, b) => (b.startDate ?? "").localeCompare(a.startDate ?? ""))
    }
  } catch (error) {
    $handleNetworkError(error)
  } finally {
    loading.value = false
  }
})

const itemTitle = (p: ContributionPeriodResponse): string => {
  if (!p) return ""
  const startYear = p.startDate ? new Date(p.startDate).getFullYear() : ""
  const endYear = p.endDate ? new Date(p.endDate).getFullYear() : ""
  if (startYear && endYear) return `${startYear}–${endYear}`
  return startYear ? `${startYear}` : `Period #${p.id}`
}
</script>

<template>
  <v-autocomplete
    :items="items"
    :loading="loading"
    :item-title="itemTitle"
    :label="label ?? 'Contribution period'"
    :model-value="modelValue"
    :rules="required ? [(v: number | undefined) => v != null || 'Required'] : []"
    auto-select-first
    clearable
    hide-no-data
    item-value="id"
    @update:model-value="$emit('update:modelValue', $event)"
  />
</template>
