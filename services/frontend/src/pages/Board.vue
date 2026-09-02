<script lang="ts" setup>
import {computed} from "vue"
import {useRoute, useRouter} from "vue-router"
import {Motion} from "motion-v"
import Island from "@/components/island/Island.vue"
import Timeline from "@/components/island/Timeline.vue"
import CallBand from "@/components/island/CallBand.vue"
import {useMotionAllowed} from "@/components/island/useMotionAllowed"
import BoardMemberRow from "@/components/common/rows/BoardMemberRow.vue"
import BoardBand from "@/domains/boards/island/BoardBand.vue"
import {BOARD_CALL} from "@/domains/boards/island/boardCall"
import {useBoards} from "@/domains/boards/island/useBoards"
import {
  academicYear,
  boardEyebrow,
  boardInRoute,
  boardName,
  boardStops,
  seatsInOrder,
} from "@/domains/boards"
import {seatTitle, type Board, type BoardSeat} from "@/domains/boards/adapters/boards"
import {$require} from "@/plugins/require"

/**
 * The association's own history, as a line of boards.
 *
 * The page opens on the board in office and a strip across the top carries every board there has
 * been; choosing one shows it and puts it in the url, so a board can be linked to and the back
 * button walks back through the years. Which board is in office and which has not taken office
 * yet are read out of the dates by the board domain — the page asks, it does not work it out.
 *
 * The seats still render through the row the old page used. #931 replaces them with rows that
 * open, and doing it here would be doing that ticket's job with none of its tests.
 */
defineOptions({name: "BoardPage"})

const route = useRoute()
const router = useRouter()
const motion = useMotionAllowed()

const {boards, loading, inOffice} = useBoards()

/**
 * The board being read: the one the url names, else the one in office.
 *
 * A url naming a board nobody has recorded falls through to the board in office rather than to
 * an empty page — a link can outlive the board it named, and a stale link is not worth a blank
 * page. The board in office is never a candidate, so a board written down before it takes office
 * is reachable on the strip and is never what a visitor arrives on.
 */
const shown = computed<Board | null>(() => {
  const number = boardInRoute(route)
  const named = number == null ? null : boards.value.find(board => board.number === number)
  return named ?? inOffice.value ?? null
})

/** Every board is a stop, in office and candidate marked, oldest first. */
const stops = computed(() => boardStops(boards.value))

/**
 * The board's own colour, or the association's blue where it has none.
 *
 * No board has one recorded yet, so every board draws blue today. The whole page reads this one
 * value — the lit stretch of the strip, the band, the cheer and the focus ring — so a colour
 * appearing on a board is the only change that has to happen for all four to follow it.
 */
const accent = computed(() => shown.value?.accent?.trim() || "var(--color-brand)")

/** The board being read goes in the url, pushed, so the back button returns to the one before. */
const chooseBoard = (number: number) => {
  void router.push({query: {...route.query, board: String(number)}})
}

const eyebrow = computed(() =>
  (shown.value ? boardEyebrow(shown.value.number, shown.value.startDate, shown.value.endDate) : ""))

/**
 * The name the board chose for itself, and nothing where it chose none.
 *
 * A board with no recorded name is named from its number on the strip, because a stop may not
 * read as blank — but here the eyebrow above has just said `BOARD IV`, and a heading repeating it
 * is the placeholder this page is meant not to have.
 */
const ownName = computed(() => shown.value?.name?.trim() ?? "")
const cheer = computed(() => shown.value?.cheer?.trim() ?? "")
const description = computed(() => shown.value?.description?.trim() ?? "")

/** What the band's photograph is of, for a reader who is not being shown it. */
const photoLabel = computed(() => {
  const board = shown.value
  if (!board) return ""
  const year = academicYear(board.startDate, board.endDate)
  const named = boardName(board.number, board.name)
  return year ? `${named}, ${year}` : named
})

/** Chair first, then the rest by seniority: the order the association thinks in. */
const seats = computed<BoardSeat[]>(() => seatsInOrder(shown.value?.members ?? []))

/**
 * Where a portrait is served from.
 *
 * The stored picture where there is one, and the frontend's own assets directory where a seat
 * still points at a file name. The two answer side by side until #935 takes the directory out.
 * The widest stored copy rather than the master: this row draws a portrait a few hundred pixels
 * across, and a portrait's ladder tops out well below what somebody uploaded.
 */
const portraitOf = (seat: BoardSeat): string => {
  const stored = seat.portrait
  if (stored) return stored.renditions[stored.renditions.length - 1]?.url ?? stored.url
  return seat.image ? $require(`@/assets/${seat.image}`) : ""
}

const rowFor = (seat: BoardSeat) => ({
  name: seatTitle(seat),
  title: seat.role,
  description: seat.description ?? undefined,
  image: portraitOf(seat),
})

/**
 * A board arriving, which is something a reader watches happen.
 *
 * Keyed on the board, so moving down the strip moves the band and the identity with it rather
 * than swapping the text under a picture that stayed still.
 */
const entrance = computed(() => ({
  initial: motion.decorative.value ? {opacity: 0, y: 14} : {opacity: 1},
  animate: {opacity: 1, y: 0},
  transition: {duration: motion.duration(0.45), ease: [0.22, 1, 0.36, 1] as const},
}))
</script>

