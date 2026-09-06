<script lang="ts" setup>
import {onBeforeUnmount, ref, watch} from "vue"
import {searchMemberAccounts} from "@/domains/user"
import type {UserDetailResponse} from "@/services/api"
import {VAutocomplete} from "vuetify/components"

type Rule = (v: UserDetailResponse | undefined) => true | string

const props = defineProps<{
  modelValue?: number | undefined
  users: UserDetailResponse[]
  rules?: Rule[]
  label?: string
}>()
const emit = defineEmits<{ "update:modelValue": [value: number | undefined] }>()

/** One page is what a reader reads before typing more; the table is far larger than any list. */
const PAGE = 20
const SETTLE_MS = 250

const selectedUser = ref<UserDetailResponse | undefined>(props.users.find((u) => u.id == props.modelValue))
// Seeded from whatever the page already holds so an existing member renders before anybody types.
const options = ref<UserDetailResponse[]>([...props.users])
const search = ref("")
const loading = ref(false)
const inputRef = ref<InstanceType<typeof VAutocomplete> | null>(null)
let settling: ReturnType<typeof setTimeout> | undefined
let latest = 0

watch(
  () => props.modelValue,
  (val) => {
    if (!val) {
      selectedUser.value = undefined
      return
    }
    selectedUser.value = options.value.find((u) => u.id === val) ?? props.users.find((u) => u.id === val)
  },
  {immediate: true},
)

watch(selectedUser, (val) => emit("update:modelValue", val?.id))

watch(
  () => props.users,
  (list) => {
    options.value = mergeSelected(list)
    // The list often arrives after this field is mounted, and until it does there is nobody to
    // resolve `modelValue` against — so fall back to it, or an already-picked member renders as
    // an empty row for good.
    const id = selectedUser.value?.id ?? props.modelValue
    if (!id) return
    selectedUser.value = options.value.find((u) => u.id === id) ?? selectedUser.value
  },
)

/** The picked user stays in the list whatever the search answered, or the field renders empty. */
function mergeSelected(list: UserDetailResponse[]): UserDetailResponse[] {
  const picked = selectedUser.value
  if (!picked || list.some((u) => u.id === picked.id)) return [...list]
  return [picked, ...list]
}

async function ask(term: string): Promise<void> {
  const mine = ++latest
  loading.value = true
  try {
    const found = await searchMemberAccounts(term, PAGE)
    // An older answer must not overwrite a newer one: the reader has typed since.
    if (mine !== latest) return
    options.value = mergeSelected(found)
  } finally {
    if (mine === latest) loading.value = false
  }
}

watch(search, (term) => {
  const typed = (term ?? "").trim()
  if (settling) clearTimeout(settling)
  // The name of the picked user is what the field shows, so it arrives here as a search the
  // reader did not type. Asking for it again would replace the list with one row.
  if (!typed || typed === selectedUserTitle()) return
  settling = setTimeout(() => void ask(typed), SETTLE_MS)
})

onBeforeUnmount(() => {
  if (settling) clearTimeout(settling)
})

const itemTitle = (u: UserDetailResponse) => (u?.discord ? `${u.fullName} (${u.discord})` : u?.fullName)

function selectedUserTitle(): string | undefined {
  return selectedUser.value ? itemTitle(selectedUser.value) : undefined
}

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
    v-model="selectedUser"
    v-model:search="search"
    :item-title="itemTitle"
    :items="options"
    :label="label ?? 'User name'"
    :loading="loading"
    :rules="rules ?? [(v: UserDetailResponse | undefined) => !!v || 'Select a user']"
    auto-select-first
    clearable
    hide-details="auto"
    hide-no-data
    item-value="id"
    no-filter
    return-object
  />
</template>

<style lang="scss" scoped>
</style>
