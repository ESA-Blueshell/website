<script setup lang="ts">
import { ref, watch } from "vue"
import { $handleNetworkError } from "@/plugins/handleNetworkError"
import { findUsers, Role, type UserDetailResponse } from "@/services/api"

const props = defineProps<{
  modelValue?: number | undefined
  label?: string
  required?: boolean
  membersOnly?: boolean
}>()
defineEmits<{ "update:modelValue": [value: number | undefined] }>()

const items = ref<UserDetailResponse[]>([])
const loading = ref(false)
const loaded = ref(false)
const search = ref("")
let debounceTimer: ReturnType<typeof setTimeout> | null = null

async function loadUsers() {
  if (loaded.value || loading.value) return
  loading.value = true
  try {
    // No size: this picker filters what it holds, so it wants the whole listing. The 500 it used
    // to name never bounded anything: the answer was everybody regardless (#1145).
    const resp = await findUsers({})
    const content = resp.data?.content ?? []
    items.value = content.slice().sort((a, b) => {
      const left = a.fullName ?? a.email ?? ""
      const right = b.fullName ?? b.email ?? ""
      return left.localeCompare(right)
    })
    loaded.value = true
  } catch (error) {
    $handleNetworkError(error)
  } finally {
    loading.value = false
  }
}

watch(search, (term) => {
  if (loaded.value || !term || term.length < 1) return
  if (debounceTimer) clearTimeout(debounceTimer)
  debounceTimer = setTimeout(() => void loadUsers(), 300)
})

const itemTitle = (u: UserDetailResponse): string => {
  if (!u) return ""
  const name = u.fullName ?? u.email ?? `User #${u.id}`
  return u.email ? `${name} (${u.email})` : name
}

// The api answers with inherited roles, so a board member carries MEMBER here without holding it.
function isEligible(user: UserDetailResponse): boolean {
  return !props.membersOnly || (user.roles ?? []).includes(Role.MEMBER)
}
</script>

<template>
  <v-autocomplete
    v-model:search="search"
    :items="items"
    :loading="loading"
    :item-title="itemTitle"
    :label="label ?? 'User'"
    :model-value="modelValue"
    :rules="required ? [(v: number | undefined) => v != null || 'Required'] : []"
    :no-filter="false"
    clearable
    item-value="id"
    no-data-text="Type to search users"
    @update:focused="(focused: boolean) => { if (focused) void loadUsers() }"
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <template #item="{ props: itemProps, item }">
      <v-list-item
        v-bind="itemProps"
        :disabled="!isEligible(item.raw)"
        :subtitle="isEligible(item.raw) ? undefined : 'Not a member'"
      />
    </template>
  </v-autocomplete>
</template>
