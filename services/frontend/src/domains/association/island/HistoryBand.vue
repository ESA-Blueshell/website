<script setup lang="ts">
import {onBeforeUnmount, onMounted, ref, watch} from "vue"
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
const props = defineProps<{
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

/**
 * How much of the telling has been written out, in characters.
 *
 * The telling carries on from the summary in the same paragraph rather than appearing under
 * it, so opening one is the sentence continuing rather than a second block arriving. Written
 * out a character at a time for the same reason: the eye follows the writing instead of
 * hunting for what just changed.
 */
const typed = ref<number>(0)
let typing: number | null = null

/** Roughly the pace of somebody writing, which is what stops it reading as a wipe. */
const CHARACTERS_A_SECOND = 120

const stopTyping = (): void => {
  if (typing !== null) cancelAnimationFrame(typing)
  typing = null
}

watch(nearest, index => {
  stopTyping()
  if (index < 0) {
    typed.value = 0
    return
  }

  const telling = props.milestones[index]?.telling ?? ""
  if (motion.reduced.value || typeof requestAnimationFrame !== "function") {
    typed.value = telling.length
    return
  }

  const started = performance.now()
  // Measured against the same clock it started on: a frame's own timestamp is not always on
  // that clock, and one that is not sends the elapsed time negative.
  const write = (): void => {
    const written = Math.round(((performance.now() - started) / 1000) * CHARACTERS_A_SECOND)
    typed.value = Math.min(written, telling.length)
    typing = written < telling.length ? requestAnimationFrame(write) : null
  }
  typed.value = 0
  typing = requestAnimationFrame(write)
})

/** What is on the page for this milestone: all of it once read, nothing before. */
const tellingSoFar = (index: number): string => {
  const telling = props.milestones[index]?.telling ?? ""
  if (motion.reduced.value) return telling
  return index === nearest.value ? telling.slice(0, typed.value) : ""
}

onBeforeUnmount(() => {
  watching?.disconnect()
  watching = null
  stopTyping()
})

const isRead = (index: number): boolean => motion.reduced.value || nearest.value === index
</script>

<template>
  <section
    class="history w-full"
    :class="{'history--still': motion.reduced.value}"
    :data-testid="testid"
  >
    <ol class="history__line mx-auto w-full max-w-3xl px-5 py-12 sm:px-8 sm:py-16">
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
          <!-- One paragraph, not two: the summary is always drawn, and the telling carries on
               from it rather than arriving underneath it. -->
          <p class="history__summary font-body">
            {{ milestone.summary }}<span class="history__telling">{{ " " + tellingSoFar(index) }}</span>
          </p>
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
  gap: 1.25rem;
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

/*
 * A little more height than the words need, and no more.
 *
 * The band that decides which milestone is being read is the middle 16% of the screen. A stop
 * no taller than its text is crossed in one flick of the wheel and its telling opens and shuts
 * before anybody could read it; a stop as tall as the screen turns the history into a chore.
 * This is the smallest height that makes scrolling arrive at them one at a time.
 */
.history__stop {
  position: relative;
  width: calc(50% - 2.75rem);
  min-height: 17vh;
  display: flex;
  align-items: center;
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
  top: 50%;
  margin-top: -0.35rem;
  width: 0.7rem;
  height: 0.7rem;
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
  transform: scale(1.5);
}

/*
 * Nothing is drawn around the milestone being read.
 *
 * The ink coming up, the dot filling and the telling opening are enough to say which one it
 * is; a panel or a rule around it is one more edge on a page already cut into bands.
 */
.history__card {
  width: 100%;
  /* Dimmed, not hidden: the summary of every milestone has to stay readable in both themes. */
  opacity: 0.68;
  transition: opacity 420ms var(--ease-out-quint);
}

.history__stop--read .history__card {
  opacity: 1;
}

.history__year {
  font-size: 0.8rem;
  letter-spacing: 0.22em;
  color: var(--color-ash);
  transition: color 420ms var(--ease-out-quint);
}

.history__stop--read .history__year {
  color: var(--color-eyebrow);
}

.history__title {
  margin-top: 0.3rem;
  font-size: clamp(1.1rem, 2.4vw, 1.7rem);
  line-height: 1.15;
  color: var(--color-chalk);
}

/* Always legible, and never as loud as the telling it stands in for. */
.history__summary {
  margin-top: 0.5rem;
  font-size: 0.9rem;
  line-height: 1.5;
  color: var(--color-ash);
}

/* Part of the sentence it continues: same size, same colour, no space of its own. */
.history__telling {
  color: inherit;
}

/*
 * Every milestone open, nothing growing, for a reader who asked for less motion — and for one
 * whose browser cannot watch the page at all. Nothing is held tall either: with everything
 * open there is nothing to scroll slowly towards.
 */
.history--still .history__stop {
  min-height: 0;
}

.history--still .history__card {
  opacity: 1;
}

.history--still .history__year {
  color: var(--color-eyebrow);
}



@media (prefers-reduced-motion: reduce) {
  .history__card,
  .history__dot,
  .history__year {
    transition: none;
  }
}

/*
 * On a phone the line runs up the left edge and every milestone sits to the right of it:
 * half a screen is not a column, and alternating sides would leave two words per line.
 */
@media (max-width: 767px) {
  .history__line {
    gap: 1rem;
  }

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
    min-height: 20vh;
  }

  .history__stop:nth-child(odd) .history__dot,
  .history__stop:nth-child(even) .history__dot {
    left: 0;
    right: auto;
  }
}
</style>
