<script lang="ts" setup>
import {onBeforeUnmount, onMounted, ref, watch} from "vue"
import {useMotionAllowed} from "./useMotionAllowed"

defineOptions({name: "BannerSlices"})

export interface SliceItem {
  id: number | string
  title: string
  /** A line under the title while the slice is shut, and while it is open. */
  meta: string
  /** The image behind it, where there is one. */
  banner: string
  /** Its own colour, where it has one; otherwise the band's accent is used. */
  accent?: string
}

const props = withDefaults(defineProps<{
  items: SliceItem[]
  accent: string
  /** What each slice's data-testid is built from, since the two pages name them differently. */
  testidPrefix: string
  /** Whether the band ends in a way to add another. */
  mayAdd?: boolean
  /** What the plus is called, since one page adds a team and the other adds one to a game. */
  addLabel?: string
  /**
   * One slice to open by name, which wins over opening the first.
   *
   * Something just added is the thing to look at, and it is rarely first: the band is in the
   * order it reads in, not the order things were written down. A prop rather than a method
   * because a new set of items reopens the first of them, and a call made before that lands
   * would simply be undone.
   */
  openId?: SliceItem["id"] | null
  /** Whether each slice offers a way to change what it shows. */
  mayEdit?: boolean
  /** Whether each slice offers a way to take it out of what is on show. */
  mayDrop?: boolean
}>(), {mayAdd: false, addLabel: "Add", openId: null, mayEdit: false, mayDrop: false})

const emit = defineEmits<{
  (event: "add"): void
  (event: "edit", id: SliceItem["id"]): void
  (event: "drop", id: SliceItem["id"]): void
}>()

const motion = useMotionAllowed()

/**
 * Which slice is open. Nothing is open for the first frame so the opening of the first one is
 * something the visitor sees happen rather than something already done — except under reduced
 * motion, where it is simply open from the start.
 */
const open = ref<number | null>(null)
const slices = ref<HTMLElement[]>([])

/**
 * A slice the visitor opened themselves. Stacked, the scroll decides which slice is open, and
 * without this a tap was undone by the next observation: the slice opened and shut again
 * before a finger left the screen. The choice stands until the page is scrolled, at which
 * point the scroll is the visitor's intent again.
 */
const tapped = ref<number | null>(null)

/** Stacked, there is no pointer to move across the slices, so the scroll does the choosing. */
const stacked = () =>
  typeof window !== "undefined" && window.matchMedia("(max-width: 767px)").matches

let watcher: IntersectionObserver | null = null

const watchScroll = () => {
  watcher?.disconnect()
  if (!stacked() || typeof IntersectionObserver === "undefined") return
  watcher = new IntersectionObserver(entries => {
    // Whichever slice has most of itself in the middle band of the screen is the one open.
    const best = entries
      .filter(entry => entry.isIntersecting)
      .sort((a, b) => b.intersectionRatio - a.intersectionRatio)[0]
    if (!best) return
    if (tapped.value !== null) return
    const index = slices.value.indexOf(best.target as HTMLElement)
    if (index >= 0) open.value = index
  }, {rootMargin: "-42% 0px -42% 0px", threshold: [0, 0.25, 0.5, 1]})
  slices.value.forEach(el => el && watcher?.observe(el))
}

const releaseTap = () => {
  tapped.value = null
}

const choose = (index: number) => {
  open.value = index
  if (stacked()) tapped.value = index
}

const indexOfNamed = () => props.items.findIndex(item => item.id === props.openId)



const settle = () => {
  const named = indexOfNamed()
  open.value = named >= 0 ? named : 0
  // Stacked, the scroll decides what is open, so a named slice has to be held against it the
  // same way a tap is. This runs on mount too, which is where a band rebuilt around a slice
  // that was just added arrives with the name already set and no change left to react to.
  if (named >= 0 && stacked()) tapped.value = named
}

onMounted(() => {
  if (!motion.decorative.value) {
    settle()
  } else {
    requestAnimationFrame(() => requestAnimationFrame(settle))
  }
  watchScroll()
  window.addEventListener("scroll", releaseTap, {passive: true})
})

onBeforeUnmount(() => {
  watcher?.disconnect()
  window.removeEventListener("scroll", releaseTap)
})

// A change of what is on show brings a different set, so the first of those opens in its turn
// — unless one of them is named, which is the set arriving because that one was just added.
watch(() => props.items, () => {
  slices.value = []
  const named = indexOfNamed()
  // Stacked, the scroll decides what is open, so a named slice has to hold against it.
  tapped.value = stacked() && named >= 0 ? named : null
  open.value = motion.decorative.value ? null : (named >= 0 ? named : 0)
  if (motion.decorative.value) requestAnimationFrame(() => requestAnimationFrame(settle))
  requestAnimationFrame(watchScroll)
})

