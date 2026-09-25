<script lang="ts" setup>
/**
 * The games an event names, picked from the games played. An archived game the event already
 * names stays named and can be taken away, but is not offered; a removed one is dropped.
 */
import {computed} from "vue"
import ChipPicker from "@/components/island/ChipPicker.vue"
import FormField from "@/components/island/FormField.vue"
import {useCasualGames} from "../useCasualGames"

const props = withDefaults(defineProps<{
  modelValue?: string[] | null
  testid?: string
  label?: string
}>(), {
  modelValue: () => [],
  testid: "event-games",
  label: "Games",
})

const emit = defineEmits<{"update:modelValue": [codes: string[]]}>()

const {games, live} = useCasualGames()

const chosen = computed(() => (props.modelValue ?? [])
  .map(code => games.value.find(game => game.code === code))
  .filter(game => game !== undefined))

const options = computed(() => live.value.map(game => ({key: game.code, label: game.name})))
const chips = computed(() => chosen.value.map(game => ({key: game.code, label: game.name})))

const add = (codes: string[]) => emit("update:modelValue", [...chosen.value.map(game => game.code), ...codes])
const remove = (code: string) => emit("update:modelValue", chosen.value.map(game => game.code).filter(one => one !== code))
</script>

<template>
  <form-field
    :label="label"
    :testid="testid"
  >
    <template #default="{controlId, labelId}">
      <chip-picker
        :chip-testid="(code: string) => `${testid}-${code}`"
        :chosen="chips"
        :control-id="controlId"
        empty-note="Every game played is already named."
        :labelled-by="labelId"
        :options="options"
        placeholder="Add a game"
        :remove-label="(name: string) => `Stop naming ${name}`"
        :testid-prefix="`${testid}-picker`"
        @add="add"
        @remove="remove"
      />
    </template>
  </form-field>
</template>
