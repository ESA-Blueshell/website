<script lang="ts" setup>
import {computed, ref, watch} from "vue"
import BandSwipe from "@/components/island/BandSwipe.vue"
import type {BandDirection} from "@/components/island/stripAxis"
import {directionBetween, seasonsEitherSide} from "./seasonAxis"
import type {Season} from "../adapters/esports"

/**
 * A season change as a pass across the screen, in the island's own band swipe.
 *
 * What is left here is the season vocabulary: which of two seasons is later, and which two lie
 * either side of the one being read, is knowledge about seasons, so it is worked out on this side
 * and the island is handed the answer. The pinned class is here too: the dark treatment is the
 * esports bands' own, not the shared band's.
 */
defineOptions({name: "SeasonSwipe"})

const props = withDefaults(defineProps<{
  /**
   * The season whose contents are shown.
   *
   * The season that has *arrived*, not the one that was clicked. The strip answers a click at
   * once; the band waits, and then moves once.
   */
  season: Season | null
  /**
   * The season a committed gesture has asked for and not yet received, where there is one.
   *
   * Only for working out where a *further* gesture goes: the contents drawn are still `season`.
   * The island keeps this bookkeeping and the page hands it back down, because which season lies
   * beside another is seasons' knowledge and has to be answered on this side.
   */
  pending?: number | null
  /**
   * The seasons a finger may travel to, which is the strip's own list.
   *
   * The gesture offers exactly what the nodes above it offer and nothing else, so that the two
   * ways of changing season reach the same places: a visitor's strip carries the seasons
   * something was fielded in, an editor's carries them all, and a game's page carries the season
   * being read whether or not the game played it.
   */
  seasons?: Season[]
  /**
   * A season the page has just said it cannot show, which releases a gesture waiting on it.
   *
   * Both esports pages fetch a season before they can draw it, so both can fail to answer one a
   * finger has already carried the screen for. See the `asked` ref in the band swipe for what
   * happens to a track that is never answered.
   */
  refused?: number | null
}>(), {seasons: () => [], refused: null, pending: null})

const emit = defineEmits<{
  (event: "travel", seasonId: number): void
  /** The gesture has begun, and these are the seasons it may be heading for. */
  (event: "reaching", seasonIds: number[]): void
}>()

/**
 * Which way the page last travelled.
 *
 * Set before the swap rather than after it: the direction has to be known while the contents
 * arriving are being rendered, since it is what decides which side they arrive from.
 */
const direction = ref<BandDirection>("same")

let shown: Season | null = null
watch(() => props.season, (next) => {
  const to = next ?? null
  direction.value = directionBetween(shown, to)
  shown = to
})

/** The seasons either side of the one being read, which is the domain's answer, not the island's. */
/**
 * The season a second gesture steps from, which is the one on screen rather than the one drawn.
 *
 * While a committed gesture waits for its answer, what fills the window is the season it asked
 * for; the season *drawn* is still the one behind it. Stepping from the drawn one made a second
 * swipe skip a season: from Autumn 2024, back to Spring 2024 and forward again landed on Spring
 * 2025, over the top of the very season the visitor could see. A gesture steps from what is in
 * front of the reader.
 */
const onScreen = computed(() =>
  props.seasons.find(one => one.id === props.pending) ?? props.season)

const either = computed(() => seasonsEitherSide(props.seasons, onScreen.value))

/**
 * Which season a stop is.
 *
 * The island deals in stops: it hands back the stop whose contents it wants drawn, and says
 * nothing about what a stop is, which is the whole of why the shared band carries no season
 * vocabulary. Turning one back into a season is seasons' knowledge, so it is answered here and
 * the pages above go on being handed a season, exactly as they are today.
 *
 * The season being read answers for itself rather than being looked up, because a game's page
 * can stand on a season its own list does not carry.
 */
const seasonAt = (stop: string | number | null): Season | null => {
  if (stop == null) return null
  if (stop === props.season?.id) return props.season
  return props.seasons.find(one => one.id === stop) ?? null
}

/**
 * A gesture beginning: both neighbours are asked about now, before it can have committed.
 *
 * Both rather than the one the finger set off towards, because a finger that crosses back through
 * where it started is heading for the other one, and the whole point of asking this early is that
 * the answer is already in hand when it does.
 */
const reaching = () => {
  const wanted = [either.value.past?.id, either.value.future?.id]
    .filter((id): id is number => id != null)
  if (wanted.length > 0) emit("reaching", wanted)
}
</script>

<template>
  <band-swipe
    class="band-swipe--pinned"
    :direction="direction"
    :future="either.future?.id ?? null"
    :past="either.past?.id ?? null"
    :refused="refused"
    :stop="season?.id ?? null"
    testid="season-swipe"
    @reaching="reaching"
    @travel="stop => emit('travel', Number(stop))"
  >
    <template #default="{stop}">
      <slot :season="seasonAt(stop)" />
    </template>
  </band-swipe>
</template>