/**
 * Opens the named slice, which is the one just added.
 *
 * After the update rather than during it, so the set it belongs to is the one on screen.
 * Stacked, this is held the same way a tap is held — until the visitor scrolls, at which
 * point the scroll is their intent again. Scrolling to it ourselves would be that scroll,
 * and would hand the choice straight back to whichever slice happened to be in the middle.
 */
watch([() => props.openId, () => props.items], () => {
  const named = indexOfNamed()
  if (named < 0) return
  open.value = named
  if (stacked()) tapped.value = named
}, {flush: "post"})
</script>

<template>
  <div
    class="team-slices"
    :data-testid="`${testidPrefix}-slices`"
    :style="{'--accent': accent}"
    @mouseleave="open = 0"
  >
    <section
      v-for="(item, index) in items"
      :key="item.id"
      :ref="el => { if (el) slices[index] = el as HTMLElement }"
      class="team-slice"
      :class="{
        'team-slice--open': index === open,
        'team-slice--first': index === 0,
        'team-slice--last': index === items.length - 1 && !mayAdd,
      }"
      :data-testid="`${testidPrefix}-${item.id}`"
      :style="item.accent ? {'--accent': item.accent} : undefined"
      @focusin="open = index"
      @mouseenter="open = index"
    >
      <!--
        Offered only to somebody who may take it up, and belonging to the slice it sits on.
        Where there is a pointer it waits for one; where there is not, it stands.
      -->
      <button
        v-if="mayEdit"
        :aria-label="`Edit ${item.title}`"
        class="team-slice__edit"
        :data-testid="`${testidPrefix}-edit-${item.id}`"
        type="button"
        @click.stop="emit('edit', item.id)"
      >
        <svg
          aria-hidden="true"
          fill="none"
          stroke="currentColor"
          stroke-width="2"
          viewBox="0 0 24 24"
        >
          <path d="M4 20h4L19 9a2.8 2.8 0 0 0-4-4L4 16v4Z" />
        </svg>
      </button>

      <button
        v-if="mayDrop"
        :aria-label="`Remove ${item.title}`"
        class="team-slice__edit team-slice__drop"
        :data-testid="`${testidPrefix}-drop-${item.id}`"
        type="button"
        @click.stop="emit('drop', item.id)"
      >
        <svg
          aria-hidden="true"
          fill="none"
          stroke="currentColor"
          stroke-width="2"
          viewBox="0 0 24 24"
        >
          <path d="M6 6l12 12M18 6L6 18" />
        </svg>
      </button>

      <img
        v-if="item.banner"
        alt=""
        class="team-slice__banner"
        :src="item.banner"
      >
      <span
        aria-hidden="true"
        class="team-slice__scrim"
      />

      <button
        class="team-slice__body"
        :aria-expanded="index === open"
        type="button"
        @click="choose(index)"
      >
        <span class="team-slice__heading">
          <span
            aria-hidden="true"
            class="team-slice__tick"
          />
          <span class="team-slice__name">{{ item.title }}</span>
          <span class="team-slice__count">{{ item.meta }}</span>
        </span>

        <span class="team-slice__roster">
          <slot
            :item="item"
            name="details"
          />
        </span>
      </button>
    </section>

    <!--
      A pane of its own rather than a control floating over the band: adding belongs to the
      band, and the band is the page. Narrow, because it is a way in rather than a thing to
      read.
    -->
    <section
      v-if="mayAdd"
      class="team-slice team-slice--add team-slice--last"
    >
      <button
        class="team-slice__body team-slice__add"
        :data-testid="`${testidPrefix}-add`"
        type="button"
        @click="emit('add')"
      >
        <span
          aria-hidden="true"
          class="team-slice__plus"
        >
          <svg
            fill="none"
            stroke="currentColor"
            stroke-width="2"
            viewBox="0 0 24 24"
          >
            <path d="M12 5v14M5 12h14" />
          </svg>
        </span>
        <span class="team-slice__add-label">{{ addLabel }}</span>
      </button>
    </section>
  </div>
</template>

<style scoped>
/*
 * Slices, not cards: full width, square, and cut apart on the diagonal so the seams read as
 * one band rather than as a row of boxes. The cut is a fixed number of pixels so the angle
 * does not change as a slice opens.
 */
.team-slices {
  --cut: 30px;

  display: flex;
  width: 100%;
  min-height: 22rem;
}

.team-slice {
  position: relative;
  flex: 1 1 0;
  min-width: 0;
  overflow: hidden;
  background-color: var(--color-surface);
  clip-path: polygon(var(--cut) 0, 100% 0, calc(100% - var(--cut)) 100%, 0 100%);
  margin-left: calc(var(--cut) * -1);
  transition: flex-grow 620ms cubic-bezier(0.22, 1, 0.36, 1);
}