<template>
  <v-main>
    <island testid="board-island">
      <header class="island-header relative isolate overflow-hidden">
        <div
          aria-hidden="true"
          class="island-header__blob pointer-events-none absolute -top-32 -left-24 h-80 w-[36rem] rounded-full bg-brand opacity-[0.18] blur-[90px]"
        />
        <div class="relative mx-auto w-full max-w-6xl px-5 pt-7 pb-6 sm:px-8 sm:pt-9 sm:pb-7">
          <p class="font-body text-[11px] font-medium tracking-[0.3em] text-eyebrow uppercase">
            Blueshell Boards
          </p>
          <h1 class="mt-2.5 max-w-2xl font-display text-2xl leading-[1.1] uppercase sm:text-4xl">
            Every year of the association,<br>
            <span class="text-brand">and who ran it</span>
          </h1>
          <p class="mt-3 max-w-xl font-body text-sm leading-relaxed text-ash">
            A board runs Blueshell for one academic year and hands over in the autumn. The line
            below is every board the association has had: choose one to read who sat on it, what
            it called itself and what it shouted. The board in office is marked, and it is where
            this page opens.
          </p>
        </div>
      </header>

      <!-- No room of its own above or below: the strip is a slice of the page, and a slice
           meets the one before it. -->
      <section
        v-if="stops.length > 1"
        class="w-full"
        data-testid="board-boards"
      >
        <timeline
          :accent="accent"
          pan-back-label="Show earlier boards"
          pan-on-label="Show later boards"
          :selected-id="shown?.number ?? null"
          :stops="stops"
          testid-prefix="board"
          @select="chooseBoard"
        />
      </section>

      <div
        class="board-page"
        :style="{'--accent': accent}"
      >
        <!-- Only while there is nothing to show: the band keeps its height either way, so
             nothing below it moves once the boards arrive. -->
        <div
          v-if="loading && !shown"
          class="flex min-h-[22rem] w-full animate-pulse bg-surface motion-reduce:animate-none"
          data-testid="board-loading"
        />

        <p
          v-else-if="!shown"
          class="flex min-h-[22rem] w-full items-center justify-center bg-surface text-center font-body text-sm text-ash"
          data-testid="board-empty"
        >
          No boards are recorded yet.
        </p>

        <Motion
          v-else
          :key="shown.number"
          v-bind="entrance"
        >
          <board-band
            :label="photoLabel"
            :number="shown.number"
            :photo="shown.photo"
          />

          <section
            class="mx-auto w-full max-w-6xl px-5 pt-7 pb-8 sm:px-8"
            data-testid="board-identity"
          >
            <p
              class="font-body text-[11px] font-medium tracking-[0.3em] text-eyebrow uppercase"
              data-testid="board-eyebrow"
            >
              {{ eyebrow }}
            </p>
            <h2
              v-if="ownName"
              class="mt-2 font-display text-2xl leading-[1.1] uppercase sm:text-4xl"
              data-testid="board-name"
            >
              {{ ownName }}
            </h2>
            <!-- Shouted rather than said, so it is set in the display face in the board's own
                 colour and never folded into the prose beside it. -->
            <p
              v-if="cheer"
              class="board-cheer mt-3 font-display text-lg sm:text-2xl"
              data-testid="board-cheer"
            >
              {{ cheer }}
            </p>
            <p
              v-if="description"
              class="mt-3 max-w-2xl font-body text-sm leading-relaxed text-ash"
              data-testid="board-description"
            >
              {{ description }}
            </p>
          </section>

          <section
            v-if="seats.length > 0"
            class="mx-auto w-full max-w-4xl px-4 pb-10 sm:px-8"
            data-testid="board-seats"
          >
            <board-member-row
              v-for="(seat, index) in seats"
              :key="seat.id"
              class="my-10"
              :member="rowFor(seat)"
              :reverse="index % 2 === 1"
            />
          </section>

          <p
            v-else
            class="mx-auto w-full max-w-6xl px-5 pt-2 pb-10 font-body text-sm text-ash sm:px-8"
            data-testid="board-no-seats"
          >
            No seats are recorded on this board yet.
          </p>
        </Motion>
      </div>

      <call-band v-bind="BOARD_CALL" />
    </island>
  </v-main>
</template>

<style scoped>
/*
 * The board's colour, made readable against whichever ground the reader is on.
 *
 * Mixed towards the ink of the theme rather than used raw: chalk is near-white in the dark half
 * and near-black in the light one, so one formula lifts a dark colour off a dark page and drops a
 * pale one onto a pale page. The two mixes differ because the light half needs the heavier hand —
 * a historical colour nobody vetted has to be readable there without being checked by hand.
 *
 * Declared on the element that carries `--accent`: a custom property built out of another is
 * substituted where it is declared, so stating this any higher would freeze it on the blue.
 */
.board-page {
  --accent-ink: color-mix(in oklab, var(--accent) 86%, var(--color-chalk));
}

:where([data-theme="light"]) .board-page {
  --accent-ink: color-mix(in oklab, var(--accent) 62%, var(--color-chalk));
}

.board-cheer {
  color: var(--accent-ink);
}
</style>
