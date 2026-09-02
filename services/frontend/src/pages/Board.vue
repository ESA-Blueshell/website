<script lang="ts" setup>
import {computed, ref, watch} from "vue"
import {useRoute, useRouter} from "vue-router"
import {Motion} from "motion-v"
import Island from "@/components/island/Island.vue"
import Timeline from "@/components/island/Timeline.vue"
import BandRule from "@/components/island/BandRule.vue"
import BandSwipe, {type BandDirection} from "@/components/island/BandSwipe.vue"
import CallBand from "@/components/island/CallBand.vue"
import {useMotionAllowed} from "@/components/island/useMotionAllowed"
import SliceBand from "@/components/island/SliceBand.vue"
import {sizeOf, srcsetOf} from "@/components/island/pictures"
import BoardBand from "@/domains/boards/island/BoardBand.vue"
import BoardDialog from "@/domains/boards/island/BoardDialog.vue"
import {BOARD_CALL} from "@/domains/boards/island/boardCall"
import {useBoards} from "@/domains/boards/island/useBoards"
import {useMayEditBoards} from "@/domains/boards/island/useMayEditBoards"
import {
  academicYear,
  boardEyebrow,
  boardInRoute,
  boardName,
  boardStops,
  nextBoardNumber,
  membersInOrder,
  travelBetween,
} from "@/domains/boards"
import {memberTitle, type Board, type BoardMember} from "@/domains/boards/adapters/boards"
import BoardMemberDialog from "@/domains/boards/island/BoardMemberDialog.vue"
import {$require} from "@/plugins/require"

/**
 * The association's own history, as a line of boards.
 *
 * The page opens on the board in office and a strip across the top carries every board there has
 * been; choosing one shows it and puts it in the url, so a board can be linked to and the back
 * button walks back through the years. Which board is in office and which has not taken office
 * yet are read out of the dates by the board domain: the page asks, it does not work it out.
 */
defineOptions({name: "BoardPage"})

const route = useRoute()
const router = useRouter()
const motion = useMotionAllowed()

const {boards, loading, inOffice, refresh} = useBoards()

/**
 * The board being read: the one the url names, else the one in office.
 *
 * A url naming a board nobody has recorded falls through to the board in office rather than to
 * an empty page: a link can outlive the board it named, and a stale link is not worth a blank
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
 * value (the lit stretch of the strip, the band, the cheer and the focus ring) so a colour
 * appearing on a board is the only change that has to happen for all four to follow it.
 */
const accent = computed(() => shown.value?.accent?.trim() || "var(--color-brand)")

/** The board being read goes in the url, pushed, so the back button returns to the one before. */
const chooseBoard = (number: number) => {
  void router.push({query: {...route.query, board: String(number)}})
}

/**
 * Which way the page travels when the board changes, so the band can leave the way the reader
 * is going and the board they chose can arrive from the other side.
 *
 * Set before the swap rather than after it: the swipe reads it while the arriving board is
 * being rendered, since it is what decides which side that board comes in from. Which board is
 * later is the domain's answer, not the island's.
 */
const travel = ref<BandDirection>("same")
let travelledTo: Board | null = null

watch(shown, (next) => {
  travel.value = travelBetween(travelledTo, next)
  travelledTo = next ?? null
})

const eyebrow = computed(() =>
  (shown.value ? boardEyebrow(shown.value.number, shown.value.startDate, shown.value.endDate) : ""))

/**
 * The name the board chose for itself, and nothing where it chose none.
 *
 * A board with no recorded name is named from its number on the strip, because a stop may not
 * read as blank. But here the eyebrow above has just said `BOARD IV`, and a heading repeating it
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
const members = computed<BoardMember[]>(() => membersInOrder(shown.value?.members ?? []))

/**
 * Where a portrait is served from.
 *
 * The stored picture where there is one, and the frontend's own assets directory where a member
 * still points at a file name. The two answer side by side until #935 takes the directory out.
 * The widest stored copy rather than the master, because this is only what a browser falls back
 * to: the widths themselves go beside it and the row is drawn a plate wide, not a portrait wide.
 */
const portraitOf = (member: BoardMember): string => {
  const stored = member.portrait
  if (stored) return stored.renditions[stored.renditions.length - 1]?.url ?? stored.url
  return member.image ? $require(`@/assets/${member.image}`) : ""
}

/**
 * Each member as the slice that draws it: the face, the name the history published, and what
 * they were. The blurb is fetched by id when a slice opens rather than carried on every one.
 *
 * The name is composed by the domain rather than here: `memberTitle` puts the nickname back
 * between the names, which is the one string a reader has always been shown. The blurb is handed
 * over as absent rather than as an empty line, so a member with nothing written about them shows
 * no blurb rather than an empty paragraph.
 */
