<script lang="ts" setup>
/* One dropdown filter: the label sits inside the box and the list is searched as the reader types.
   Nothing chosen reads as "Any" and filters nothing. */
import {computed} from "vue"
import FormField from "@/components/island/FormField.vue"
import SearchPicker from "@/components/island/SearchPicker.vue"

const {label, options, testid, anyLabel = "Any"} = defineProps<{
  label: string
  options: Array<{key: string; label: string}>
  testid: string
  anyLabel?: string
}>()

const chosen = defineModel<string | null>({default: null})

const ANY = "__any__"

const rows = computed(() => [{key: ANY, label: anyLabel}, ...options])

const pick = (key: string) => {
  chosen.value = key === ANY ? null : key
}
</script>

<template>
  <form-field
    class="filter-picker"
    :filled="true"
    :label="label"
    variant="inside"
  >
    <search-picker
      :options="rows"
      :placeholder="rows.find((row) => row.key === (chosen ?? ANY))?.label ?? anyLabel"
      :selected-key="chosen ?? ANY"
      :testid-prefix="testid"
      @pick="pick"
    />
  </form-field>
</template>

<style scoped>
.filter-picker {
  width: 11.5rem;
}

/* A filter never has a message, so the line a field keeps for one would only make it taller
   than the search box beside it. */
.filter-picker :deep(.island-field__said) {
  display: none;
}
</style>
