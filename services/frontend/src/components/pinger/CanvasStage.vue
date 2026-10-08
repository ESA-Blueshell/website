<script lang="ts" setup>
import {reactive, watch} from "vue"
import CanvasPlacement from "@/components/pinger/CanvasPlacement.vue"
import {apiUrl, type Placement} from "@/domains/pinger"

/**
 * The SNTPings canvas plate, restored from the old watch page: a pixel grid over the whole 4K
 * field, the target prefix on it, and each placement's image filling in pixel by pixel in its own
 * box. Several images sit on the one plate, each animated on its own, all driven by the cluster's
 * live total. The whole canvas's pixel count is the sum of the placements', which the progress
 * meter reads.
 */
defineOptions({name: "PingerCanvasStage"})

const props = defineProps<{
  placements: Placement[]
  running: boolean
  sent: number
  pps: number
  prefixLabel: string
}>()

const emit = defineEmits<{passtotal: [count: number]}>()

const resolve = (url: string): string => apiUrl(url)

// Each placement reports its own pixel count; the whole canvas's is their sum.
const counts = reactive<Record<number, number>>({})

const onCount = (id: number, count: number): void => {
  counts[id] = count
  emit("passtotal", Object.values(counts).reduce((sum, n) => sum + n, 0))
}

// Drop a removed placement's count so the total does not keep counting a gone image.
watch(() => props.placements.map(p => p.id), (ids) => {
  for (const key of Object.keys(counts)) {
    if (!ids.includes(Number(key))) delete counts[Number(key)]
  }
  emit("passtotal", Object.values(counts).reduce((sum, n) => sum + n, 0))
})
</script>

<template>
  <div class="plate">
    <div class="plate__grid" />
    <canvas-placement
      v-for="placement in placements"
      :key="placement.id"
      :height="placement.height"
      :image-url="resolve(placement.imageUrl)"
      :origin-x="placement.originX"
      :origin-y="placement.originY"
      :pps="pps"
      :running="running"
      :sent="sent"
      :width="placement.width"
      @passtotal="onCount(placement.id, $event)"
    />
    <span class="plate__tag plate__tag--tl">0 · 0</span>
    <span class="plate__tag plate__tag--br">{{ prefixLabel }}</span>
    <span class="plate__shade" />
  </div>
</template>

<style scoped>
.plate {
  position: relative;
  width: 100%;
  aspect-ratio: 3840 / 2160;
  overflow: hidden;
  border-radius: 0.6rem;
  background-color: #0b0f1a;
  background-image:
    linear-gradient(rgba(11, 15, 26, 0.82), rgba(11, 15, 26, 0.82)),
    url("../../assets/bg/shelly-bg-black.png");
  background-size: auto, 135px 77px;
  background-repeat: repeat;
}

/* The pixel grid over the whole field: 16 by 9 cells, as the old watch page drew it. */
.plate__grid {
  position: absolute;
  inset: 0;
  opacity: 0.5;
  background-image:
    linear-gradient(to right, oklch(1 0 0 / 5%) 1px, transparent 1px),
    linear-gradient(to bottom, oklch(1 0 0 / 5%) 1px, transparent 1px);
  background-size: 6.25% 11.111%;
}

.plate__tag {
  position: absolute;
  font-family: var(--font-bitmap, ui-monospace, monospace);
  font-size: 8px;
  letter-spacing: 0.08em;
  color: oklch(1 0 0 / 55%);
}

.plate__tag--tl {
  left: 10px;
  top: 8px;
}

.plate__tag--br {
  right: 10px;
  top: 8px;
  color: var(--color-brand-lit);
}

.plate__shade {
  position: absolute;
  inset: 0;
  pointer-events: none;
  background: linear-gradient(105deg, transparent 55%, color-mix(in oklab, var(--color-brand) 10%, transparent));
}
</style>
