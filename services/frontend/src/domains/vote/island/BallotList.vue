<script lang="ts" setup>
import {nextTick, ref} from "vue"
import IconButton from "@/components/island/IconButton.vue"
import type {AnnualVote} from "../adapters/vote"
import {pointsAt, type BallotPick} from "../ballot"
import PickRow from "./PickRow.vue"
import PlaceEditor from "./PlaceEditor.vue"

/**
 * Every place of a top, filled or free, each with what it is worth. The next free place is filled
 * in where it stands. A pick moves by dragging its handle, by the arrow keys on the handle, or by
 * its up and down buttons.
 */
defineOptions({name: "VoteBallotList"})

const props = defineProps<{
  vote: AnnualVote
  picks: BallotPick[]
}>()

const emit = defineEmits<{
  pick: [pick: BallotPick]
  problem: [reason: string]
  move: [index: number, step: -1 | 1]
  reorder: [from: number, to: number]
  remove: [index: number]
}>()

const list = ref<HTMLOListElement | null>(null)
const held = ref<number | null>(null)
const lift = ref(0)
let grab = 0

const rows = (): HTMLElement[] => [...(list.value?.querySelectorAll<HTMLElement>("[data-pick]") ?? [])]

// Where the held row would sit under the pointer, against its own place in the list.
function follow(pointerY: number) {
  const row = rows()[held.value!]
  if (!row || !list.value) return
  lift.value = pointerY - list.value.getBoundingClientRect().top - grab - row.offsetTop
}

function onGrab(event: PointerEvent, index: number) {
  if (event.button !== 0) return
  const row = rows()[index]
  if (!row) return
  // Held, a press would start selecting the page's text as the pointer moves.
  event.preventDefault()
  ;(event.currentTarget as HTMLElement).setPointerCapture(event.pointerId)
  held.value = index
  grab = event.clientY - row.getBoundingClientRect().top
  lift.value = 0
}

async function onDrag(event: PointerEvent) {
  if (held.value === null || !list.value) return
  const top = list.value.getBoundingClientRect().top
  const y = event.clientY - top
  const all = rows()
  let to = held.value
  all.forEach((row, index) => {
    if (index === held.value) return
    const middle = row.offsetTop + row.offsetHeight / 2
    if (index < held.value! && y - grab < middle && index < to) to = index
    if (index > held.value! && y - grab + all[held.value!]!.offsetHeight > middle && index > to) to = index
  })
  if (to !== held.value) {
    emit("reorder", held.value, to)
    held.value = to
    await nextTick()
  }
  follow(event.clientY)
}

function onDrop() {
  held.value = null
  lift.value = 0
}

function onKey(event: KeyboardEvent, index: number) {
  const step = event.key === "ArrowUp" ? -1 : event.key === "ArrowDown" ? 1 : 0
  if (step === 0) return
  event.preventDefault()
  const to = index + step
  if (to < 0 || to >= props.picks.length) return
  emit("move", index, step)
  void nextTick(() => list.value?.querySelectorAll<HTMLElement>(".ballot__grip")[to]?.focus())
}
</script>

