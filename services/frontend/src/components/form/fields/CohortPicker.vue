<script setup lang="ts">
import {onMounted, ref} from "vue"
import {$handleNetworkError} from "@/plugins/handleNetworkError"
import {fetchCohortOptions, type CohortOption} from "@/domains/cohorts/adapters/cohorts"

defineProps<{
  modelValue?: number | undefined;
  label?: string;
  required?: boolean;
}>()
defineEmits<{ "update:modelValue": [value: number | undefined] }>()

const items = ref<CohortOption[]>([])
const loading = ref(true)

onMounted(async () => {
  try {
    items.value = await fetchCohortOptions()
  } catch (error) {
    $handleNetworkError(error)
  } finally {
    loading.value = false
  }
})

const itemTitle = (c: CohortOption): string => {
  if (!c) return ""
  // Example: "Members — BREVO LIST (12)"
  return `${c.label} — ${c.system} ${c.kind} (${c.memberCount})`
}
</script>

<template>
  <v-autocomplete
    :items="items"
    :loading="loading"
    :item-title="itemTitle"
    :label="label ?? 'Cohort'"
    :model-value="modelValue"
    :rules="required ? [(v: number | undefined) => v != null || 'Required'] : []"
    auto-select-first
    clearable
    hide-no-data
    item-value="id"
    @update:model-value="$emit('update:modelValue', $event)"
  />
</template>
