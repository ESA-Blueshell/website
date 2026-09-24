<script lang="ts" setup>
/**
 * The channels a game is played in, picked from the server's games category. Each chosen channel
 * is kept with its name, so while the bot is away the field still says where the game lives, and
 * says it cannot change them.
 */
import {computed, onMounted, ref} from "vue"
import FormField from "@/components/island/FormField.vue"
import SearchPicker from "@/components/island/SearchPicker.vue"
import {type GameRoom, listGameRooms} from "../index"

const props = withDefaults(defineProps<{
  modelValue?: GameRoom[] | null
  testid?: string
}>(), {
  modelValue: () => [],
  testid: "game-channels",
})

const emit = defineEmits<{"update:modelValue": [channels: GameRoom[]]}>()

const channels = ref<GameRoom[] | null>(null)
const loaded = ref(false)
onMounted(async () => {
  channels.value = await listGameRooms()
  loaded.value = true
})

const chosen = computed<GameRoom[]>(() => props.modelValue ?? [])
const unavailable = computed<boolean>(() => loaded.value && channels.value === null)

const options = computed(() => (channels.value ?? [])
  .filter(channel => !chosen.value.some(one => one.id === channel.id))
  .map(channel => ({key: channel.id, label: `#${channel.name}`})))

const add = (id: string) => {
  const channel = channels.value?.find(one => one.id === id)
  if (channel) emit("update:modelValue", [...chosen.value, {id: channel.id, guildId: channel.guildId, name: channel.name}])
}

const remove = (id: string) => emit("update:modelValue", chosen.value.filter(one => one.id !== id))
</script>

<template>
  <form-field
    :hint="unavailable ? 'The Discord channel list is unavailable right now, so these cannot change.' : undefined"
    label="Channels"
    :testid="testid"
  >
    <template #default="{controlId, labelId}">
      <search-picker
        v-if="!unavailable"
        :control-id="controlId"
        :disabled="!loaded"
        empty-note="The games category has no channels left to add."
        :labelled-by="labelId"
        :options="options"
        placeholder="Add a channel"
        :testid-prefix="`${testid}-picker`"
        @pick="add"
      />
      <ul
        v-if="chosen.length > 0"
        class="game-channels"
      >
        <li
          v-for="channel in chosen"
          :key="channel.id"
          class="game-channels__channel"
          :data-testid="`${testid}-${channel.id}`"
        >
          #{{ channel.name }}
          <button
            v-if="!unavailable"
            :aria-label="`Take away #${channel.name}`"
            class="game-channels__remove"
            type="button"
            @click="remove(channel.id)"
          >
            ×
          </button>
        </li>
      </ul>
    </template>
  </form-field>
</template>

<style scoped>
.game-channels {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem;
  margin: 0.5rem 0 0;
  padding: 0;
  list-style: none;
}

.game-channels__channel {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  padding: 0.2rem 0.5rem;
  border-radius: 999px;
  background-color: color-mix(in oklab, var(--color-brand) 18%, transparent);
  font-size: 0.85rem;
}

.game-channels__remove {
  border: 0;
  background: none;
  color: inherit;
  cursor: pointer;
  font-size: 1rem;
  line-height: 1;
}
</style>
