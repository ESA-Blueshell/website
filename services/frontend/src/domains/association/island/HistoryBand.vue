<script setup lang="ts">
import {computed, onBeforeUnmount, onMounted, ref, watch} from "vue"
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
const list = ref<HTMLElement | null>(null)

const holdItem = (element: unknown, index: number): void => {
  if (element instanceof HTMLElement) items.value[index] = element
}

/**
 * Whichever milestone's own middle is closest to the middle of the screen is the one being read.
 *
 * Measured from where the stops actually are, not from crossings reported as they happen: a
 * reader flicking the wheel can carry a stop past the middle of the screen between two frames,
 * and a milestone nobody was told about is a milestone that never opens. Distance is always
 * answerable, so scrolling past always arrives somewhere.
 */
const measure = (): void => {
  const band = list.value?.getBoundingClientRect()
  const middle = window.innerHeight / 2
  // Nothing is being read while the whole history is above or below the reader.
  if (!band || band.bottom < middle || band.top > middle) {
    nearest.value = -1
    return
  }

  let closest = -1
  let away = Number.POSITIVE_INFINITY
  items.value.forEach((item, index) => {
    if (!item) return
    const box = item.getBoundingClientRect()
    const distance = Math.abs((box.top + box.bottom) / 2 - middle)
    if (distance < away) {
      away = distance
      closest = index
    }
  })
  nearest.value = closest
}

/** At most one measurement a frame, however many scroll events the browser sends. */
let pending: number | null = null
const remeasure = (): void => {
  if (typeof requestAnimationFrame !== "function") {
    measure()
    return
  }
  if (pending !== null) return
  pending = requestAnimationFrame(() => {
    pending = null
    measure()
  })
}

onMounted(() => {
  // A reader who asked for less motion gets every milestone open and none of the growing.
  if (motion.reduced.value) return
  if (typeof window === "undefined") return

  window.addEventListener("scroll", remeasure, {passive: true})
  window.addEventListener("resize", remeasure, {passive: true})
  measure()
})

/**
 * How much of the telling has been written out, in characters.
 *
 * The telling carries on from the summary in the same paragraph rather than appearing under
 * it, so opening one is the sentence continuing rather than a second block arriving. Written
 * out a character at a time for the same reason: the eye follows the writing instead of
 * hunting for what just changed.
 *
 * A character a frame, in a bitmap face, with a block cursor waiting at the end of it: the way
 * a console wrote a line of dialogue, and the one piece of the page that is playing rather than
 * presenting.
 */
const typed = ref<number>(0)
let typing: number | null = null

/** Around a character and a half a frame on a 60Hz screen: the pace a handheld RPG writes at. */
const CHARACTERS_A_SECOND = 40

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

  const line = lineOf(index)
  if (motion.reduced.value || typeof requestAnimationFrame !== "function") {
    typed.value = line.length
    return
  }

  const started = performance.now()
  // Measured against the same clock it started on: a frame's own timestamp is not always on
  // that clock, and one that is not sends the elapsed time negative.
  const write = (): void => {
    const written = Math.round(((performance.now() - started) / 1000) * CHARACTERS_A_SECOND)
    typed.value = Math.min(written, line.length)
    typing = written < line.length ? requestAnimationFrame(write) : null
  }
  typed.value = 0
  typing = requestAnimationFrame(write)
})

/** The cursor sits at the end of the writing, and goes when there is nothing left to write. */
const writing = computed<boolean>(() => {
  if (motion.reduced.value || nearest.value < 0) return false
  return typed.value < lineOf(nearest.value).length
})

/**
 * The milestone's line in three pieces: what is written, the character being written, the rest.
 *
 * The whole line is written out, summary and telling together, and a milestone that is not
 * being read shows none of it. Arriving at one is a dialogue box opening: it starts empty and
 * fills, and nothing had to be taken off the page first.
 *
 * The part not yet written is on the page the whole time, in no colour. It has to be: the
 * milestones left of the line are set right-aligned, and text added to while it is right-aligned
 * grows away from its own end — the line crawls leftwards and reads as though it were being
 * written backwards. With the whole line holding its place from the start, the cursor travels
 * across ground the words already occupy and leaves them behind it, which is the thing being
 * imitated.
 */
interface Written {
  written: string
  landing: string
  waiting: string
}

const lineOf = (index: number): string => {
  const milestone = props.milestones[index]
  return milestone ? `${milestone.summary} ${milestone.telling}` : ""
}