.team-slice--first {
  clip-path: polygon(0 0, 100% 0, calc(100% - var(--cut)) 100%, 0 100%);
  margin-left: 0;
}

.team-slice--last {
  clip-path: polygon(var(--cut) 0, 100% 0, 100% 100%, 0 100%);
}

/* Hidden rather than transparent, for the same reason as the strip's: a see-through
   affordance still answers a click. */
.team-slice__edit {
  position: absolute;
  top: 10px;
  right: 12px;
  z-index: 3;
  visibility: hidden;
  display: grid;
  place-items: center;
  width: 26px;
  height: 26px;
  background: color-mix(in oklab, var(--color-void) 78%, transparent);
  border: 1px solid color-mix(in oklab, var(--accent) 55%, transparent);
  color: var(--color-chalk);
  cursor: pointer;
}

/* Beside the edit, not on top of it: two affordances on one slice read as a pair. */
.team-slice__drop {
  right: 44px;
}

.team-slice__drop:hover,
.team-slice__drop:focus-visible {
  border-color: #b03434;
  color: #ff9d9d;
}

.team-slice__edit svg {
  width: 14px;
  height: 14px;
}

.team-slice:hover .team-slice__edit,
.team-slice:focus-within .team-slice__edit {
  visibility: visible;
}

@media (hover: none) {
  .team-slice__edit {
    visibility: visible;
  }
}

.team-slice--add {
  flex: 0 0 auto;
  width: 8rem;
  background-color: var(--color-pit);
}

.team-slice__add {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  width: 100%;
  height: 100%;
  padding-left: var(--cut);
  color: var(--color-ash);
  cursor: pointer;
}

.team-slice__add:hover,
.team-slice__add:focus-visible {
  background-color: color-mix(in oklab, var(--accent) 16%, var(--color-pit));
  color: var(--color-chalk);
}

.team-slice__plus svg {
  width: 26px;
  height: 26px;
}

.team-slice__add-label {
  font-family: var(--font-display);
  font-size: 0.72rem;
  font-style: italic;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.team-slice--first.team-slice--last {
  clip-path: none;
}

.team-slice--open {
  flex-grow: 3.4;
  z-index: 1;
}

.team-slice__banner {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  filter: grayscale(70%) brightness(0.5);
  scale: 1.06;
  transition: filter 620ms ease, scale 900ms cubic-bezier(0.22, 1, 0.36, 1);
}

.team-slice--open .team-slice__banner {
  filter: grayscale(0%) brightness(0.72);
  scale: 1;
}

.team-slice__scrim {
  position: absolute;
  inset: 0;
  background:
    linear-gradient(to top, color-mix(in oklab, var(--color-void) 92%, transparent) 0%, transparent 62%),
    linear-gradient(to right, color-mix(in oklab, var(--color-void) 55%, transparent), transparent 55%);
}

.team-slice__body {
  position: relative;
  display: flex;
  height: 100%;
  width: 100%;
  flex-direction: column;
  justify-content: flex-end;
  gap: 0.75rem;
  padding: 1.5rem calc(var(--cut) + 0.5rem);
  text-align: left;
  cursor: pointer;
}

.team-slice__heading {
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
}

.team-slice__tick {
  width: 2rem;
  height: 3px;
  margin-bottom: 0.6rem;
  background-color: var(--accent);
  scale: 0.35 1;
  transform-origin: left center;
  transition: scale 520ms cubic-bezier(0.22, 1, 0.36, 1);
}

.team-slice--open .team-slice__tick {
  scale: 1;
}

.team-slice__name {
  font-family: var(--font-display);
  font-size: 1rem;
  line-height: 1.1;
  text-transform: uppercase;
  color: var(--color-chalk);
}

.team-slice--open .team-slice__name {
  font-size: 1.5rem;
}

.team-slice__count {
  font-size: 0.7rem;
  letter-spacing: 0.02em;
  color: var(--color-ash);
}

/*
 * The roster belongs to the open slice. A closed one keeps it in the document — it is one
 * button, and its label should say who is in the team — but gives it no room and no ink.
 */
.team-slice__roster {
  display: flex;
  flex-direction: column;
  gap: 0.65rem;
  overflow: hidden;
  max-height: 0;
  opacity: 0;
  transition: max-height 560ms cubic-bezier(0.22, 1, 0.36, 1), opacity 320ms ease;
}

.team-slice--open .team-slice__roster {
  max-height: 12rem;
  opacity: 1;
}

/* A roster clipped at its last line is worse than a taller slice. */
@media (max-width: 767px) {
  .team-slice--open .team-slice__roster {
    max-height: 22rem;
  }
}

/*
 * The rules below dress what a page renders into the details slot. That content is compiled
 * in the page's own scope, not this component's, so a plain scoped selector never matches it
 * — which left rosters running together as one line of text.
 */
:slotted(.team-slice__group) {
  display: block;
}

:slotted(.team-slice__group-label) {
  display: block;
  font-size: 0.6rem;
  letter-spacing: 0.18em;
  text-transform: uppercase;
  color: var(--color-ash);
}

:slotted(.team-slice__members) {
  display: flex;
  flex-wrap: wrap;
  gap: 0.35rem 1.5rem;
  margin-top: 0.3rem;
}

/*
 * One member, one block. Run along a line the handle and the name beside it read as two
 * players; stacked, with the handle leading, it is clear which is the person and which is
 * what they play under.
 */
:slotted(.team-slice__member) {
  display: flex;
  min-width: 0;
  flex-direction: column;
  line-height: 1.15;
}

:slotted(.team-slice__handle) {
  font-size: 0.95rem;
  color: var(--color-chalk);
}

:slotted(.team-slice__link) {
  align-self: flex-start;
  margin-top: 0.9rem;
  font-family: var(--font-display);
  font-size: 0.7rem;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--accent);
  transition: opacity 200ms ease;
}

