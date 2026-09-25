<script lang="ts" setup>
/**
 * The committees that organise events for a game, picked from the committees that run. One that
 * is archived stays picked and can be taken away, but is not offered.
 */
import {computed} from "vue"
import ChipPicker from "@/components/island/ChipPicker.vue"
import FormField from "@/components/island/FormField.vue"
import {useCommittees} from "../useCommittees"

const props = withDefaults(defineProps<{
  modelValue?: number[] | null
  testid?: string
  label?: string
}>(), {
  modelValue: () => [],
  testid: "game-organisers",
  label: "Committees that organise events for it",
})

const emit = defineEmits<{"update:modelValue": [ids: number[]]}>()

const {committees, live} = useCommittees()

const chosen = computed(() => (props.modelValue ?? [])
  .map(id => committees.value.find(committee => committee.id === id))
  .filter(committee => committee !== undefined))

const options = computed(() => live.value.map(committee => ({key: String(committee.id), label: committee.name})))
const chips = computed(() => chosen.value.map(committee => ({key: String(committee.id), label: committee.name})))

const add = (keys: string[]) => emit("update:modelValue", [...chosen.value.map(one => one.id), ...keys.map(Number)])
const remove = (key: string) => emit("update:modelValue", chosen.value.map(one => one.id).filter(one => String(one) !== key))
</script>

<template>
  <form-field
    :label="label"
    :testid="testid"
  >
    <template #default="{controlId, labelId}">
      <chip-picker
        :chip-testid="(id: string) => `${testid}-${id}`"
        :chosen="chips"
        :control-id="controlId"
        empty-note="Every committee is named already."
        :labelled-by="labelId"
        :options="options"
        placeholder="Add a committee"
        :testid-prefix="`${testid}-picker`"
        @add="add"
        @remove="remove"
      />
    </template>
  </form-field>
</template>
