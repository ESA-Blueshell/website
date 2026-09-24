<script lang="ts" setup>
/**
 * The committees that organise events for a game, picked from the committees that run. One that
 * is archived stays picked and can be taken away, but is not offered.
 */
import {computed} from "vue"
import FormField from "@/components/island/FormField.vue"
import SearchPicker from "@/components/island/SearchPicker.vue"
import {useCommittees} from "../useCommittees"

const props = withDefaults(defineProps<{
  modelValue?: number[] | null
  testid?: string
}>(), {
  modelValue: () => [],
  testid: "game-organisers",
})

const emit = defineEmits<{"update:modelValue": [ids: number[]]}>()

const {committees, live} = useCommittees()

const chosen = computed(() => (props.modelValue ?? [])
  .map(id => committees.value.find(committee => committee.id === id))
  .filter(committee => committee !== undefined))

const options = computed(() => live.value
  .filter(committee => !chosen.value.some(one => one.id === committee.id))
  .map(committee => ({key: String(committee.id), label: committee.name})))

const add = (key: string) => emit("update:modelValue", [...chosen.value.map(one => one.id), Number(key)])

const remove = (id: number) => emit("update:modelValue", chosen.value.map(one => one.id).filter(one => one !== id))
</script>

<template>
  <form-field
    label="Committees that organise events for it"
    :testid="testid"
  >
    <template #default="{controlId, labelId}">
      <search-picker
        :control-id="controlId"
        empty-note="Every committee is named already."
        :labelled-by="labelId"
        :options="options"
        placeholder="Add a committee"
        :testid-prefix="`${testid}-picker`"
        @pick="add"
      />
      <ul
        v-if="chosen.length > 0"
        class="game-organisers"
      >
        <li
          v-for="committee in chosen"
          :key="committee.id"
          class="game-organisers__one"
          :data-testid="`${testid}-${committee.id}`"
        >
          {{ committee.name }}
          <button
            :aria-label="`Take ${committee.name} away`"
            class="game-organisers__remove"
            type="button"
            @click="remove(committee.id)"
          >
            ×
          </button>
        </li>
      </ul>
    </template>
  </form-field>
</template>

<style scoped>
.game-organisers {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem;
  margin: 0.5rem 0 0;
  padding: 0;
  list-style: none;
}

.game-organisers__one {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  padding: 0.2rem 0.5rem;
  border-radius: 999px;
  background-color: color-mix(in oklab, var(--color-brand) 18%, transparent);
  font-size: 0.85rem;
}

.game-organisers__remove {
  border: 0;
  background: none;
  color: inherit;
  cursor: pointer;
  font-size: 1rem;
  line-height: 1;
}
</style>
