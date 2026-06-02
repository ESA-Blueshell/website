<script setup lang="ts">
import {onMounted, ref} from "vue"
import {$handleNetworkError} from "@/plugins/handleNetworkError"
import {findEvents, type EventResponse} from "@/services/api"

defineProps<{
  modelValue?: number | undefined;
  label?: string;
  required?: boolean;
}>()
defineEmits<{ "update:modelValue": [value: number | undefined] }>()

const items = ref<EventResponse[]>([])
const loading = ref(true)

onMounted(async () => {
  try {
    const resp = await findEvents()
    if (resp.status === 200 && Array.isArray(resp.data)) {
      items.value = (resp.data as EventResponse[])
        .slice()
        .sort((a, b) => (b.startTime ?? "").localeCompare(a.startTime ?? ""))
    }
  } catch (error) {
    $handleNetworkError(error)
  } finally {
    loading.value = false
  }
})

const itemTitle = (e: EventResponse): string => {
  if (!e) return ""
  const dateLabel = e.startTime ? new Date(e.startTime).toLocaleDateString() : ""
  return dateLabel ? `${e.title} — ${dateLabel}` : (e.title ?? `Event #${e.id}`)
}
</script>

<template>
  <v-autocomplete
    :items="items"
    :loading="loading"
    :item-title="itemTitle"
    :label="label ?? 'Event'"
    :model-value="modelValue"
    :rules="required ? [(v: number | undefined) => v != null || 'Required'] : []"
    auto-select-first
    clearable
    hide-no-data
    item-value="id"
    @update:model-value="$emit('update:modelValue', $event)"
  />
</template>
