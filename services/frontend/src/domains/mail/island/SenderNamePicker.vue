<script lang="ts" setup>
/* The name an email goes out under: one the site offers, or any name typed in. The api checks a
   typed name is one line of a fitting length; the picker only offers it. */
import {computed, ref} from "vue"
import SearchPicker from "@/components/island/SearchPicker.vue"

defineOptions({name: "SenderNamePicker"})

const {offered, controlId = undefined, labelledBy = undefined, testidPrefix} = defineProps<{
  /** The names the site offers: the sending address's own first. */
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
  const extra = term && !known.some((one) => one === term) ? [{key: term, label: term, note: "Use this name"}] : []
  return [...known.map((one) => ({key: one, label: one})), ...extra]
})
</script>

<template>
  <search-picker
    :control-id="controlId"
    empty-note="Type a name."
    :labelled-by="labelledBy"
    :options="options"
    placeholder="Search or type a name"
    :selected-key="chosen"
    :testid-prefix="testidPrefix"
    @pick="(key: string) => { chosen = key; typed = '' }"
    @search="(term: string) => typed = term"
  />
</template>
