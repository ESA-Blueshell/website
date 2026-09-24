<script lang="ts" setup>
/**
 * The games an event names, picked from the games played. An archived game the event already
 * names stays named and can be taken away, but is not offered; a removed one is dropped.
 */
import {computed} from "vue"
import FormField from "@/components/island/FormField.vue"
import SearchPicker from "@/components/island/SearchPicker.vue"
import {useCasualGames} from "../useCasualGames"

const props = withDefaults(defineProps<{
  modelValue?: string[] | null
  testid?: string
}>(), {
  modelValue: () => [],
  testid: "event-games",
})

const emit = defineEmits<{"update:modelValue": [codes: string[]]}>()

const {games, live} = useCasualGames()

const chosen = computed(() => (props.modelValue ?? [])
  .map(code => games.value.find(game => game.code === code))
  .filter(game => game !== undefined))

const options = computed(() => live.value
  .filter(game => !chosen.value.some(one => one.code === game.code))
  .map(game => ({key: game.code, label: game.name})))

const add = (code: string) => emit("update:modelValue", [...chosen.value.map(game => game.code), code])

const remove = (code: string) => emit("update:modelValue", chosen.value.map(game => game.code).filter(one => one !== code))
</script>

<template>
  <form-field
    label="Games"
    :testid="testid"
  >
    <template #default="{controlId, labelId}">
      <search-picker
        :control-id="controlId"
        empty-note="Every game played is already named."
        :labelled-by="labelId"
        :options="options"
        placeholder="Add a game"
        :testid-prefix="`${testid}-picker`"
        @pick="add"
      />
      <ul
        v-if="chosen.length > 0"
        class="event-games"
      >
        <li
          v-for="game in chosen"
          :key="game.code"
          class="event-games__game"
          :data-testid="`${testid}-${game.code}`"
        >
          {{ game.name }}
          <button
            :aria-label="`Stop naming ${game.name}`"
            class="event-games__remove"
            type="button"
            @click="remove(game.code)"
          >
            ×
          </button>
        </li>
      </ul>
    </template>
  </form-field>
</template>

<style scoped>
.event-games {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem;
  margin: 0.5rem 0 0;
  padding: 0;
  list-style: none;
}

.event-games__game {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  padding: 0.2rem 0.5rem;
  border-radius: 999px;
  background-color: color-mix(in oklab, var(--color-brand) 18%, transparent);
  font-size: 0.85rem;
}

.event-games__remove {
  border: 0;
  background: none;
  color: inherit;
  cursor: pointer;
  font-size: 1rem;
  line-height: 1;
}
</style>
