<script setup lang="ts">
import {onBeforeUnmount, onMounted, ref} from "vue"
import type {Milestone} from "@/domains/association/historyAxis"
import {useMotionAllowed} from "@/components/island/useMotionAllowed"

/**
 * The association's history, read by scrolling rather than by dragging.
 *
 * A line down the middle with the milestones either side of it. Whichever one is nearest the
 * middle of the screen is the one being read: it stands up, and it tells the rest of its story.
 * The reader moves the page, and the page answers — there is nothing to find and click.
 *
 * Not the island's [Timeline], which is a strip a reader travels along sideways to *select*
 * something the band below it then draws. Here the milestones are the content, there are only
 * ever a handful, and each is a paragraph rather than a heading — a horizontal strip would put
 * six paragraphs off the side of the screen.
 */
defineProps<{
  milestones: readonly Milestone[]
  testid?: string
}>()

const motion = useMotionAllowed()

/** Which milestone is nearest the middle of the screen, or none while the page is elsewhere. */
const nearest = ref<number>(-1)
const items = ref<HTMLElement[]>([])

const holdItem = (element: unknown, index: number): void => {
  if (element instanceof HTMLElement) items.value[index] = element
}

let watching: IntersectionObserver | null = null

onMounted(() => {
  // A reader who asked for less motion gets every milestone open and none of the growing.
  if (motion.reduced.value) return
  if (typeof IntersectionObserver !== "function") return

  watching = new IntersectionObserver(
    entries => {
      for (const entry of entries) {
        const index = items.value.indexOf(entry.target as HTMLElement)
        if (index === -1) continue
        if (entry.isIntersecting) nearest.value = index
        else if (nearest.value === index) nearest.value = -1
      }
    },
    // The same band across the middle the slices open on: only what a reader has actually
    // brought to the centre counts as the one they are reading.
    {rootMargin: "-42% 0px -42% 0px", threshold: 0},
  )
  for (const item of items.value) if (item) watching.observe(item)
})

onBeforeUnmount(() => {
  watching?.disconnect()
  watching = null
})

const isRead = (index: number): boolean => motion.reduced.value || nearest.value === index
</script>

<template>
  <section
    class="history w-full"
    :class="{'history--still': motion.reduced.value}"
    :data-testid="testid"
  >
    <ol class="history__line mx-auto w-full max-w-4xl px-5 py-12 sm:px-8 sm:py-16">
      <li
        v-for="(milestone, index) in milestones"
        :key="`${milestone.year}-${milestone.title}`"
        :ref="element => holdItem(element, index)"
        class="history__stop"
        :class="{'history__stop--read': isRead(index)}"
        :data-testid="testid ? `${testid}-stop` : undefined"
      >
        <span
          aria-hidden="true"
          class="history__dot"
        />
        <div class="history__card">
          <p class="history__year font-display">
            {{ milestone.year }}
          </p>
          <h3 class="history__title font-display uppercase">
            {{ milestone.title }}
          </h3>
          <!-- The grid row rather than a height: a paragraph knows its own size and this way
               nothing has to guess it. -->
          <div class="history__more">
            <p class="history__telling font-body">
              {{ milestone.telling }}
            </p>
          </div>
        </div>
      </li>
    </ol>
  </section>
</template>

<style scoped>
.history {
  background: var(--band-ground);
}

.history__line {
  position: relative;
  list-style: none;
  display: grid;
  gap: 2.5rem;
}

/* The line itself, running the height of the list behind the stops. */
.history__line::before {
  content: "";
  position: absolute;
  top: 3rem;
  bottom: 3rem;
  left: 50%;
  width: 2px;
  transform: translateX(-50%);
  background: linear-gradient(
    to bottom,
    transparent,
    var(--color-brand) 12%,
    var(--color-brand) 88%,
    transparent
  );
}

.history__stop {
  position: relative;
  width: calc(50% - 2.75rem);
}

/* Alternating sides, so the line reads as one thing being passed rather than a list indented. */
.history__stop:nth-child(odd) {
  margin-right: auto;
  text-align: right;
}

.history__stop:nth-child(even) {
  margin-left: auto;
}

.history__dot {
  position: absolute;
  top: 0.55rem;
  width: 0.85rem;
  height: 0.85rem;
  border-radius: 999px;
  background: var(--color-ground);
  border: 2px solid var(--color-brand);
  transition: transform 320ms var(--ease-out-quint), background-color 320ms var(--ease-out-quint);
}

.history__stop:nth-child(odd) .history__dot {
  right: -3.2rem;
}

.history__stop:nth-child(even) .history__dot {
  left: -3.2rem;
}

.history__stop--read .history__dot {
  background: var(--color-brand);
  transform: scale(1.45);
}

.history__card {
  transition: transform 420ms var(--ease-out-quint), opacity 420ms var(--ease-out-quint);
  transform-origin: var(--grow-from, center);
  opacity: 0.62;
}

.history__stop:nth-child(odd) .history__card {
  --grow-from: right center;
}

.history__stop:nth-child(even) .history__card {
  --grow-from: left center;
}

.history__stop--read .history__card {
  opacity: 1;
  transform: scale(1.06);
}

.history__year {
  font-size: 0.8rem;
  letter-spacing: 0.22em;
  color: var(--color-eyebrow);
}

.history__title {
  margin-top: 0.35rem;
  font-size: clamp(1.05rem, 2.2vw, 1.6rem);
  line-height: 1.15;
  color: var(--color-chalk);
}

/* Closed to nothing and opened by the row rather than by a height nobody can know. */
.history__more {
  display: grid;
  grid-template-rows: 0fr;
  transition: grid-template-rows 480ms var(--ease-out-quint);
}

.history__stop--read .history__more {
  grid-template-rows: 1fr;
}

.history__telling {
  overflow: hidden;
  margin-top: 0;
  font-size: 0.875rem;
  line-height: 1.6;
  color: var(--color-ash);
  opacity: 0;
  transition: opacity 320ms var(--ease-out-quint), margin-top 320ms var(--ease-out-quint);
}

.history__stop--read .history__telling {
  margin-top: 0.6rem;
  opacity: 1;
}

/*
 * Every milestone open, nothing growing, for a reader who asked for less motion — and for one
 * whose browser cannot watch the page at all.
 */
.history--still .history__card {
  opacity: 1;
  transform: none;
}

.history--still .history__more {
  grid-template-rows: 1fr;
}

.history--still .history__telling {
  margin-top: 0.6rem;
  opacity: 1;
}

@media (prefers-reduced-motion: reduce) {
  .history__card,
  .history__dot,
  .history__more,
  .history__telling {
    transition: none;
  }
}

/*
 * On a phone the line runs up the left edge and every milestone sits to the right of it:
 * half a screen is not a column, and alternating sides would leave two words per line.
 */
@media (max-width: 767px) {
  .history__line::before {
    left: 0.4rem;
  }

  .history__stop,
  .history__stop:nth-child(odd),
  .history__stop:nth-child(even) {
    width: 100%;
    margin: 0;
    padding-left: 2.25rem;
    text-align: left;
  }

  .history__stop:nth-child(odd) .history__dot,
  .history__stop:nth-child(even) .history__dot {
    left: 0;
    right: auto;
  }

  .history__stop:nth-child(odd) .history__card,
  .history__stop:nth-child(even) .history__card {
    --grow-from: left center;
  }

  .history__stop--read .history__card {
    transform: scale(1.03);
  }
}
</style>
