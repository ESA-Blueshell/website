<script setup lang="ts">
import {onMounted, ref} from "vue"
import {$handleNetworkError} from "@/plugins/handleNetworkError"
import {findUsers, type UserDetailResponse} from "@/services/api"

defineProps<{
  modelValue?: number | undefined;
  label?: string;
  required?: boolean;
}>()
defineEmits<{ "update:modelValue": [value: number | undefined] }>()

const items = ref<UserDetailResponse[]>([])
const loading = ref(true)

onMounted(async () => {
  try {
    const resp = await findUsers()
    if (resp.status === 200 && Array.isArray(resp.data)) {
      items.value = (resp.data as UserDetailResponse[]).slice().sort((a, b) => {
        const left = a.fullName ?? a.email ?? ""
        const right = b.fullName ?? b.email ?? ""
        return left.localeCompare(right)
      })
    }
  } catch (error) {
    $handleNetworkError(error)
  } finally {
    loading.value = false
  }
})

const itemTitle = (u: UserDetailResponse): string => {
  if (!u) return ""
  const name = u.fullName ?? u.email ?? `User #${u.id}`
  return u.email ? `${name} — ${u.email}` : name
}
</script>

<template>
  <v-autocomplete
    :items="items"
    :loading="loading"
    :item-title="itemTitle"
    :label="label ?? 'User'"
    :model-value="modelValue"
    :rules="required ? [(v: number | undefined) => v != null || 'Required'] : []"
    auto-select-first
    clearable
    hide-no-data
    item-value="id"
    @update:model-value="$emit('update:modelValue', $event)"
  />
</template>
