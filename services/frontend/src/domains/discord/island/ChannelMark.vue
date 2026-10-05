<script lang="ts" setup>
/* A channel named the way Discord writes a mention: its glyph and name on Discord's tint, and,
   where the channel is known, a link into it in the Discord app. A category is named plainly, as
   Discord names one. */
import {computed} from "vue"
import ChannelGlyph from "./ChannelGlyph.vue"
import {GUILD_ID} from "../adapters/widget"

defineOptions({name: "ChannelMark"})

const {name, id = undefined, guildId = undefined, voice = false, locked = false, category = false, testid = undefined} = defineProps<{
  name: string
  /** The channel's id; without one the mark names the channel and links nowhere. */
  id?: string
  /** The server it is in, where the api says; the association's own server otherwise. */
  guildId?: string
  voice?: boolean
  locked?: boolean
  category?: boolean
  testid?: string
}>()

const href = computed(() => (id && !category ? `https://discord.com/channels/${guildId ?? GUILD_ID}/${id}` : undefined))
</script>

<template>
  <span
    v-if="category"
    class="channel-mark channel-mark--category"
    :data-testid="testid"
  >{{ name }}</span>
  <component
    :is="href ? 'a' : 'span'"
    v-else
    class="channel-mark"
    :data-testid="testid"
    :href="href"
    :rel="href ? 'noopener' : undefined"
    :target="href ? '_blank' : undefined"
    @click.stop
  >
    <channel-glyph
      class="channel-mark__glyph"
      :locked="locked"
      :voice="voice"
    />{{ name }}
  </component>
</template>

<style scoped>
.channel-mark {
  display: inline-flex;
  align-items: center;
  gap: 0.15em;
  padding: 0 0.3em;
  border-radius: 3px;
  font-weight: 500;
  white-space: nowrap;
  color: #c9cdfb;
  background-color: rgb(88 101 242 / 30%);
}

a.channel-mark:hover {
  color: #ffffff;
  background-color: #5865f2;
}

.channel-mark__glyph {
  width: 0.95em;
  height: 0.95em;
}

/* Discord names a category in small capitals, without a mention's tint. */
.channel-mark--category {
  padding: 0;
  font-size: 0.82em;
  font-weight: 600;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  color: var(--color-ash);
  background: none;
}

:where([data-theme="light"]) .channel-mark {
  color: #4752c4;
  background-color: rgb(88 101 242 / 15%);
}
</style>
