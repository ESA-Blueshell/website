<script lang="ts" setup>
import {computed, onMounted, reactive, ref} from "vue"
import {DEFAULT_PAINT, apiUrl, loadPaintJob, savePaintJob, storePaintImage, type PaintJob} from "@/domains/pinger"

defineOptions({name: "PingerManagerPage"})

const CANVAS_W = 3840
const CANVAS_H = 2160

// The box and prefix/rate the admin edits. originX/originY/width/height are in canvas pixels.
const box = reactive({originX: DEFAULT_PAINT.originX, originY: DEFAULT_PAINT.originY, width: DEFAULT_PAINT.width, height: DEFAULT_PAINT.height})
const prefix = ref("")
const rate = ref(DEFAULT_PAINT.ratePps)
// Whether the always-on SiteCie painter contributes; rate above is its rate.
const siteCieEnabled = ref(DEFAULT_PAINT.siteCieEnabled)

// imagePath is the stored path sent back on save; previewSrc is what the editor draws.
const imagePath = ref<string | null>(null)
const previewSrc = ref<string | null>(null)

const loading = ref(true)
const saving = ref(false)
const message = ref<{type: "success" | "error"; text: string} | null>(null)

const stage = ref<HTMLElement | null>(null)

function applyJob(job: PaintJob) {
  box.originX = job.originX
  box.originY = job.originY
  box.width = job.width
  box.height = job.height
  prefix.value = job.prefix ?? ""
  rate.value = job.ratePps
  siteCieEnabled.value = job.siteCieEnabled
  imagePath.value = job.imageUrl ? job.imageUrl.replace(/^\/files\/public\//, "") : null
  previewSrc.value = job.imageUrl ? apiUrl(job.imageUrl) : null
}

onMounted(async () => {
  applyJob(await loadPaintJob())
  loading.value = false
})

// The box as percentages of the canvas, so it renders correctly at any stage width.
const boxStyle = computed(() => ({
  left: `${(box.originX / CANVAS_W) * 100}%`,
  top: `${(box.originY / CANVAS_H) * 100}%`,
  width: `${(box.width / CANVAS_W) * 100}%`,
  height: `${(box.height / CANVAS_H) * 100}%`,
}))

type Drag = {mode: "move" | "resize"; startX: number; startY: number; box: typeof box}

function beginDrag(mode: "move" | "resize", event: PointerEvent) {
  event.preventDefault()
  const el = stage.value
  if (!el) return
  const rect = el.getBoundingClientRect()
  const start: Drag = {mode, startX: event.clientX, startY: event.clientY, box: {...box}}

  const move = (e: PointerEvent) => {
    const dx = ((e.clientX - start.startX) / rect.width) * CANVAS_W
    const dy = ((e.clientY - start.startY) / rect.height) * CANVAS_H
    if (start.mode === "move") {
      box.originX = clamp(Math.round(start.box.originX + dx), 0, CANVAS_W - box.width)
      box.originY = clamp(Math.round(start.box.originY + dy), 0, CANVAS_H - box.height)
    } else {
      box.width = clamp(Math.round(start.box.width + dx), 16, CANVAS_W - box.originX)
      box.height = clamp(Math.round(start.box.height + dy), 16, CANVAS_H - box.originY)
    }
  }
  const up = () => {
    window.removeEventListener("pointermove", move)
    window.removeEventListener("pointerup", up)
  }
  window.addEventListener("pointermove", move)
  window.addEventListener("pointerup", up)
}

function clamp(v: number, lo: number, hi: number) {
  return Math.min(Math.max(v, lo), Math.max(lo, hi))
}

async function onFile(files: File[] | File | null) {
  const file = Array.isArray(files) ? files[0] : files
  if (!file) return
  message.value = null
  const stored = await storePaintImage(file)
  if (!stored.ok) {
    message.value = {type: "error", text: stored.reason}
    return
  }
  imagePath.value = stored.saved
  previewSrc.value = URL.createObjectURL(file)
}

async function save() {
  saving.value = true
  message.value = null
  const result = await savePaintJob({
    prefix: prefix.value.trim() || null,
    ratePps: Number(rate.value),
    originX: box.originX,
    originY: box.originY,
    width: box.width,
    height: box.height,
    imagePath: imagePath.value,
    siteCieEnabled: siteCieEnabled.value,
  })
  saving.value = false
  if (result.ok) {
    applyJob(result.saved)
    message.value = {type: "success", text: "Saved. The pinger picks it up within a couple of seconds."}
  } else {
    message.value = {type: "error", text: result.reason}
  }
}
</script>

<template>
  <v-container
    class="py-8"
    style="max-width: 1000px"
  >
    <h1 class="text-h4 mb-2">
      Pinger
    </h1>
    <p class="text-body-2 mb-6">
      Set what the association paints on the SNTPings canvas. Upload an image, drag and resize its box over the 4K
      canvas, and set the prefix the event announces. The pinger and everyone running the helper follow this.
    </p>

    <v-alert
      v-if="message"
      :type="message.type"
      class="mb-4"
      variant="tonal"
    >
      {{ message.text }}
    </v-alert>

    <div v-if="!loading">
      <div
        ref="stage"
        class="pinger-stage mb-6"
        :style="{aspectRatio: `${CANVAS_W} / ${CANVAS_H}`}"
      >
        <div
          class="pinger-box"
          :style="boxStyle"
          @pointerdown="beginDrag('move', $event)"
        >
          <img
            v-if="previewSrc"
            :src="previewSrc"
            class="pinger-box__img"
            alt="The image to paint"
            draggable="false"
          >
          <span
            v-else
            class="pinger-box__empty"
          >Upload an image</span>
          <span
            class="pinger-box__handle"
            @pointerdown.stop="beginDrag('resize', $event)"
          />
        </div>
      </div>

      <v-row>
        <v-col
          cols="12"
          md="6"
        >
          <v-file-input
            label="Image"
            accept="image/png,image/jpeg,image/webp"
            prepend-icon="mdi-image"
            @update:model-value="onFile"
          />
        </v-col>
        <v-col
          cols="12"
          md="6"
        >
          <v-text-field
            v-model="prefix"
            label="Prefix"
            placeholder="2001:db8:b317:a000::/64"
            hint="Leave empty to keep the pinger idle"
            persistent-hint
          />
        </v-col>
        <v-col
          cols="12"
          md="6"
        >
          <v-switch
            v-model="siteCieEnabled"
            data-testid="sitecie-toggle"
            label="SiteCie paints"
            color="primary"
            hint="Turn SiteCie's cluster painter on or off; it idles within a couple of seconds when off"
            persistent-hint
          />
        </v-col>
        <v-col
          cols="12"
          md="6"
        >
          <v-text-field
            v-model.number="rate"
            label="SiteCie rate (packets per second)"
            type="number"
          />
        </v-col>
        <v-col
          cols="6"
          md="3"
        >
          <v-text-field
            v-model.number="box.originX"
            label="Origin X"
            type="number"
          />
        </v-col>
        <v-col
          cols="6"
          md="3"
        >
          <v-text-field
            v-model.number="box.originY"
            label="Origin Y"
            type="number"
          />
        </v-col>
        <v-col
          cols="6"
          md="3"
        >
          <v-text-field
            v-model.number="box.width"
            label="Width"
            type="number"
          />
        </v-col>
        <v-col
          cols="6"
          md="3"
        >
          <v-text-field
            v-model.number="box.height"
            label="Height"
            type="number"
          />
        </v-col>
      </v-row>

      <v-btn
        color="primary"
        class="mt-4"
        :loading="saving"
        @click="save"
      >
        Save
      </v-btn>
    </div>
  </v-container>
</template>

<style scoped>
.pinger-stage {
  position: relative;
  width: 100%;
  background: #0b1020;
  background-image: linear-gradient(rgba(255, 255, 255, 0.06) 1px, transparent 1px), linear-gradient(90deg, rgba(255, 255, 255, 0.06) 1px, transparent 1px);
  background-size: 10% 10%;
  border-radius: 6px;
  overflow: hidden;
  touch-action: none;
}
.pinger-box {
  position: absolute;
  border: 2px solid #7fd8ff;
  box-shadow: 0 0 0 9999px rgba(0, 0, 0, 0.35);
  cursor: move;
  touch-action: none;
}
.pinger-box__img {
  width: 100%;
  height: 100%;
  object-fit: contain;
  pointer-events: none;
  user-select: none;
}
.pinger-box__empty {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #7fd8ff;
  font-size: 0.8rem;
}
.pinger-box__handle {
  position: absolute;
  right: -7px;
  bottom: -7px;
  width: 14px;
  height: 14px;
  background: #7fd8ff;
  border-radius: 3px;
  cursor: nwse-resize;
}
</style>
