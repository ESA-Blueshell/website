<script lang="ts" setup>
import {computed, onMounted, ref} from "vue"
import Island from "@/components/island/Island.vue"
import FormSection from "@/components/island/FormSection.vue"
import FormControl from "@/components/island/FormControl.vue"
import CheckBox from "@/components/island/CheckBox.vue"
import CutButton from "@/components/island/CutButton.vue"
import IconButton from "@/components/island/IconButton.vue"
import NoticeBox from "@/components/island/NoticeBox.vue"
import {
  DEFAULT_PAINT,
  addPlacement,
  apiUrl,
  loadPaintJob,
  movePlacement,
  removePlacement,
  saveSettings,
  storePaintImage,
  type Box,
  type PaintJob,
  type Placement,
} from "@/domains/pinger"

defineOptions({name: "PingerManagerPage"})

const CANVAS_W = 3840
const CANVAS_H = 2160

/** Where a freshly added image lands before the admin drags it; its height follows the image ratio. */
const ADD_ORIGIN_X = 1200
const ADD_ORIGIN_Y = 500
const ADD_WIDTH = 900

/** A placement plus what the editor needs: where to draw it and the image's own aspect ratio. */
type Editable = Placement & {src: string; ratio: number | null}

const placements = ref<Editable[]>([])
const selectedId = ref<number | null>(null)

const prefix = ref("")
const rate = ref("128")
const siteCieEnabled = ref(DEFAULT_PAINT.siteCieEnabled)

const loading = ref(true)
const saving = ref(false)
const message = ref<{tone: "info" | "danger"; text: string} | null>(null)

const stage = ref<HTMLElement | null>(null)
const fileInput = ref<HTMLInputElement | null>(null)

const editable = (placement: Placement): Editable => ({...placement, src: apiUrl(placement.imageUrl), ratio: null})

function applyJob(job: PaintJob) {
  prefix.value = job.prefix ?? ""
  rate.value = String(job.ratePps)
  siteCieEnabled.value = job.siteCieEnabled
  placements.value = job.placements.map(editable)
}

onMounted(async () => {
  applyJob(await loadPaintJob())
  loading.value = false
})

const boxStyle = (p: Placement) => ({
  left: `${(p.originX / CANVAS_W) * 100}%`,
  top: `${(p.originY / CANVAS_H) * 100}%`,
  width: `${(p.width / CANVAS_W) * 100}%`,
  height: `${(p.height / CANVAS_H) * 100}%`,
})

const clamp = (v: number, lo: number, hi: number) => Math.min(Math.max(v, lo), Math.max(lo, hi))

const box = (p: Placement): Box => ({originX: p.originX, originY: p.originY, width: p.width, height: p.height})

// A placement's footprint on the canvas in pixels, and the whole canvas's, shown so an admin sees
// how much of the 4K field each image and all of them together cover.
const pixelsOf = (p: Placement): number => p.width * p.height
const totalPixels = computed<number>(() => placements.value.reduce((sum, p) => sum + pixelsOf(p), 0))
const formatInt = (n: number): string => n.toLocaleString("en")

// The image's own ratio, captured once it loads, so a resize can hold it.
function onImageLoad(p: Editable, event: Event) {
  const img = event.target as HTMLImageElement
  if (img.naturalWidth > 0 && img.naturalHeight > 0) p.ratio = img.naturalWidth / img.naturalHeight
}

function beginDrag(p: Editable, mode: "move" | "resize", event: PointerEvent) {
  event.preventDefault()
  selectedId.value = p.id
  const el = stage.value
  if (!el) return
  const rect = el.getBoundingClientRect()
  const start = {x: event.clientX, y: event.clientY, originX: p.originX, originY: p.originY, width: p.width, height: p.height}

  const move = (e: PointerEvent) => {
    const dx = ((e.clientX - start.x) / rect.width) * CANVAS_W
    const dy = ((e.clientY - start.y) / rect.height) * CANVAS_H
    if (mode === "move") {
      p.originX = clamp(Math.round(start.originX + dx), 0, CANVAS_W - p.width)
      p.originY = clamp(Math.round(start.originY + dy), 0, CANVAS_H - p.height)
    } else if (p.ratio) {
      // Aspect-locked: the width leads, the height follows the image's ratio, and if that runs off
      // the canvas the height leads instead, so the image never stretches.
      let w = clamp(Math.round(start.width + dx), 16, CANVAS_W - p.originX)
      let h = Math.round(w / p.ratio)
      if (p.originY + h > CANVAS_H) {
        h = CANVAS_H - p.originY
        w = Math.round(h * p.ratio)
      }
      p.width = w
      p.height = h
    } else {
      p.width = clamp(Math.round(start.width + dx), 16, CANVAS_W - p.originX)
      p.height = clamp(Math.round(start.height + dy), 16, CANVAS_H - p.originY)
    }
  }
  const up = async () => {
    window.removeEventListener("pointermove", move)
    window.removeEventListener("pointerup", up)
    const result = await movePlacement(p.id, box(p))
    if (!result.ok) message.value = {tone: "danger", text: result.reason}
  }
  window.addEventListener("pointermove", move)
  window.addEventListener("pointerup", up)
}

