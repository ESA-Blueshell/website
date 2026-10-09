<script lang="ts" setup>
import {computed, onBeforeUnmount, onMounted, ref} from "vue"
import {apiUrl, type Placement} from "@/domains/pinger"

/**
 * The event's livestream of the canvas with our placements drawn over it in their boxes, so what we
 * paint can be checked against what the canvas shows. Safari plays the HLS feed itself; elsewhere
 * hls.js is loaded on demand.
 */
defineOptions({name: "PingerLiveCanvas"})

const CANVAS_W = 3840
const CANVAS_H = 2160

const props = defineProps<{
  placements: Placement[]
  streamUrl: string
}>()

type Overlay = "ghost" | "outline" | "off"
const overlays: {mode: Overlay, label: string}[] = [
  {mode: "ghost", label: "Ghost"},
  {mode: "outline", label: "Outline"},
  {mode: "off", label: "Off"},
]
const overlay = ref<Overlay>("ghost")

const video = ref<HTMLVideoElement | null>(null)
const playing = ref<boolean>(false)
const failed = ref<boolean>(false)

const boxes = computed(() => props.placements.map(p => ({
  id: p.id,
  src: apiUrl(p.imageUrl),
  style: {
    left: `${(p.originX / CANVAS_W) * 100}%`,
    top: `${(p.originY / CANVAS_H) * 100}%`,
    width: `${(p.width / CANVAS_W) * 100}%`,
    height: `${(p.height / CANVAS_H) * 100}%`,
  },
})))

let destroy: (() => void) | null = null

onMounted(async () => {
  const el = video.value
  if (!el) return
  if (el.canPlayType("application/vnd.apple.mpegurl")) {
    el.src = props.streamUrl
    return
  }
  const {default: Hls} = await import("hls.js")
  if (!Hls.isSupported()) {
    failed.value = true
    return
  }
  const hls = new Hls({liveSyncDurationCount: 2})
  hls.on(Hls.Events.ERROR, (_event, data) => {
    if (data.fatal) failed.value = true
  })
  hls.loadSource(props.streamUrl)
  hls.attachMedia(el)
  destroy = () => hls.destroy()
})

onBeforeUnmount(() => destroy?.())
</script>

<template>
  <div
    class="live"
    data-testid="snt-live"
  >
    <div class="live__plate">
      <video
        ref="video"
        autoplay
        class="live__video"
        muted
        playsinline
        @playing="playing = true"
        @waiting="playing = false"
      />
      <div
        v-if="overlay !== 'off'"
        class="live__overlay"
        :class="`live__overlay--${overlay}`"
      >
        <div
          v-for="box in boxes"
          :key="box.id"
          class="live__box"
          data-testid="snt-live-box"
          :style="box.style"
        >
          <img
            v-if="overlay === 'ghost'"
            alt=""
            class="live__img"
            :src="box.src"
          >
        </div>
      </div>
      <span
        class="live__badge"
        :class="{'live__badge--on': playing}"
      >{{ failed ? "Stream offline" : playing ? "Live" : "Connecting" }}</span>
    </div>

    <div
      class="live__modes"
      role="group"
      aria-label="Our art over the stream"
    >
      <span class="live__modes-label">Our art</span>
      <button
        v-for="o in overlays"
        :key="o.mode"
        :aria-pressed="overlay === o.mode"
        class="live__mode"
        :class="{'live__mode--on': overlay === o.mode}"
        :data-testid="`snt-live-${o.mode}`"
        type="button"
        @click="overlay = o.mode"
      >
        {{ o.label }}
      </button>
    </div>
  </div>
</template>

<style scoped>
.live {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.live__plate {
  position: relative;
  width: 100%;
  aspect-ratio: 3840 / 2160;
  overflow: hidden;
  border-radius: 0.6rem;
  background-color: #0b0f1a;
}

.live__video {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: fill;
}

.live__overlay {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.live__box {
  position: absolute;
  display: flex;
  align-items: center;
  justify-content: center;
}

.live__overlay--outline .live__box,
.live__overlay--ghost .live__box {
  outline: 1px dashed color-mix(in oklab, var(--color-brand-lit) 80%, transparent);
  outline-offset: 0;
}

.live__img {
  width: 100%;
  height: 100%;
  object-fit: contain;
  opacity: 0.4;
}

.live__badge {
  position: absolute;
  left: 10px;
  top: 8px;
  padding: 0.15rem 0.5rem;
  border-radius: 9999px;
  font-family: var(--font-bitmap, ui-monospace, monospace);
  font-size: 9px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: oklch(1 0 0 / 80%);
  background: oklch(0 0 0 / 55%);
}

.live__badge--on {
  background: color-mix(in oklab, var(--color-danger, #d33) 85%, transparent);
  color: #fff;
}

.live__modes {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 0.4rem;
  font-size: 0.82rem;
  color: var(--color-ash);
}

.live__modes-label {
  margin-right: 0.25rem;
}

.live__mode {
  padding: 0.2rem 0.7rem;
  border-radius: 9999px;
  border: 1px solid oklch(1 0 0 / 18%);
  color: var(--color-ash);
}

.live__mode--on {
  border-color: var(--color-brand-lit);
  color: var(--color-chalk);
}
</style>