:slotted(.team-slice__link):hover {
  opacity: 0.75;
}

:slotted(.team-slice__member-name) {
  font-size: 0.7rem;
  letter-spacing: 0.01em;
  color: color-mix(in oklab, var(--color-ash) 85%, transparent);
}

/* Beside the part they played rather than under it: it qualifies the role, it is not a
   second fact about the person. */
:slotted(.team-slice__member-role) {
  display: block;
  font-size: 0.7rem;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: color-mix(in oklab, var(--accent) 82%, var(--color-chalk));
}

/* A caption, so it is held to a couple of lines and the markdown inside it stays inline. */
:slotted(.team-slice__member-note) {
  display: block;
  margin-top: 0.15rem;
  max-width: 22rem;
  font-size: 0.72rem;
  line-height: 1.35;
  color: color-mix(in oklab, var(--color-ash) 92%, transparent);
}

:slotted(.team-slice__member-note p) {
  display: inline;
  margin: 0;
}

/* Stacked on a narrow screen, where a row of slices would leave each one a sliver. The cut
   turns with them so the seams still read as diagonal. */
@media (max-width: 767px) {
  .team-slices {
    --cut: 22px;

    flex-direction: column;
    min-height: 0;
  }

  /* Hidden rather than transparent, for the same reason as the strip's: a see-through
   affordance still answers a click. */
.team-slice__edit {
  position: absolute;
  top: 10px;
  right: 12px;
  z-index: 3;
  visibility: hidden;
  display: grid;
  place-items: center;
  width: 26px;
  height: 26px;
  background: color-mix(in oklab, var(--color-void) 78%, transparent);
  border: 1px solid color-mix(in oklab, var(--accent) 55%, transparent);
  color: var(--color-chalk);
  cursor: pointer;
}

/* Beside the edit, not on top of it: two affordances on one slice read as a pair. */
.team-slice__drop {
  right: 44px;
}

.team-slice__drop:hover,
.team-slice__drop:focus-visible {
  border-color: #b03434;
  color: #ff9d9d;
}

.team-slice__edit svg {
  width: 14px;
  height: 14px;
}

.team-slice:hover .team-slice__edit,
.team-slice:focus-within .team-slice__edit {
  visibility: visible;
}

@media (hover: none) {
  .team-slice__edit {
    visibility: visible;
  }
}

.team-slice--add {
    width: 100%;
    min-height: 5.5rem;
  }

  .team-slice__add {
    flex-direction: row;
    padding-left: 0;
  }

  .team-slice {
    clip-path: polygon(0 var(--cut), 100% 0, 100% calc(100% - var(--cut)), 0 100%);
    margin-left: 0;
    margin-top: calc(var(--cut) * -1);
    min-height: 8.5rem;
  }

  .team-slice--first {
    clip-path: polygon(0 0, 100% 0, 100% calc(100% - var(--cut)), 0 100%);
    margin-top: 0;
  }

  .team-slice--last {
    clip-path: polygon(0 var(--cut), 100% 0, 100% 100%, 0 100%);
  }

  /* Stacked, an open slice needs room for its roster rather than a share of a row. */
  .team-slice--open {
    flex-grow: 1;
    min-height: 17rem;
  }

  .team-slice__body {
    padding: 1.75rem 1.25rem;
  }
}

@media (prefers-reduced-motion: reduce) {
  .team-slice,
  .team-slice__banner,
  .team-slice__tick,
  .team-slice__roster {
    transition: none;
  }
}
</style>
