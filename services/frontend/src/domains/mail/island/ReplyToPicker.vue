<script lang="ts" setup>
/* Where replies go: one of the addresses the api offers, or any address typed in. The api checks a
   typed address is one; the picker only offers it. */
import {computed, ref} from "vue"
import SearchPicker from "@/components/island/SearchPicker.vue"

defineOptions({name: "ReplyToPicker"})

const {offered, controlId = undefined, labelledBy = undefined, testidPrefix} = defineProps<{
  /** The addresses the api offers. */
  offered: string[]
  controlId?: string
  labelledBy?: string
  testidPrefix: string
}>()

const chosen = defineModel<string | null>({default: null})
const typed = ref("")

const options = computed(() => {
  const known = [...new Set([...offered, ...(chosen.value ? [chosen.value] : [])])]
  const term = typed.value.trim()
  const extra = term && !known.some((one) => one.toLowerCase() === term.toLowerCase())
    ? [{key: term, label: term, note: "Use this address"}]
    : []
  return [...known.map((one) => ({key: one, label: one})), ...extra]
})
</script>

<template>
  <search-picker
    :control-id="controlId"
    empty-note="Type an address."
    :labelled-by="labelledBy"
    :options="options"
    placeholder="Search or type an address"
    :selected-key="chosen"
    :testid-prefix="testidPrefix"
    @pick="(key: string) => { chosen = key; typed = '' }"
    @search="(term: string) => typed = term"
  />
</template>