function pickFile() {
  fileInput.value?.click()
}

async function onFile(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file) return
  message.value = null
  const stored = await storePaintImage(file)
  if (!stored.ok) {
    message.value = {tone: "danger", text: stored.reason}
    return
  }
  // Size the first box to the image's own ratio, so it lands undistorted.
  const ratio = await imageRatio(file)
  const width = ADD_WIDTH
  const height = ratio ? Math.round(width / ratio) : Math.round(width * 0.75)
  const at: Box = {
    originX: clamp(ADD_ORIGIN_X, 0, CANVAS_W - width),
    originY: clamp(ADD_ORIGIN_Y, 0, CANVAS_H - height),
    width,
    height: clamp(height, 1, CANVAS_H - ADD_ORIGIN_Y),
  }
  const added = await addPlacement(stored.saved, at)
  if (fileInput.value) fileInput.value.value = ""
  if (!added.ok) {
    message.value = {tone: "danger", text: added.reason}
    return
  }
  const row = editable(added.saved)
  row.ratio = ratio
  placements.value.push(row)
  selectedId.value = added.saved.id
}

/** The intrinsic aspect ratio of a chosen file, read off a temporary object URL. */
function imageRatio(file: File): Promise<number | null> {
  return new Promise((resolve) => {
    const url = URL.createObjectURL(file)
    const img = new Image()
    img.onload = () => {
      URL.revokeObjectURL(url)
      resolve(img.naturalWidth > 0 && img.naturalHeight > 0 ? img.naturalWidth / img.naturalHeight : null)
    }
    img.onerror = () => {
      URL.revokeObjectURL(url)
      resolve(null)
    }
    img.src = url
  })
}

async function remove(id: number) {
  message.value = null
  const result = await removePlacement(id)
  if (!result.ok) {
    message.value = {tone: "danger", text: result.reason}
    return
  }
  placements.value = placements.value.filter(p => p.id !== id)
}

async function save() {
  saving.value = true
  message.value = null
  const result = await saveSettings({prefix: prefix.value.trim() || null, ratePps: Number(rate.value) || 1, siteCieEnabled: siteCieEnabled.value})
  saving.value = false
  message.value = result.ok
    ? {tone: "info", text: "Saved. The pinger picks it up within a couple of seconds."}
    : {tone: "danger", text: result.reason}
}
</script>

