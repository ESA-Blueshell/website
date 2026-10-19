<script lang="ts" setup>
import {computed, ref, watch} from "vue"
import CutButton from "@/components/island/CutButton.vue"
import ModalDialog from "@/components/island/ModalDialog.vue"
import type {Crop} from "../adapters/declarations"

/**
 * A photo of a receipt with a frame around the part to keep. The frame is dragged to move it and
 * by its corners to size it, so a photo of a whole table keeps only the bill.
 */
defineOptions({name: "ReceiptCropper"})

const {src} = defineProps<{
  /** The photo, or nothing while no photo waits to be cropped. */
  src: string | null
}>()

const emit = defineEmits<{done: [crop: Crop]; cancel: []}>()

const START: Crop = {x: 0.06, y: 0.06, width: 0.88, height: 0.88}
const SMALLEST = 0.08

const crop = ref<Crop>({...START})
const stage = ref<HTMLElement | null>(null)

watch(() => src, () => {
  crop.value = {...START}
})

type Grip = "move" | "nw" | "ne" | "sw" | "se"
let grip: Grip | null = null
let from = {x: 0, y: 0, crop: {...START}}

const clamp = (value: number, low: number, high: number) => Math.min(high, Math.max(low, value))

const press = (event: PointerEvent, which: Grip) => {
  grip = which
  from = {x: event.clientX, y: event.clientY, crop: {...crop.value}}
  ;(event.currentTarget as HTMLElement).setPointerCapture(event.pointerId)
}

const drag = (event: PointerEvent) => {
  if (!grip || !stage.value) return
  const box = stage.value.getBoundingClientRect()
  const dx = (event.clientX - from.x) / box.width
  const dy = (event.clientY - from.y) / box.height
  const was = from.crop
  if (grip === "move") {
    crop.value = {
      ...was,
      x: clamp(was.x + dx, 0, 1 - was.width),
      y: clamp(was.y + dy, 0, 1 - was.height),
    }
    return
  }
  let left = was.x
  let top = was.y
  let right = was.x + was.width
  let bottom = was.y + was.height
  if (grip.includes("w")) left = clamp(left + dx, 0, right - SMALLEST)
  if (grip.includes("e")) right = clamp(right + dx, left + SMALLEST, 1)
  if (grip.includes("n")) top = clamp(top + dy, 0, bottom - SMALLEST)
  if (grip.includes("s")) bottom = clamp(bottom + dy, top + SMALLEST, 1)
  crop.value = {x: left, y: top, width: right - left, height: bottom - top}
}

const release = () => {
  grip = null
}

const frame = computed(() => ({
  left: `${crop.value.x * 100}%`,
  top: `${crop.value.y * 100}%`,
  width: `${crop.value.width * 100}%`,
  height: `${crop.value.height * 100}%`,
}))

const CORNERS: Grip[] = ["nw", "ne", "sw", "se"]
</script>

<template>
  <modal-dialog
    :cancel-testid="'receipt-crop-cancel'"
    :open="src != null"
    testid="receipt-crop"
    title="Crop the receipt"
    wide
    @update:open="$event || emit('cancel')"
  >
    <p class="crop__lead">
      Drag the frame over the receipt and pull its corners until only the receipt is inside.
    </p>
    <div
      v-if="src"
      ref="stage"
      class="crop__stage"
      @pointermove="drag"
      @pointerup="release"
      @pointercancel="release"
    >
      <img
        alt="The photo to crop"
        class="crop__photo"
        draggable="false"
        :src="src"
      >
      <div
        class="crop__frame"
        data-testid="receipt-crop-frame"
        :style="frame"
        @pointerdown.prevent="press($event, 'move')"
      >
        <span
          v-for="corner in CORNERS"
          :key="corner"
          :class="['crop__grip', `crop__grip--${corner}`]"
          :data-testid="`receipt-crop-${corner}`"
          @pointerdown.prevent.stop="press($event, corner)"
        />
      </div>
    </div>
    <template #footer>
      <cut-button
        testid="receipt-crop-whole"
        tone="quiet"
        @click="emit('done', {x: 0, y: 0, width: 1, height: 1})"
      >
        Use the whole photo
      </cut-button>
      <cut-button
        testid="receipt-crop-done"
        tone="solid"
        @click="emit('done', crop)"
      >
        Crop
      </cut-button>
    </template>
  </modal-dialog>
</template>

<style scoped>
.crop__lead {
  margin-bottom: 0.9rem;
  color: var(--color-ash);
}

.crop__stage {
  position: relative;
  width: fit-content;
  max-width: 100%;
  margin: 0 auto;
  touch-action: none;
  user-select: none;
}

/* A small photo is drawn up to a size the corners can be pulled on; the width follows the height. */
.crop__photo {
  display: block;
  width: auto;
  min-height: 40vh;
  max-width: 100%;
  max-height: 62vh;
}

/* Everything outside the frame is dimmed by the frame's own shadow, so the bill stands out. */
.crop__frame {
  position: absolute;
  border: 2px solid var(--color-brand);
  box-shadow: 0 0 0 100vmax rgb(0 0 0 / 0.55);
  cursor: move;
}

.crop__stage {
  overflow: hidden;
}

.crop__grip {
  position: absolute;
  width: 18px;
  height: 18px;
  background: var(--color-brand);
}

.crop__grip--nw {
  top: -9px;
  left: -9px;
  cursor: nwse-resize;
}

.crop__grip--ne {
  top: -9px;
  right: -9px;
  cursor: nesw-resize;
}

.crop__grip--sw {
  bottom: -9px;
  left: -9px;
  cursor: nesw-resize;
}

.crop__grip--se {
  right: -9px;
  bottom: -9px;
  cursor: nwse-resize;
}
</style>