const memberSlices = computed(() => members.value.map(member => ({
  id: member.id,
  title: memberTitle(member),
  meta: member.role,
  banner: portraitOf(member),
  srcset: srcsetOf(member.portrait),
  // Only a member who wrote something opens onto anything. Where nobody on the board did, the
  // band settles on nothing and stands still rather than growing onto an empty panel.
  expandable: Boolean(member.description?.trim()),
  ...sizeOf(member.portrait),
})))

/** What a member wrote about themselves, by the id the band hands back. */
const blurbOf = (id: number | string): string | undefined =>
  members.value.find(member => member.id === Number(id))?.description?.trim() || undefined

/**
 * A board arriving, which is something a reader watches happen.
 *
 * Unkeyed: the pass above remounts it, so this plays as a board arrives rather than being
 * replayed every time the page re-asks about the one already on screen.
 */
const entrance = computed(() => ({
  initial: motion.decorative.value ? {opacity: 0, y: 14} : {opacity: 1},
  animate: {opacity: 1, y: 0},
  transition: {duration: motion.duration(0.45), ease: [0.22, 1, 0.36, 1] as const},
}))

/**
 * Whether this reader may correct the history, which decides what is offered and nothing else.
 *
 * A visitor is shown the years and none of the machinery. The api refuses what it refuses
 * either way: hiding a pencil is not a guard, and this page does not treat it as one.
 */
const mayEdit = useMayEditBoards()

/** The number a board added would take, read off the line rather than remembered. */
const nextNumber = computed(() => nextBoardNumber(boards.value))

/** The board being corrected, or nothing while one is being added. */
const editing = ref<Board | null>(null)
const editorOpen = ref(false)

const editBoard = (number: number) => {
  editing.value = boards.value.find(board => board.number === number) ?? null
  editorOpen.value = true
}

// Nothing to fill the form from: the dialog opens on the suggested number and writes a board.
const addBoard = () => {
  editing.value = null
  editorOpen.value = true
}

/**
 * A board saved is a board re-read: the timeline, the band and the identity all draw from the
 * one list, so asking again is the whole of showing the correction.
 *
 * A board nobody was looking at, one just written down or one whose number has just changed,
 * is then shown, because somebody who has just described a board wants to see it.
 */
const boardSaved = async (saved: Board) => {
  await refresh()
  if (saved.number !== shown.value?.number) {
    void router.push({query: {...route.query, board: String(saved.number)}})
  }
}

/**
 * A board that has gone takes its stop on the timeline with it.
 *
 * The url is emptied of it first: a board named in the url that nobody has recorded falls
 * through to the board in office, so the page would recover either way. But a link left
 * pointing at a board that has been removed is a link that lies.
 */
const boardRemoved = async () => {
  const query = {...route.query}
  delete query.board
  void router.push({query})
  await refresh()
}

/**
 * The membership being filled in, and whether the dialog is open on one.
 *
 * The membership itself rather than its id, so the dialog is handed what the page already read
 * and asks the api nothing to open. Nothing is held for one being added: the dialog fills its
 * dates from the board's own term instead.
 */
const memberOpen = ref(false)
const editingMember = ref<BoardMember | null>(null)

/** The rows name a member by whatever id they were handed, which here is always the member's. */
const editMember = (id: number | string) => {
  editingMember.value = members.value.find(member => member.id === id) ?? null
  memberOpen.value = true
}

const addMember = () => {
  editingMember.value = null
  memberOpen.value = true
}

