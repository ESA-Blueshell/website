<script lang="ts" setup>
import {computed, ref} from "vue"
import ModalDialog from "@/components/island/ModalDialog.vue"
import type {Receipt} from "../declarations"

/**
 * The receipts of one expense as small squares that sit in a row beside its words. A press opens
 * one large, where scrolling or pinching zooms in on the point under the pointer and a drag moves
 * it around, so the small print can be read.
 */
defineOptions({name: "ReceiptStrip"})

const {receipts, testid} = defineProps<{
  receipts: Receipt[]
  testid: string
}>()

const open = ref<Receipt | null>(null)
const view = ref<HTMLElement | null>(null)
const shot = ref<HTMLImageElement | null>(null)

/* The picture's size and place over its resting spot, origin at its top left, in screen pixels. */
const scale = ref(1)
const shift = ref({x: 0, y: 0})
const easing = ref(false)

const LEAST = 1
const MOST = 8

const show = (receipt: Receipt) => {
  open.value = receipt
  reset()
}

const reset = () => {
  scale.value = LEAST
  shift.value = {x: 0, y: 0}
}

/* Keeps at least a fifth of the picture in the window, so it cannot be dragged out of sight. */
const kept = (x: number, y: number) => {
  const picture = shot.value
  if (!picture) return {x, y}
  const width = picture.offsetWidth * scale.value
  const height = picture.offsetHeight * scale.value
  const reach = (size: number, room: number) => Math.max(room - size * 0.2, size * 0.8)
  return {
    x: Math.min(reach(width, view.value?.clientWidth ?? width), Math.max(-width * 0.8, x)),
    y: Math.min(reach(height, view.value?.clientHeight ?? height), Math.max(-height * 0.8, y)),
  }
}

/** Zooms to [next], keeping the point under (clientX, clientY) where it is. */
const zoomTo = (next: number, clientX?: number, clientY?: number) => {
  const picture = shot.value
  if (!picture) return
  const target = Math.min(MOST, Math.max(LEAST, next))
  if (target === LEAST) {
    reset()
    return
  }
  const rest = picture.getBoundingClientRect()
  const originX = rest.left - shift.value.x
  const originY = rest.top - shift.value.y
  const pointX = (clientX ?? rest.left + rest.width / 2) - originX
  const pointY = (clientY ?? rest.top + rest.height / 2) - originY
  const ratio = target / scale.value
  shift.value = kept(pointX - (pointX - shift.value.x) * ratio, pointY - (pointY - shift.value.y) * ratio)
  scale.value = target
}

/* A mouse wheel sends big steps and a trackpad many small ones, so the zoom follows the distance. */
const wheel = (event: WheelEvent) => {
  easing.value = false
  const lines = event.deltaMode === 1 ? 16 : 1
  zoomTo(scale.value * Math.exp(-event.deltaY * lines * (event.ctrlKey ? 0.01 : 0.002)), event.clientX, event.clientY)
}

const doubleClick = (event: MouseEvent) => {
  easing.value = true
  zoomTo(scale.value > 1.5 ? LEAST : 2.5, event.clientX, event.clientY)
}

/* One finger or the mouse drags; two fingers pinch. */
const pointers = new Map<number, {x: number; y: number}>()
let pinchFrom: {distance: number; scale: number} | null = null
let dragFrom: {x: number; y: number; shift: {x: number; y: number}} | null = null

const distance = () => {
  const [a, b] = [...pointers.values()]
  return a && b ? Math.hypot(a.x - b.x, a.y - b.y) : 0
}

const press = (event: PointerEvent) => {
  easing.value = false
  ;(event.currentTarget as HTMLElement).setPointerCapture(event.pointerId)
  pointers.set(event.pointerId, {x: event.clientX, y: event.clientY})
  if (pointers.size === 2) {
    pinchFrom = {distance: distance(), scale: scale.value}
    dragFrom = null
  } else {
    dragFrom = {x: event.clientX, y: event.clientY, shift: {...shift.value}}
  }
}

