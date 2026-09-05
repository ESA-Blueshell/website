<script lang="ts" setup>
import {computed, onBeforeUnmount, ref, watch} from "vue"
import {findUserById, findUsers, type UserDetailResponse} from "@/services/api"
import {VAutocomplete} from "vuetify/components"

type Rule = (v: number | undefined) => true | string

const props = defineProps<{
  modelValue?: number | undefined
  rules?: Rule[]
  label?: string
}>()
const emit = defineEmits<{ "update:modelValue": [value: number | undefined] }>()

/**
 * How many matches one answer shows. Not a limit on who can be picked: a name that is not
 * among them is reached by typing more of it, and the field says so while there are more.
 */
const MATCHES_SHOWN = 20
const SEARCH_DEBOUNCE_MS = 250

const items = ref<UserDetailResponse[]>([])
const loading = ref(false)
const search = ref("")
/** Set while the api holds more matches than this answer shows. */
const moreMatches = ref(false)
const inputRef = ref<InstanceType<typeof VAutocomplete> | null>(null)

/**
 * A fresh member row carries `0`, which is nobody. Passed through as a selection it lands in the
 * field as the text "0" and prefixes whatever gets typed next.
 */
const picked = computed(() => props.modelValue || undefined)

let debounceHandle: ReturnType<typeof setTimeout> | undefined
/** Answers can land out of order, and an older one must not replace a newer one. */
let latestRequest = 0

/**
 * Keeps whoever is picked in the item list. Vuetify renders a selection by finding it among
 * the items, so a search that no longer matches them would otherwise blank the row.
 */
function keepPicked(list: UserDetailResponse[]): UserDetailResponse[] {
  const pickedUser = items.value.find((u) => u.id === picked.value)
  if (!pickedUser || list.some((u) => u.id === pickedUser.id)) return list
  return [pickedUser, ...list]
}

async function runSearch(term: string): Promise<void> {
  const request = ++latestRequest
  loading.value = true
  const res = await findUsers({
    query: {search: term.trim() || undefined, page: 0, size: MATCHES_SHOWN},
  })
  if (request !== latestRequest) return
  loading.value = false
  if (res.error || !res.data) return
  const content = res.data.content ?? []
  moreMatches.value = (res.data.page?.totalElements ?? content.length) > content.length
  items.value = keepPicked(content)
}

watch(search, (term) => {
  clearTimeout(debounceHandle)
  debounceHandle = setTimeout(() => void runSearch(term ?? ""), SEARCH_DEBOUNCE_MS)
})

/**
 * The picked member is read by id rather than looked for in a list: a committee being edited
 * names its members by id only, and no page of users is guaranteed to hold them.
 */
watch(
  picked,
  async (id) => {
    if (!id || items.value.some((u) => u.id === id)) return
    const res = await findUserById({path: {userId: id}})
    if (res.error || !res.data) return
    if (picked.value !== id) return
    items.value = [res.data, ...items.value.filter((u) => u.id !== id)]
  },
  {immediate: true},
)

onBeforeUnmount(() => clearTimeout(debounceHandle))

const itemTitle = (u: UserDetailResponse) => (u?.discord ? `${u.fullName} (${u.discord})` : u?.fullName)

function validate() {
  return inputRef.value?.validate?.()
}

function resetValidation() {
  inputRef.value?.resetValidation?.()
}

function focus() {
  inputRef.value?.focus?.()
}

defineExpose({validate, resetValidation, focus})
</script>

<template>
  <v-autocomplete
    ref="inputRef"
    v-model:search="search"
    :hint="moreMatches ? 'More members match — keep typing to narrow it down' : undefined"
    :item-title="itemTitle"
    :items="items"
    :label="label ?? 'User name'"
    :loading="loading"
    :model-value="picked"
    :rules="rules ?? [(v: number | undefined) => !!v || 'Select a user']"
    auto-select-first
    clearable
    hide-details="auto"
    item-value="id"
    no-data-text="Type a name to find a member"
    no-filter
    persistent-hint
    @update:focused="(focused: boolean) => { if (focused && items.length === 0) void runSearch(search) }"
    @update:model-value="(value: number | undefined) => emit('update:modelValue', value ?? undefined)"
  />
</template>

<style lang="scss" scoped>
</style>
