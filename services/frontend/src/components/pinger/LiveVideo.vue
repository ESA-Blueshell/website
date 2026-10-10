<script lang="ts">
export type StreamStatus = "connecting" | "live" | "offline"

export const STREAM_BADGES: Record<StreamStatus, string> = {connecting: "Connecting", live: "Live", offline: "Stream offline"}
</script>

<script lang="ts" setup>
import {onBeforeUnmount, onMounted, ref} from "vue"

/**
 * The event's HLS livestream of the canvas as a bare video, with whether it plays held in the model.
 * hls.js, loaded on demand, plays the feed; the browser plays it itself only where hls.js cannot run.
 */
defineOptions({name: "PingerLiveVideo"})

const props = defineProps<{streamUrl: string}>()
const status = defineModel<StreamStatus>({default: "connecting"})

const video = ref<HTMLVideoElement | null>(null)

const follow = (next: StreamStatus): void => {
  if (status.value !== "offline") status.value = next
}

let destroy: (() => void) | null = null

onMounted(async () => {
  const el = video.value
  if (!el) return
  // hls.js first: desktop Chrome answers "maybe" for native HLS and then refuses the stream, so the
  // browser's own player is only the fallback, for iOS Safari where Media Source is missing.
  const {default: Hls} = await import("hls.js")
  if (!Hls.isSupported()) {
    if (el.canPlayType("application/vnd.apple.mpegurl")) el.src = props.streamUrl
    else status.value = "offline"
    return
  }
  const hls = new Hls({liveSyncDurationCount: 2})
  hls.on(Hls.Events.ERROR, (_event, data) => {
    if (data.fatal) status.value = "offline"
  })
  hls.loadSource(props.streamUrl)
  hls.attachMedia(el)
  destroy = () => hls.destroy()
})

onBeforeUnmount(() => destroy?.())
</script>

<template>
  <video
    ref="video"
    autoplay
    muted
    playsinline
    @playing="follow('live')"
    @waiting="follow('connecting')"
  />
</template>