<template>
  <island testid="pinger-manager">
    <div class="wrap">
      <header class="head">
        <p class="head__eyebrow">
          SNTPings
        </p>
        <h1 class="head__title">
          The pinger
        </h1>
        <p class="head__body">
          Set what the association paints on the SNTPings canvas. Add one or more images, drag and resize each box
          over the 4K canvas, and set the prefix the event announces. The pinger and everyone running the helper
          follow this.
        </p>
      </header>

      <notice-box
        v-if="message"
        class="notice"
        :tone="message.tone"
        testid="pinger-message"
      >
        {{ message.text }}
      </notice-box>

      <div
        v-if="!loading"
        class="stage-wrap"
      >
        <div
          ref="stage"
          class="stage"
          :style="{aspectRatio: `${CANVAS_W} / ${CANVAS_H}`}"
        >
          <div class="stage__grid" />
          <div
            v-for="p in placements"
            :key="p.id"
            class="box"
            :class="{'box--selected': p.id === selectedId}"
            data-testid="pinger-placement"
            :style="boxStyle(p)"
            @pointerdown="beginDrag(p, 'move', $event)"
          >
            <img
              :src="p.src"
              alt="An image on the canvas"
              class="box__img"
              draggable="false"
              @load="onImageLoad(p, $event)"
            >
            <span
              class="box__coords"
              data-testid="pinger-placement-coords"
            >{{ p.originX }}, {{ p.originY }} · {{ p.width }}&times;{{ p.height }}</span>
            <span
              class="box__handle"
              @pointerdown.stop="beginDrag(p, 'resize', $event)"
            />
          </div>
          <p
            v-if="placements.length === 0"
            class="stage__empty"
          >
            Add an image to start painting
          </p>
          <span class="stage__tag stage__tag--tl">0 · 0</span>
          <span class="stage__tag stage__tag--br">{{ prefix || "…/64" }}</span>
          <span class="stage__shade" />
        </div>
      </div>

      <form-section
        v-if="!loading"
        testid="pinger-placements"
        title="Images on the canvas"
      >
        <ul class="plates">
          <li
            v-for="p in placements"
            :key="p.id"
            class="plate"
            :class="{'plate--selected': p.id === selectedId}"
            data-testid="pinger-placement-row"
            @click="selectedId = p.id"
          >
            <span class="plate__thumb">
              <img
                :src="p.src"
                alt=""
                draggable="false"
              >
            </span>
            <span class="plate__facts">
              <span class="plate__fact">
                <span class="plate__label">Origin</span>
                <span class="plate__value">{{ p.originX }}, {{ p.originY }}</span>
              </span>
              <span class="plate__fact">
                <span class="plate__label">Size</span>
                <span class="plate__value">{{ p.width }} &times; {{ p.height }}</span>
              </span>
              <span class="plate__fact">
                <span class="plate__label">Pixels</span>
                <span class="plate__value">{{ formatInt(pixelsOf(p)) }}</span>
              </span>
            </span>
            <icon-button
              danger
              label="Remove this image"
              testid="pinger-placement-remove"
              @click.stop="remove(p.id)"
            >
              <svg
                aria-hidden="true"
                fill="none"
                viewBox="0 0 16 16"
              >
                <path
                  d="M4 4l8 8M12 4l-8 8"
                  stroke="currentColor"
                  stroke-width="1.6"
                />
              </svg>
            </icon-button>
          </li>
          <li
            class="plates__add"
            data-testid="pinger-add"
            @click="pickFile"
          >
            <span
              aria-hidden="true"
              class="plates__add-plus"
            >+</span>
            <span class="plates__add-text">Add an image</span>
          </li>
        </ul>
        <p class="plates__total">
          Total <span>{{ formatInt(totalPixels) }}</span> pixels across {{ placements.length }} image{{ placements.length === 1 ? "" : "s" }}
        </p>
        <input
          ref="fileInput"
          accept="image/png,image/jpeg,image/webp"
          class="hidden-file"
          type="file"
          @change="onFile"
        >
      </form-section>

      <form-section
        v-if="!loading"
        testid="pinger-settings"
        title="Settings"
      >
        <form-control
          v-model="prefix"
          hint="Leave empty to keep the pinger idle"
          kind="text"
          label="Prefix"
          placeholder="2001:db8:b317:a000::/64"
          testid="pinger-prefix"
        />
        <form-control
          v-model="rate"
          kind="count"
          label="SiteCie rate (packets per second)"
          testid="pinger-rate"
        />
        <check-box
          v-model="siteCieEnabled"
          hint="Turn SiteCie's cluster painter on or off; it idles within a couple of seconds when off"
          label="SiteCie paints"
          testid="pinger-sitecie"
        />
        <div class="save">
          <cut-button
            :disabled="saving"
            submit
            testid="pinger-save"
            tone="solid"
            @click="save"
          >
            {{ saving ? "Saving…" : "Save settings" }}
          </cut-button>
        </div>
      </form-section>
    </div>
  </island>
</template>

<style scoped>
.wrap {
  width: 100%;
  max-width: 60rem;
  margin: 0 auto;
  padding: 2.25rem 1.5rem 3rem;
}

.head__eyebrow {
  font-family: var(--font-body);
  font-size: 11px;
  font-weight: 500;
  letter-spacing: 0.3em;
  text-transform: uppercase;
  color: var(--color-eyebrow);
}

.head__title {
  margin-top: 0.5rem;
  font-family: var(--font-display);
  font-size: 2.2rem;
  text-transform: uppercase;
}

.head__body {
  margin-top: 0.75rem;
  max-width: 44rem;
  font-size: 0.9rem;
  line-height: 1.55;
  color: var(--color-ash);
}

.notice {
  margin-top: 1.25rem;
}

.stage-wrap {
  margin-top: 1.5rem;
}

/* The editor plate matches the public SNTPings canvas exactly: the dark field with the shell tile
   behind it, the pixel grid, the corner tags and the brand shade. Only the draggable boxes differ. */
.stage {
  position: relative;
  width: 100%;
  overflow: hidden;
  border-radius: 0.6rem;
  background-color: #0b0f1a;
  background-image:
    linear-gradient(rgba(11, 15, 26, 0.82), rgba(11, 15, 26, 0.82)),
    url("../../assets/bg/shelly-bg-black.png");
  background-size: auto, 135px 77px;
  background-repeat: repeat;
  touch-action: none;
}

