<script lang="ts" setup>
/**
 * The channels a game is played in, picked from the server's games category, or from its esports
 * category for the game's competition. Each chosen channel
 * is kept with its name, so while the bot is away the field still says where the game lives, and
 * says it cannot change them.
 */
import {computed, onMounted, ref} from "vue"
import ChipPicker from "@/components/island/ChipPicker.vue"
import FormField from "@/components/island/FormField.vue"
import {GameChannelCategory, type GameRoom, listGameRooms} from "../index"

const props = withDefaults(defineProps<{
  modelValue?: GameRoom[] | null
  testid?: string
  /** What the field is called: a game has Games channels and Esports channels on Discord. */
  label?: string
  /** What the picker says once every channel of its category is chosen. */
  emptyNote?: string
  category?: GameChannelCategory
}>(), {
  modelValue: () => [],
  testid: "game-channels",
  label: "Channels",
  emptyNote: "The games category has no channels left to add.",
  category: GameChannelCategory.GAMES,
})

const emit = defineEmits<{"update:modelValue": [channels: GameRoom[]]}>()

const channels = ref<GameRoom[] | null>(null)
const loaded = ref(false)
onMounted(async () => {
  channels.value = await listGameRooms(props.category)
  loaded.value = true
})

const chosen = computed<GameRoom[]>(() => props.modelValue ?? [])
const unavailable = computed<boolean>(() => loaded.value && channels.value === null)

const options = computed(() => (channels.value ?? []).map(channel => ({key: channel.id, label: channel.name})))
const chips = computed(() => chosen.value.map(channel => ({key: channel.id, label: channel.name})))

const add = (ids: string[]) => {
  const picked = ids
    .map(id => channels.value?.find(one => one.id === id))
    .filter(channel => channel !== undefined)
    .map(channel => ({id: channel.id, guildId: channel.guildId, name: channel.name}))
  if (picked.length > 0) emit("update:modelValue", [...chosen.value, ...picked])
}

const remove = (id: string) => emit("update:modelValue", chosen.value.filter(one => one.id !== id))
</script>

<template>
  <form-field
    :hint="unavailable ? 'The Discord channel list is unavailable right now, so these cannot change.' : undefined"
    :label="label"
    :testid="testid"
  >
    <template #default="{controlId, labelId}">
      <chip-picker
        :chip-testid="(id: string) => `${testid}-${id}`"
        :chosen="chips"
        :control-id="controlId"
        :disabled="!loaded || unavailable"
        :empty-note="emptyNote"
        :labelled-by="labelId"
        :options="options"
        placeholder="Add a channel"
        :remove-label="(name: string) => `Take away ${name}`"
        sigil="#"
        :testid-prefix="`${testid}-picker`"
        @add="add"
        @remove="remove"
      />
    </template>
  </form-field>
</template>