/** A member written down, corrected or removed is read again, so the page shows what was saved. */
const memberSaved = () => {
  void refresh()
}
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
            The boards who made Blueshell<br>
            <span class="text-brand">what it is</span>
          </h1>
          <p class="mt-3 max-w-xl font-body text-sm leading-relaxed text-ash">
            A board runs Blueshell for a year, and a board year runs with the academic one: the events, the money, the lounge and the games. Every board does its best to keep Blueshell as open and welcoming as it can be, and to put on plenty of events where fellow gamers can meet one another.
          </p>
        </div>
      </header>

      <!-- No room of its own above or below: the strip is a slice of the page, and a slice
           meets the one before it. -->
      <!-- To somebody who may edit, the strip is where a board is added, so it stands even for
           a line of one or of none. To a visitor a strip of one says nothing the page below it
           does not already say. -->
      <section
        v-if="stops.length > 1 || mayEdit"
        class="w-full"
        data-testid="board-boards"
      >
        <timeline
          :accent="accent"
          add-label="Add a board"
          :may-edit="mayEdit"
          pan-back-label="Show earlier boards"
          pan-on-label="Show later boards"
          :selected-id="shown?.number ?? null"
          :stops="stops"
          testid-prefix="board"
          @add="addBoard"
          @edit="editBoard"
          @select="chooseBoard"
        />
      </section>

      <div
        class="board-page"
        :style="{'--accent': accent}"
      >
        <!--
          The board being read travels: choosing another on the line sends this one off the way
          the reader is going and brings that one in from the other side. What the board holds
          travels as one thing, the banner, the strip and the faces alike, and the line above
          stays where it is, because it is the thing being travelled along.
        -->
        <band-swipe
          :direction="travel"
          :stop="shown?.number ?? null"
          testid="board-swipe"
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
            v-bind="entrance"
          >
            <board-band
              :cheer="cheer"
              :description="description"
              :eyebrow="eyebrow"
              :label="photoLabel"
              :may-add-photo="mayEdit"
              :name="ownName"
              :photo="shown.photo"
              @add-photo="editBoard(shown.number)"
            />

            <!-- The board and the people on it are two photographic bands, and a strip of the
                 page between them is what keeps them from reading as one picture. -->
            <band-rule testid="board-rule" />

            <!-- Also where a board has nobody on it yet, so long as the reader may add
                 somebody: the way in is at the end of the stack, and an empty stack is exactly
                 where it is needed. -->
            <section
              v-if="members.length > 0 || mayEdit"
              class="w-full"
              data-testid="board-members"
            >
              <!-- The same band the games are drawn in, wearing its other layout: a face holds
                     the left of each slice and the words start on it, so a member reads as a
                     person rather than as a row in a table. -->
              <!--
                The association's blue rather than the board's colour. The board's colour is
                what the line and the banner are: the board itself, and the fact of which board
                it is. A row of faces is the people, and lighting six panels in a colour a board
                chose says that colour again where the page has already said it twice.
              -->
              <slice-band
                accent="var(--color-brand)"
                add-label="Add a member"
                empty-label="No members are recorded on this board yet"
                :items="memberSlices"
                layout="aside"
                :may-add="mayEdit"
                :may-edit="mayEdit"
                testid-prefix="board-member"
                @add="addMember"
                @edit="editMember"
              >
                <template #details="{item}">
                  <p
                    v-if="blurbOf(item.id)"
                    class="board-member__blurb"
                    :data-testid="`board-member-blurb-${item.id}`"
                  >
                    {{ blurbOf(item.id) }}
                  </p>
                </template>
              </slice-band>
            </section>

            <p
              v-else
              class="mx-auto w-full max-w-6xl px-5 pt-2 pb-10 font-body text-sm text-ash sm:px-8"
              data-testid="board-no-members"
            >
              No members are recorded on this board yet.
            </p>

            <!-- And the same rule under the faces, mirrored, so the band is held between two
                 marks rather than closed with a copy of the one that opened it. -->
            <band-rule
              mirrored
              testid="board-rule-foot"
            />
          </Motion>

          <!--
            Inside the pass, not under it. Boards are not all the same height, so a call band
            that stayed put while the board above it travelled was the one thing on screen
            saying nothing was moving.
          -->
          <call-band v-bind="BOARD_CALL" />
        </band-swipe>
      </div>

      <!-- One dialog for the board being corrected and for a board being added: which it is
           follows from whether there is a board in it. Outside the band and the identity, so
           it is not unmounted by the board it just changed arriving. -->
      <board-dialog
        v-if="mayEdit"
        :accent="accent"
        :board="editing"
        :next-number="nextNumber"
        :open="editorOpen"
        @removed="boardRemoved"
        @saved="boardSaved"
        @update:open="editorOpen = $event"
      />

      <!-- One dialog per member, opened from the row it belongs to. Outside the band and the
           identity for the reason the board's own is: a member saved re-reads the board, and
           the dialog must not be unmounted by the board it just changed arriving. -->
      <board-member-dialog
        v-if="mayEdit"
        v-model:open="memberOpen"
        :accent="accent"
        :board-end="shown?.endDate"
        :board-id="shown?.id ?? null"
        :board-start="shown?.startDate"
        :member="editingMember"
        @removed="memberSaved"
        @saved="memberSaved"
      />
    </island>
  </v-main>
</template>

<style scoped>
/*
 * The board's colour, made readable against whichever ground the reader is on.
 *
 * Mixed towards the ink of the theme rather than used raw: chalk is near-white in the dark half
 * and near-black in the light one, so one formula lifts a dark colour off a dark page and drops a
 * pale one onto a pale page. The two mixes differ because the light half needs the heavier hand,
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


/* A blurb is prose rather than a caption, so it is set to be read at length. */
.board-member__blurb {
  margin: 0;
  font-family: var(--font-body);
  font-size: 0.85rem;
  line-height: 1.5;
  color: var(--color-chalk);
  opacity: 0.92;
}
</style>