const move = (event: PointerEvent) => {
  if (!pointers.has(event.pointerId)) return
  pointers.set(event.pointerId, {x: event.clientX, y: event.clientY})
  if (pinchFrom && pointers.size === 2) {
    const [a, b] = [...pointers.values()]
    zoomTo(pinchFrom.scale * distance() / pinchFrom.distance, (a!.x + b!.x) / 2, (a!.y + b!.y) / 2)
    return
  }
  if (dragFrom && scale.value > LEAST) {
    shift.value = kept(dragFrom.shift.x + event.clientX - dragFrom.x, dragFrom.shift.y + event.clientY - dragFrom.y)
  }
}

const lift = (event: PointerEvent) => {
  pointers.delete(event.pointerId)
  if (pointers.size < 2) pinchFrom = null
  dragFrom = null
}

/* Safari reports a trackpad pinch as gestures rather than wheel events. */
let gestureFrom = 1
const gestureStart = (event: Event) => {
  event.preventDefault()
  gestureFrom = scale.value
}
const gestureChange = (event: Event) => {
  event.preventDefault()
  const gesture = event as Event & {scale: number; clientX: number; clientY: number}
  zoomTo(gestureFrom * gesture.scale, gesture.clientX, gesture.clientY)
}

const picture = computed(() => ({
  transform: `translate(${shift.value.x}px, ${shift.value.y}px) scale(${scale.value})`,
  transition: easing.value ? "transform 160ms ease-out" : "none",
}))
const isImage = (receipt: Receipt) => receipt.type.startsWith("image/")
</script>

<template>
  <div
    class="receipts"
    :data-testid="testid"
  >
    <button
      v-for="(receipt, index) in receipts"
      :key="receipt.id"
      :aria-label="`Open receipt ${index + 1}`"
      class="receipts__thumb"
      :data-testid="`${testid}-open-${index}`"
      type="button"
      @click.stop="show(receipt)"
    >
      <img
        v-if="isImage(receipt)"
        alt=""
        :src="receipt.url"
      >
      <span
        v-else
        class="receipts__pdf"
      >PDF</span>
    </button>

    <modal-dialog
      :cancel="false"
      :open="open != null"
      :testid="`${testid}-view`"
      title="Receipt"
      wide
      @update:open="open = $event ? open : null"
    >
      <div
        v-if="open && isImage(open)"
        ref="view"
        class="receipts__view"
        :class="{'receipts__view--zoomed': scale > 1}"
        :data-testid="`${testid}-zoom-area`"
        @dblclick="doubleClick"
        @dragstart.prevent
        @gesturechange="gestureChange"
        @gesturestart="gestureStart"
        @pointercancel="lift"
        @pointerdown.prevent="press"
        @pointermove="move"
        @pointerup="lift"
        @wheel.prevent="wheel"
      >
        <img
          ref="shot"
          :alt="open.name"
          class="receipts__full"
          :data-testid="`${testid}-full`"
          draggable="false"
          :src="open.url"
          :style="picture"
        >
      </div>
      <object
        v-else-if="open"
        class="receipts__pdf-view"
        :data="open.url"
        type="application/pdf"
      >
        {{ open.name }}
      </object>
    </modal-dialog>
  </div>
</template>

<style scoped>
.receipts {
  display: flex;
  flex-wrap: wrap;
  gap: 0.35rem;
}

.receipts__thumb {
  display: grid;
  flex: none;
  place-items: center;
  width: 2.6rem;
  height: 2.6rem;
  padding: 0;
  overflow: hidden;
  border: 1px solid color-mix(in oklab, var(--color-chalk) 22%, transparent);
  background: color-mix(in oklab, var(--color-chalk) 5%, transparent);
  cursor: pointer;
}

.receipts__thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.receipts__thumb:focus-visible {
  outline: 2px solid var(--edit-accent, var(--color-brand));
  outline-offset: 2px;
}

.receipts__pdf {
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.08em;
  color: var(--color-ash);
}

/* The receipt fills the dialog; zoomed in, it is dragged to the part being read. */
.receipts__view {
  display: grid;
  place-items: center;
  height: 70vh;
  overflow: hidden;
  touch-action: none;
  background: var(--color-pit);
}

.receipts__view--zoomed {
  cursor: grab;
}

.receipts__view--zoomed:active {
  cursor: grabbing;
}

.receipts__full {
  max-width: 100%;
  max-height: 70vh;
  transform-origin: 0 0;
  user-select: none;
  -webkit-user-drag: none;
  will-change: transform;
}

.receipts__pdf-view {
  width: 100%;
  height: 75vh;
}
</style>
