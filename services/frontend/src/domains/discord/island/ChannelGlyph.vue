<script lang="ts" setup>
/* Discord's own mark for a channel: text or voice, open or private. Drawn as a mask, so it takes
   the colour of the text beside it. */
import {computed} from "vue"
import textGlyph from "@/assets/discord/text.webp"
import textLockedGlyph from "@/assets/discord/text-locked.webp"
import voiceGlyph from "@/assets/discord/voice.webp"
import voiceLockedGlyph from "@/assets/discord/voice-locked.webp"

defineOptions({name: "ChannelGlyph"})

const {voice = false, locked = false} = defineProps<{
  voice?: boolean
  /** Private: only the roles it is opened to get in. */
  locked?: boolean
}>()

const said = computed(() => `${locked ? "Private" : "Public"} ${voice ? "voice" : "text"} channel`)
const mask = computed(() => {
  const url = `url(${voice ? (locked ? voiceLockedGlyph : voiceGlyph) : locked ? textLockedGlyph : textGlyph})`
  return {maskImage: url, WebkitMaskImage: url}
})
</script>

<template>
  <span
    :aria-label="said"
    class="channel-glyph"
    role="img"
    :style="mask"
    :title="said"
  />
</template>

<style scoped>
.channel-glyph {
  display: inline-block;
  flex: none;
  width: 1.05em;
  height: 1.05em;
  vertical-align: -0.15em;
  background-color: currentcolor;
  mask-position: center;
  mask-repeat: no-repeat;
  mask-size: contain;
}
</style>