const lineSoFar = (index: number): Written => {
  const line = lineOf(index)
  if (motion.reduced.value) return {written: line, landing: "", waiting: ""}
  // Every milestone holds the room its line will need, read or not, so arriving at one never
  // pushes the ones below it down the page — and never moves the page out from under the reader.
  // Nothing of it is on show until it is reached: a summary drawn beforehand would have to be
  // taken off the page for the writing to start, and text that vanishes to come back is worse
  // than text that was never there.
  if (index !== nearest.value) return {written: "", landing: "", waiting: line}

  const at = typed.value
  // A space never gets a mark of its own: there is nothing to see landing.
  const lands = at > 0 && line[at - 1] !== " "
  return {
    written: line.slice(0, lands ? at - 1 : at),
    landing: lands ? (line[at - 1] ?? "") : "",
    waiting: line.slice(at),
  }
}

onBeforeUnmount(() => {
  if (typeof window !== "undefined") {
    window.removeEventListener("scroll", remeasure)
    window.removeEventListener("resize", remeasure)
  }
  if (pending !== null) cancelAnimationFrame(pending)
  pending = null
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
    <ol
      ref="list"
      class="history__line mx-auto w-full max-w-3xl px-5 py-12 sm:px-8 sm:py-16"
    >
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
          <!-- One paragraph, not two: the telling carries on from the summary rather than
               arriving underneath it. -->
          <p class="history__summary">
            <span class="history__written">{{ lineSoFar(index).written }}</span><span
              v-if="lineSoFar(index).landing"
              :key="typed"
              class="history__landing"
            >{{ lineSoFar(index).landing }}</span><span
              v-if="writing && index === nearest"
              aria-hidden="true"
              class="history__cursor"
            /><span class="history__waiting">{{ lineSoFar(index).waiting }}</span>
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
 * The telling holds its own room whether or not it has been written yet, so the height comes
 * mostly from the words. The floor under it is for the short ones: a stop crossed in one flick
 * of the wheel opens and shuts before anybody could read it.
 */
.history__stop {
  position: relative;
  width: calc(50% - 2.75rem);
  min-height: 12vh;
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

/*
 * Always legible, and never as loud as the telling it stands in for.
 *
 * Set in the bitmap face at 16px, not in rem: Silkscreen is drawn on an 8px grid, and a size
 * off that grid puts its stems between pixels. The line height is loose because a bitmap face
 * set tight is a wall.
 */
.history__summary {
  margin-top: 0.5rem;
  font-family: var(--font-bitmap);
  font-size: 16px;
  line-height: 1.65;
  color: var(--color-ash);
}

/* What has been written, and the only part of the telling anybody can read. */
.history__written {
  color: inherit;
}

/* Holding its place and saying nothing: the reason a right-aligned telling does not crawl. */
.history__waiting {
  color: transparent;
}

/*
 * The character under the cursor comes up in two steps rather than appearing outright.
 *
 * Opacity and nothing else. A box of its own — anything that would let it be moved — is a break
 * opportunity in the middle of a word: the line re-wraps around the character being written,
 * and the paragraph jumps a line and back as the cursor travels along it.
 */
.history__landing {
  animation: history-landing 140ms steps(2, end);
}

@keyframes history-landing {
  from {
    opacity: 0.25;
  }

  to {
    opacity: 1;
  }
}

/*
 * Drawn rather than typed: no bitmap face is guaranteed to carry a block character.
 *
 * It is painted out of the flow, over the character it is standing on, from an inline box that
 * is empty and stays inline. Anything with a box of its own — an inline-block, even a zero-width
 * one — is a place the line may break: the words after it drop to the next line, and the
 * paragraph jumps a line and back as the cursor travels along it.
 */
.history__cursor {
  position: relative;
}

.history__cursor::before {
  content: "";
  position: absolute;
  top: 0.05em;
  left: 0.08em;
  width: 0.5em;
  height: 0.9em;
  background: currentColor;
  animation: history-cursor 640ms steps(1, end) infinite;
}

@keyframes history-cursor {
  0%,
  50% {
    opacity: 1;
  }

  50.01%,
  100% {
    opacity: 0;
  }
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

  .history__cursor::before {
    display: none;
  }

  .history__landing {
    animation: none;
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
    min-height: 14vh;
  }

  .history__stop:nth-child(odd) .history__dot,
  .history__stop:nth-child(even) .history__dot {
    left: 0;
    right: auto;
  }
}
</style>
