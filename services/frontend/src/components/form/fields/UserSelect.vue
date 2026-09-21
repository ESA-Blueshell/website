<script lang="ts" setup>
/**
 * The api does the searching: this holds one page of answers and asks for another when the
 * typing settles, keeping the chosen member in that page.
 */
import {computed, onBeforeUnmount, ref, watch} from "vue"
import IslandField from "@/components/island/IslandField.vue"
import IslandPicker from "@/components/island/IslandPicker.vue"
import {nameOf, termsFor} from "@/components/form/fields/userTerms"
import {searchMemberAccounts} from "@/domains/user"
import type {UserDetailResponse} from "@/domains/user"

const props = withDefaults(defineProps<{
  modelValue?: number | undefined
  users: UserDetailResponse[]
  label?: string
  errorMessages?: string | string[]
  disabled?: boolean
  testid?: string
}>(), {
  modelValue: undefined,
  label: "User name",
  errorMessages: () => [],
  disabled: false,
  testid: undefined,
})

const emit = defineEmits<{"update:modelValue": [value: number | undefined]}>()

/** One page is what a reader reads before typing more; the table is far larger than any list. */
const PAGE = 20
const SETTLE_MS = 250

const held = ref<UserDetailResponse[]>([...props.users])
const picked = ref<UserDetailResponse | undefined>(props.users.find(one => one.id === props.modelValue))
const loading = ref(false)
let settling: ReturnType<typeof setTimeout> | undefined
let latest = 0

/** The chosen member stays in the list whatever the search answered, or the field draws empty. */
const withPicked = (list: UserDetailResponse[]): UserDetailResponse[] => {
  const one = picked.value
  if (!one || list.some(other => other.id === one.id)) return [...list]
  return [one, ...list]
}

watch(() => props.modelValue, (id) => {
  if (!id) {
    picked.value = undefined
    return
  }
  picked.value = held.value.find(one => one.id === id)
    ?? props.users.find(one => one.id === id)
    ?? picked.value
}, {immediate: true})

watch(() => props.users, (list) => {
  held.value = withPicked(list)
  // The list often arrives after this field is mounted, and until it does there is nobody to
  // resolve the value against.
  const id = picked.value?.id ?? props.modelValue
  if (id) picked.value = held.value.find(one => one.id === id) ?? picked.value
})

const ask = async (term: string): Promise<void> => {
  const mine = ++latest
  loading.value = true
  try {
    const found = await searchMemberAccounts(term, PAGE)
    // An older answer must not overwrite a newer one: the reader has typed since.
    if (mine !== latest) return
    held.value = withPicked(found)
  } finally {
    if (mine === latest) loading.value = false
  }
}

const onSearch = (term: string) => {
  if (settling) clearTimeout(settling)
  if (term === "") return
  settling = setTimeout(() => void ask(term), SETTLE_MS)
}

onBeforeUnmount(() => {
  if (settling) clearTimeout(settling)
})

const options = computed(() => held.value.map(one => ({
  key: String(one.id),
  label: one.discord ? `${nameOf(one)} (${one.discord})` : nameOf(one),
  note: one.email,
  terms: termsFor(one),
})))

const error = computed<string>(() => {
  const said = Array.isArray(props.errorMessages) ? props.errorMessages[0] : props.errorMessages
  return said ?? ""
})

const onPick = (key: string) => {
  const id = Number(key)
  picked.value = held.value.find(one => one.id === id)
  emit("update:modelValue", id)
}
</script>

<template>
  <island-field
    :error="error"
    :filled="picked !== undefined"
    :label="label"
    :testid="testid"
    variant="inside"
  >
    <island-picker
      :disabled="disabled"
      empty-note="Nobody to choose from yet."
      :loading="loading"
      :options="options"
      remote
      :selected-key="picked ? String(picked.id) : null"
      :testid-prefix="testid ?? 'user-select'"
      @pick="onPick"
      @search="onSearch"
    />
  </island-field>
</template>
