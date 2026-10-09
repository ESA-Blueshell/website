<script lang="ts" setup>
import {onBeforeUnmount, onMounted, ref, watch, computed} from "vue"

/**
 * One image on the canvas, filling in pixel by pixel in its own box. Lifted from the old watch
 * page: the logo builds up in a shuffled order with a spark of blue at the leading edge, wrapping
 * and repainting each pass. Several of these sit on one plate, each filling on its own pixel count,
 * all driven by the cluster's live total. Reduced motion fills without the per-frame climb or spark.
 */
defineOptions({name: "PingerCanvasPlacement"})

const CANVAS_W = 3840
const CANVAS_H = 2160
const PX_W = 600
const PX_H = 480
const SPARK = 300

const props = defineProps<{
  imageUrl: string
  originX: number
  originY: number
  width: number
  height: number
  running: boolean
  sent: number
  pps: number
}>()

const emit = defineEmits<{passtotal: [count: number]}>()

const boxStyle = computed(() => ({
  left: `${(props.originX / CANVAS_W) * 100}%`,
  top: `${(props.originY / CANVAS_H) * 100}%`,
  width: `${(props.width / CANVAS_W) * 100}%`,
  height: `${(props.height / CANVAS_H) * 100}%`,
}))

const pxCanvas = ref<HTMLCanvasElement | null>(null)
const sparkCanvas = ref<HTMLCanvasElement | null>(null)

const reduceMotion = typeof matchMedia === "function" && matchMedia("(prefers-reduced-motion: reduce)").matches

let xs: Uint16Array | null = null
let ys: Uint16Array | null = null
let cols: string[] = []
let order: Uint32Array | null = null
let count = 0

let baseSent = props.sent
let baseAt = now()
let drawn = 0
let frame = 0

function now(): number {
  return typeof performance !== "undefined" ? performance.now() : Date.now()
}

watch(() => props.sent, (value) => {
  baseSent = value
  baseAt = now()
})

function buildFrom(img: HTMLImageElement): void {
  const off = document.createElement("canvas")
  off.width = PX_W
  off.height = PX_H
  const octx = off.getContext("2d")
  if (!octx) return
  octx.drawImage(img, 0, 0, PX_W, PX_H)
  let data: Uint8ClampedArray
  try {
    data = octx.getImageData(0, 0, PX_W, PX_H).data
  } catch {
    return
  }
  const total = PX_W * PX_H
  xs = new Uint16Array(total)
  ys = new Uint16Array(total)
  cols = new Array(total)
  let p = 0
  for (let i = 0; i < total; i++) {
    const a = data[i * 4 + 3] ?? 0
    if (a < 8) continue
    xs[p] = i % PX_W
    ys[p] = Math.floor(i / PX_W)
    cols[p] = `rgba(${data[i * 4]},${data[i * 4 + 1]},${data[i * 4 + 2]},${a / 255})`
    p++
  }
  count = p
  order = new Uint32Array(count)
  for (let j = 0; j < count; j++) order[j] = j
  for (let k = count - 1; k > 0; k--) {
    const m = Math.floor(Math.random() * (k + 1))
    const t = order[k]!
    order[k] = order[m]!
    order[m] = t
  }
  emit("passtotal", count)
}

function loop(): void {
  frame = requestAnimationFrame(loop)
  const base = pxCanvas.value?.getContext("2d")
  const top = sparkCanvas.value?.getContext("2d")
  if (!base || !top || !order || count === 0) return

  if (!props.running) {
    base.clearRect(0, 0, PX_W, PX_H)
    top.clearRect(0, 0, PX_W, PX_H)
    drawn = 0
    return
  }

  const elapsed = reduceMotion ? 0 : Math.max(0, now() - baseAt) / 1000
  const done = baseSent + Math.max(0, props.pps) * elapsed
  const target = Math.max(0, Math.min(count, Math.floor(done % count)))

  if (target < drawn) {
    base.clearRect(0, 0, PX_W, PX_H)
    drawn = 0
  }
  for (let j = drawn; j < target; j++) {
    const idx = order[j]!
    base.fillStyle = cols[idx] ?? "#000"
    base.fillRect(xs![idx] ?? 0, ys![idx] ?? 0, 1, 1)
  }
  top.clearRect(0, 0, PX_W, PX_H)
  if (!reduceMotion) {
    top.fillStyle = "#7fd8ff"
    for (let s = Math.max(0, target - SPARK); s < target; s++) {
      const id2 = order[s]!
      top.fillRect((xs![id2] ?? 0) - 1, (ys![id2] ?? 0) - 1, 2, 2)
    }
  }
  drawn = target
}

function loadImage(): void {
  const img = new Image()
  img.onload = () => buildFrom(img)
  img.src = props.imageUrl
  if (img.complete && img.naturalWidth) buildFrom(img)
}

watch(() => props.imageUrl, () => {
  xs = ys = order = null
  cols = []
  count = 0
  drawn = 0
  emit("passtotal", 0)
  loadImage()
})

onMounted(() => {
  loadImage()
  frame = requestAnimationFrame(loop)
})

onBeforeUnmount(() => {
  cancelAnimationFrame(frame)
  emit("passtotal", 0)
})
</script>

<template>
  <div
    class="target"
    :style="boxStyle"
  >
    <img
      :src="imageUrl"
      alt=""
      class="target__img target__dim"
      draggable="false"
    >
    <canvas
      ref="pxCanvas"
      aria-label="An image filling in as the canvas is painted"
      class="target__img target__px"
      :height="PX_H"
      :width="PX_W"
    />
    <canvas
      ref="sparkCanvas"
      aria-hidden="true"
      class="target__img target__spark"
      :height="PX_H"
      :width="PX_W"
    />
    <span class="target__frame" />
  </div>
</template>

<style scoped>
.target {
  position: absolute;
}

.target__img {
  position: absolute;
  inset: 0;
  display: block;
  width: 100%;
  height: 100%;
  image-rendering: pixelated;
}

.target__dim {
  opacity: 0.2;
  filter: grayscale(1) brightness(1.15);
}

.target__frame {
  position: absolute;
  inset: -5px;
  border: 1px dashed color-mix(in oklab, var(--color-brand) 70%, transparent);
}
</style>
