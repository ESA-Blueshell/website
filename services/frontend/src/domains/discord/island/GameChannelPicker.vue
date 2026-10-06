<script lang="ts" setup>
/**
 * The channels a game is played in. The server's games category is offered first, or its esports
 * category for the game's competition, and after it every other text channel under the category
 * it is filed in, since a game's channel can live anywhere. Each chosen channel is kept with its
 * name, so while the bot is away the field still says where the game lives, and says it cannot
 * change them.
 */
import {computed, onMounted, ref} from "vue"
import ChipPicker from "@/components/island/ChipPicker.vue"
import ChannelMark from "./ChannelMark.vue"
import CutButton from "@/components/island/CutButton.vue"
import FormField from "@/components/island/FormField.vue"
import store from "@/plugins/store"
import {makeGameChannel} from "../adapters/channelAccess"
import {type FiledRoom, GameChannelCategory, type GameRoom, listEveryRoom, listGameRooms} from "../index"

const props = withDefaults(defineProps<{
  modelValue?: GameRoom[] | null
  testid?: string
  /** What the field is called: a game has Games channels and Esports channels on Discord. */
  label?: string
  /** What the picker says once every channel of its category is chosen. */
  emptyNote?: string
  category?: GameChannelCategory
  /** The name a channel the site makes takes; none offers no making. */
  createName?: string
}>(), {
  modelValue: () => [],
  testid: "game-channels",
  label: "Channels",
  emptyNote: "The games category has no channels left to add.",
  category: GameChannelCategory.GAMES,
  createName: "",
})

const emit = defineEmits<{"update:modelValue": [channels: GameRoom[]]}>()

const channels = ref<GameRoom[] | null>(null)
const elsewhere = ref<FiledRoom[]>([])
const loaded = ref(false)
onMounted(async () => {
  const [own, every] = await Promise.all([listGameRooms(props.category), listEveryRoom()])
  channels.value = own
  const offered = new Set((own ?? []).map(one => one.id))
  elsewhere.value = (every ?? []).filter(one => !offered.has(one.id))
  loaded.value = true
})

const chosen = computed<GameRoom[]>(() => props.modelValue ?? [])
const unavailable = computed<boolean>(() => loaded.value && channels.value === null)

const options = computed(() => [
  ...(channels.value ?? []).map(channel => ({key: channel.id, label: channel.name})),
  ...elsewhere.value.map(channel => ({key: channel.id, label: channel.name, note: channel.category ?? "No category"})),
])
const chips = computed(() => chosen.value.map(channel => ({key: channel.id, label: channel.name})))

const add = (ids: string[]) => {
  const picked = ids
    .map(id => channels.value?.find(one => one.id === id) ?? elsewhere.value.find(one => one.id === id))
    .filter(channel => channel !== undefined)
    .map(channel => ({id: channel.id, guildId: channel.guildId, name: channel.name}))
  if (picked.length > 0) emit("update:modelValue", [...chosen.value, ...picked])
}

const remove = (id: string) => emit("update:modelValue", chosen.value.filter(one => one.id !== id))

const making = ref(false)
const make = async () => {
  if (making.value || props.createName.trim() === "") return
  making.value = true
  const made = await makeGameChannel(props.createName.trim(), props.category)
  making.value = false
  if (!made.ok) return store.commit("setStatusSnackbarMessage", made.reason)
  channels.value = [...(channels.value ?? []), made.saved]
  emit("update:modelValue", [...chosen.value, made.saved])
}
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
        :remove-label="(name: string) => `Remove ${name}`"
        sigil="#"
        :testid-prefix="`${testid}-picker`"
        @add="add"
        @remove="remove"
      >
        <template #chip="{option}">
          <channel-mark :name="option.label" />
        </template>
        <template #option="{option}">
          <channel-mark :name="option.label" />
        </template>
      </chip-picker>
    </template>
  </form-field>
  <div
    v-if="loaded && !unavailable && createName.trim() !== ''"
    class="game-channels__make"
  >
    <cut-button
      :disabled="making"
      :testid="`${testid}-make`"
      @click="make"
    >
      Create a new channel #{{ createName.trim() }}
    </cut-button>
  </div>
</template>

<style scoped>
.game-channels__make {
  margin-top: 0.4rem;
}
</style>