<template>
  <ol
    ref="list"
    class="ballot"
    data-testid="vote-ballot"
  >
    <li
      v-for="(pick, index) in picks"
      :key="pick.candidate.id"
      class="ballot__place"
      :class="{'ballot__place--held': held === index}"
      data-pick
      data-testid="vote-pick"
      :style="held === index ? {translate: `0 ${lift}px`} : undefined"
    >
      <pick-row
        :cover="pick.candidate.cover ?? null"
        :dragging="held === index"
        :game="pick.candidate.name"
        :place="index + 1"
        :points="pointsAt(vote.points, index)"
        :track="pick.track?.title"
      >
        <template #end>
          <button
            :aria-label="`Move ${pick.candidate.name}: drag, or press the arrow keys`"
            class="ballot__grip"
            :data-testid="`vote-pick-grip-${index}`"
            type="button"
            @keydown="onKey($event, index)"
            @lostpointercapture="onDrop"
            @pointercancel="onDrop"
            @pointerdown="onGrab($event, index)"
            @pointermove="onDrag"
            @pointerup="onDrop"
          >
            <svg
              aria-hidden="true"
              fill="currentColor"
              viewBox="0 0 24 24"
            ><circle
              cx="9"
              cy="6"
              r="1.6"
            /><circle
              cx="15"
              cy="6"
              r="1.6"
            /><circle
              cx="9"
              cy="12"
              r="1.6"
            /><circle
              cx="15"
              cy="12"
              r="1.6"
            /><circle
              cx="9"
              cy="18"
              r="1.6"
            /><circle
              cx="15"
              cy="18"
              r="1.6"
            /></svg>
          </button>
          <span class="ballot__steps">
            <icon-button
              :disabled="index === 0"
              :label="`Move ${pick.candidate.name} up`"
              :testid="`vote-pick-up-${index}`"
              @click="$emit('move', index, -1)"
            >
              <svg
                aria-hidden="true"
                fill="none"
                stroke="currentColor"
                stroke-linecap="round"
                stroke-linejoin="round"
                stroke-width="1.6"
                viewBox="0 0 24 24"
              ><path d="M12 19V5M6 11l6-6 6 6" /></svg>
            </icon-button>
            <icon-button
              :disabled="index === picks.length - 1"
              :label="`Move ${pick.candidate.name} down`"
              :testid="`vote-pick-down-${index}`"
              @click="$emit('move', index, 1)"
            >
              <svg
                aria-hidden="true"
                fill="none"
                stroke="currentColor"
                stroke-linecap="round"
                stroke-linejoin="round"
                stroke-width="1.6"
                viewBox="0 0 24 24"
              ><path d="M12 5v14M6 13l6 6 6-6" /></svg>
            </icon-button>
          </span>
          <icon-button
            danger
            :label="`Take ${pick.candidate.name} off your top ${vote.points.length}`"
            :testid="`vote-pick-remove-${index}`"
            @click="$emit('remove', index)"
          >
            <svg
              aria-hidden="true"
              fill="none"
              stroke="currentColor"
              stroke-linecap="round"
              stroke-width="1.6"
              viewBox="0 0 24 24"
            ><path d="M6 6l12 12M18 6 6 18" /></svg>
          </icon-button>
        </template>
      </pick-row>
    </li>
    <li
      v-for="index in vote.points.length - picks.length"
      :key="`free-${index}`"
      :class="{'ballot__place--editing': index === 1}"
      data-testid="vote-free"
    >
      <pick-row
        :place="picks.length + index"
        :points="pointsAt(vote.points, picks.length + index - 1)"
      >
        <template #free>
          <place-editor
            v-if="index === 1"
            :vote="vote"
            @pick="$emit('pick', $event)"
            @problem="$emit('problem', $event)"
          />
          <template v-else>
            Free
          </template>
        </template>
      </pick-row>
    </li>
  </ol>
</template>

<style scoped>
.ballot {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.ballot__place--held {
  position: relative;
  z-index: 1;
  user-select: none;
}

/* Two fields stand in the place being filled, so the row, and with it its cut, is two fields tall. */
.ballot__place--editing :deep(.pick-row) {
  --row-h: 9.6rem;
}

.ballot__place:not(.ballot__place--held) {
  transition: translate 160ms ease;
}

.ballot__grip {
  display: grid;
  place-items: center;
  width: 2.25rem;
  height: 2.25rem;
  color: var(--color-ash);
  cursor: grab;
  touch-action: none;
}

.ballot__grip:hover {
  color: var(--color-chalk);
}

.ballot__place--held .ballot__grip {
  cursor: grabbing;
  color: var(--color-chalk);
}

.ballot__grip svg {
  width: 1.15rem;
  height: 1.15rem;
}

.ballot__steps {
  display: flex;
}

@media (max-width: 600px) {
  /* No room for the steps on a phone: the handle drags, and its arrow keys step. */
  .ballot__steps {
    display: none;
  }

  /* The fields take the cover's room, so they keep a width a name can be typed in. */
  .ballot__place--editing :deep(.pick-row) {
    --row-h: 9rem;
    grid-template-areas:
      "place words"
      "points words";
    grid-template-columns: 3.4rem minmax(0, 1fr);
    gap: 0.4rem 0.6rem;
  }

  .ballot__place--editing :deep(.pick-row__place) {
    grid-area: place;
    align-self: end;
  }

  .ballot__place--editing :deep(.pick-row__words) {
    grid-area: words;
  }

  .ballot__place--editing :deep(.pick-row__points) {
    grid-area: points;
    align-self: start;
    min-width: 0;
    padding-inline: 0.5rem;
  }

  .ballot__place--editing :deep(.pick-row__cover) {
    display: none;
  }
}
</style>
