<script lang="ts" setup>
import {onBeforeUnmount, onMounted, ref} from "vue"

/**
 * A part of a public page drawn at the width it has there and shrunk to fit a narrower column,
 * so a preview is the part itself rather than an imitation of it.
 *
 * Inert: it is a picture of the page, and a pointer or a keyboard wandering into it would
 * operate a band that is not there.
 */
defineOptions({name: "PreviewFrame"})

const {width = 1280} = defineProps<{
  /** The width the part is laid out at before it is shrunk, in pixels. */
  width?: number
}>()

const frame = ref<HTMLElement | null>(null)
const stage = ref<HTMLElement | null>(null)
const scale = ref(1)
const height = ref(0)

const measure = () => {
  const room = frame.value?.clientWidth ?? 0
  scale.value = room > 0 ? Math.min(1, room / width) : 1
  height.value = stage.value?.offsetHeight ?? 0
}

let watcher: ResizeObserver | null = null

onMounted(() => {
  measure()
  if (typeof ResizeObserver === "undefined") return
  watcher = new ResizeObserver(measure)
  if (frame.value) watcher.observe(frame.value)
  if (stage.value) watcher.observe(stage.value)
})

onBeforeUnmount(() => watcher?.disconnect())
</script>

<template>
  <div
    ref="frame"
    class="preview-frame"
    :style="{height: `${Math.ceil(height * scale)}px`}"
  >
    <div
      ref="stage"
      aria-hidden="true"
      class="preview-frame__stage"
      inert
      :style="{width: `${width}px`, transform: `scale(${scale})`}"
    >
      <slot />
    </div>
  </div>
</template>

<style scoped>
.preview-frame {
  position: relative;
  overflow: hidden;
  background-color: var(--color-ground);
  outline: 1px solid var(--color-hairline);
}

/* A container, so a part that sizes itself by the page's width (in cqw, which on the page
   itself falls back to the viewport) takes the stage's width here instead of the screen's. */
.preview-frame__stage {
  position: absolute;
  top: 0;
  left: 0;
  container-type: inline-size;
  transform-origin: 0 0;
  pointer-events: none;
}
</style>
