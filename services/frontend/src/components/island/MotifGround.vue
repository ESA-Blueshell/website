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
 * told where it stands and the stylesheet does the arithmetic: this reports `--motif-offset`,
 * its own distance from the top of the document, and island.css takes it modulo the tile. Every
 * band then repeats from one origin, which is what makes the pattern continuous however the
 * bands differ over it.
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

/**
 * Where this stands in the document, which is all the stylesheet needs.
 *
 * `offsetTop` walks the offset parents rather than reading the viewport, so it is the same
 * number whatever the page is scrolled to and nothing has to be recomputed as it scrolls.
 */
const measure = (): void => {
  const element = ground.value
  if (!element) return
  let top = 0
  for (let box: HTMLElement | null = element; box; box = box.offsetParent as HTMLElement | null) {
    top += box.offsetTop
  }
  offset.value = top
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