.stage__grid {
  position: absolute;
  inset: 0;
  opacity: 0.5;
  pointer-events: none;
  background-image:
    linear-gradient(to right, oklch(1 0 0 / 5%) 1px, transparent 1px),
    linear-gradient(to bottom, oklch(1 0 0 / 5%) 1px, transparent 1px);
  background-size: 6.25% 11.111%;
}

.stage__tag {
  position: absolute;
  pointer-events: none;
  font-family: var(--font-bitmap, ui-monospace, monospace);
  font-size: 8px;
  letter-spacing: 0.08em;
  color: oklch(1 0 0 / 55%);
}

.stage__tag--tl {
  left: 10px;
  top: 8px;
}

.stage__tag--br {
  right: 10px;
  top: 8px;
  color: var(--color-brand-lit);
}

.stage__shade {
  position: absolute;
  inset: 0;
  pointer-events: none;
  background: linear-gradient(105deg, transparent 55%, color-mix(in oklab, var(--color-brand) 10%, transparent));
}

.stage__empty {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  font-size: 0.85rem;
  color: var(--color-brand-lit);
}

.box {
  position: absolute;
  border: 2px solid color-mix(in oklab, var(--color-brand-lit) 60%, transparent);
  cursor: move;
  touch-action: none;
}

.box--selected {
  border-color: var(--color-brand-lit);
  box-shadow: 0 0 0 1px var(--color-brand-lit);
}

.box__img {
  width: 100%;
  height: 100%;
  object-fit: contain;
  pointer-events: none;
  user-select: none;
}

/* The live readout on the box itself, so coordinates are visible on the canvas as it is dragged. */
.box__coords {
  position: absolute;
  left: 0;
  top: -1.3rem;
  padding: 0.1rem 0.35rem;
  font-family: var(--font-bitmap, ui-monospace, monospace);
  font-size: 8px;
  letter-spacing: 0.04em;
  white-space: nowrap;
  color: var(--color-void);
  background: var(--color-brand-lit);
}

.box__handle {
  position: absolute;
  right: -7px;
  bottom: -7px;
  width: 14px;
  height: 14px;
  background: var(--color-brand-lit);
  cursor: nwse-resize;
}

.hidden-file {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip-path: inset(50%);
}

.plates {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

/* The add control sits as the last row of the list: pick an image and it joins the list above, the
   add bar staying at the bottom for the next one. */
.plates__add {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  padding: 0.7rem 0.75rem;
  border: 1px dashed color-mix(in oklab, var(--color-brand-lit) 50%, transparent);
  background: color-mix(in oklab, var(--color-brand) 6%, transparent);
  color: var(--color-brand-lit);
  font-family: var(--font-body);
  font-size: 0.82rem;
  font-weight: 600;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  cursor: pointer;
}

.plates__add:hover {
  background: color-mix(in oklab, var(--color-brand) 12%, transparent);
}

.plates__add-plus {
  font-size: 1.1rem;
  line-height: 1;
}

.plates__total {
  margin-top: 0.6rem;
  font-family: var(--font-body);
  font-size: 0.78rem;
  letter-spacing: 0.04em;
  color: var(--color-ash);
}

.plates__total span {
  font-family: var(--font-bitmap, ui-monospace, monospace);
  color: var(--color-chalk);
  font-variant-numeric: tabular-nums;
}

.plate {
  display: grid;
  grid-template-columns: 3.5rem minmax(0, 1fr) auto;
  align-items: center;
  gap: 0 1rem;
  padding: 0.6rem 0.75rem;
  background-color: var(--band-ground);
  box-shadow: inset 3px 0 0 transparent;
  cursor: pointer;
}

.plate--selected {
  box-shadow: inset 3px 0 0 var(--color-brand);
}

.plate__thumb {
  display: grid;
  place-items: center;
  width: 3.5rem;
  height: 2.6rem;
  overflow: hidden;
  background: color-mix(in oklab, var(--color-chalk) 6%, transparent);
}

.plate__thumb img {
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.plate__facts {
  display: flex;
  flex-wrap: wrap;
  gap: 0.25rem 1.5rem;
  min-width: 0;
}

.plate__fact {
  display: flex;
  flex-direction: column;
}

.plate__label {
  font-family: var(--font-body);
  font-size: 10px;
  font-weight: 500;
  letter-spacing: 0.22em;
  text-transform: uppercase;
  color: var(--color-ash);
}

.plate__value {
  font-family: var(--font-bitmap, ui-monospace, monospace);
  font-size: 0.85rem;
  color: var(--color-chalk);
}

.save {
  margin-top: 0.5rem;
}
</style>
