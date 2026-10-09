<script lang="ts" setup>
import {computed, onBeforeUnmount, onMounted, ref, watch} from "vue"
import {apiUrl, type Placement} from "@/domains/pinger"

/**
 * The event's livestream of the canvas with our placements drawn over it in their boxes, so what we
 * paint can be checked against what the canvas shows. hls.js, loaded on demand, plays the feed;
 * the browser plays it itself only where hls.js cannot run. It also measures the pixels one pass
 * paints, which the meter reads.
 */
defineOptions({name: "PingerLiveCanvas"})

const CANVAS_W = 3840
const CANVAS_H = 2160
const SAMPLE_W = 600
const SAMPLE_H = 480

const props = defineProps<{
  placements: Placement[]
  streamUrl: string
}>()

const emit = defineEmits<{passtotal: [count: number]}>()

type Overlay = "ghost" | "outline" | "off"
const overlays: {mode: Overlay, label: string}[] = [
  {mode: "ghost", label: "Ghost"},
  {mode: "outline", label: "Outline"},
  {mode: "off", label: "Off"},
]
const overlay = ref<Overlay>("ghost")
const corners = ["tl", "tr", "bl", "br"] as const

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

/** Opaque pixels in the image sampled at a fixed size; 0 where a cross-origin image hides them. */
function opaquePixels(img: HTMLImageElement): number {
  const off = document.createElement("canvas")
  off.width = SAMPLE_W
  off.height = SAMPLE_H
  const ctx = off.getContext("2d")
  if (!ctx) return 0
  ctx.drawImage(img, 0, 0, SAMPLE_W, SAMPLE_H)
  let data: Uint8ClampedArray
  try {
    data = ctx.getImageData(0, 0, SAMPLE_W, SAMPLE_H).data
  } catch {
    return 0
  }
  let count = 0
  for (let i = 3; i < data.length; i += 4) {
    if ((data[i] ?? 0) >= 8) count++
  }
  return count
}

// A load from a replaced set of placements must not count towards the current one.
let measuring = 0

function measure(placements: Placement[]): void {
  const round = ++measuring
  const counts = new Map<number, number>()
  emit("passtotal", 0)
  for (const p of placements) {
    const img = new Image()
    img.onload = () => {
      if (round !== measuring) return
      counts.set(p.id, opaquePixels(img))
      emit("passtotal", [...counts.values()].reduce((sum, n) => sum + n, 0))
    }
    img.src = apiUrl(p.imageUrl)
  }
}

watch(() => props.placements, measure, {immediate: true})

let destroy: (() => void) | null = null

onMounted(async () => {
  const el = video.value
  if (!el) return
  // hls.js first: desktop Chrome answers "maybe" for native HLS and then refuses the stream, so the
  // browser's own player is only the fallback, for iOS Safari where Media Source is missing.
  const {default: Hls} = await import("hls.js")
  if (!Hls.isSupported()) {
    if (el.canPlayType("application/vnd.apple.mpegurl")) el.src = props.streamUrl
    else failed.value = true
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

onBeforeUnmount(() => {
  measuring++
  destroy?.()
})
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
          <span
            v-for="corner in corners"
            :key="corner"
            class="live__corner"
            :class="`live__corner--${corner}`"
          />
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

/* The frame has to read over whatever the stream shows, so a bright line sits between two dark
   ones. The line is drawn inside the box, so a placement on the canvas edge keeps it past the
   plate's clip; only the corner brackets stand outside. */
.live__box {
  position: absolute;
  outline: 3px solid var(--color-acid);
  outline-offset: -3px;
  box-shadow:
    inset 0 0 0 5px oklch(0 0 0 / 75%),
    0 0 0 2px oklch(0 0 0 / 75%),
    0 0 14px 2px color-mix(in oklab, var(--color-acid) 45%, transparent);
}

.live__corner {
  position: absolute;
  width: clamp(12px, 18%, 32px);
  height: clamp(12px, 18%, 32px);
  border: 0 solid var(--color-acid);
  filter: drop-shadow(0 0 1.5px oklch(0 0 0 / 90%));
}

.live__corner--tl {
  left: -7px;
  top: -7px;
  border-left-width: 6px;
  border-top-width: 6px;
}

.live__corner--tr {
  right: -7px;
  top: -7px;
  border-right-width: 6px;
  border-top-width: 6px;
}

.live__corner--bl {
  left: -7px;
  bottom: -7px;
  border-left-width: 6px;
  border-bottom-width: 6px;
}

.live__corner--br {
  right: -7px;
  bottom: -7px;
  border-right-width: 6px;
  border-bottom-width: 6px;
}

@media (prefers-reduced-motion: no-preference) {
  .live__box {
    animation: live-glow 1.8s ease-in-out infinite alternate;
  }
}

@keyframes live-glow {
  to {
    box-shadow:
      inset 0 0 0 5px oklch(0 0 0 / 75%),
      0 0 0 2px oklch(0 0 0 / 75%),
      0 0 26px 6px color-mix(in oklab, var(--color-acid) 70%, transparent);
  }
}

.live__img {
  position: absolute;
  inset: 0;
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
