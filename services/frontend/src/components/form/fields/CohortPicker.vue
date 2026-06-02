<script setup lang="ts">
import {onMounted, ref} from "vue"
import {$handleNetworkError} from "@/plugins/handleNetworkError"
import {findCohorts, type CohortSummary} from "@/services/api"

defineProps<{
  modelValue?: number | undefined;
  label?: string;
  required?: boolean;
}>()
defineEmits<{ "update:modelValue": [value: number | undefined] }>()

const items = ref<CohortSummary[]>([])
const loading = ref(true)

onMounted(async () => {
  try {
    const resp = await findCohorts()
    if (resp.status === 200 && Array.isArray(resp.data)) {
      items.value = (resp.data as CohortSummary[]).slice().sort((a, b) => {
        const bySystem = a.system.localeCompare(b.system)
        return bySystem !== 0 ? bySystem : a.label.localeCompare(b.label)
      })
    }
  } catch (error) {
    $handleNetworkError(error)
  } finally {
    loading.value = false
  }
})

const itemTitle = (c: CohortSummary): string => {
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
