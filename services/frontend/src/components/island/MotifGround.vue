<script setup lang="ts">
import {onBeforeUnmount, onMounted, ref} from "vue"

/**
 * The shell pattern, and the soft light over it, as the ground of whatever draws it.
 *
 * A band draws its own, so a band can set its colour, its strength and whether it wants the
 * light at all — but the pattern still has to line up with the band above and the band below,
 * or the shells step sideways at every seam.
 *
 * Nothing in CSS aligns a repeat to anything but the element repeating it, so the element is
 * told where it stands: `--motif-offset` is its own distance from the top of the document, and
 * the tile is drawn from there. Every band on the page then repeats from the same origin, which
 * is what makes the pattern continuous however the bands differ over it.
 */
defineOptions({name: "MotifGround"})

withDefaults(defineProps<{
  /** Whether the repeating shells are drawn at all. */
  pattern?: boolean
  /** Whether the soft light is drawn over them. */
  haze?: boolean
}>(), {pattern: true, haze: false})

const ground = ref<HTMLElement | null>(null)
const offset = ref<number>(0)

const measure = (): void => {
  const box = ground.value?.getBoundingClientRect()
  if (!box) return
  offset.value = Math.round(box.top + window.scrollY)
}

let watching: ResizeObserver | null = null

onMounted(() => {
  measure()
  // The page's own height decides where a band stands, so anything that reflows moves it.
  if (typeof ResizeObserver === "function") {
    watching = new ResizeObserver(measure)
    watching.observe(document.body)
  }
  window.addEventListener("resize", measure, {passive: true})
})

onBeforeUnmount(() => {
  watching?.disconnect()
  watching = null
  window.removeEventListener("resize", measure)
})
</script>

<template>
  <div
    ref="ground"
    aria-hidden="true"
    class="motif__ground"
    :style="{'--motif-offset': `${offset}px`}"
  >
    <div
      v-if="pattern"
      class="motif__ink"
    />
    <div
      v-if="haze"
      class="motif__haze"
    />
  </div>
</template>
