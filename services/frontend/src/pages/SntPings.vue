<script lang="ts" setup>
import {computed, onBeforeUnmount, onMounted, ref} from "vue"
import {
  apiUrl,
  DEFAULT_PAINT,
  EMPTY_LEADERBOARD,
  loadLeaderboard,
  loadOptIn,
  loadPaintJob,
  openLeaderboardStream,
  ownStanding,
  saveOptIn,
  type Leaderboard,
  type PaintJob,
} from "@/domains/pinger"
import store from "@/plugins/store"

defineOptions({name: "SntPingsPage"})

const CANVAS_W = 3840
const CANVAS_H = 2160

const paint = ref<PaintJob>(DEFAULT_PAINT)
const snapshot = ref<Leaderboard>(EMPTY_LEADERBOARD)

// Only a signed-in member sees the block below the board; a visitor never does.
const isMember = computed<boolean>(() => store.getters.isMember)
const optedIn = ref<boolean>(false)
const optInError = ref<string | null>(null)

/** The canvas image, resolved against the api, drawn in its box over the 4K canvas. */
const previewSrc = computed<string | null>(() => (paint.value.imageUrl ? apiUrl(paint.value.imageUrl) : null))

const boxStyle = computed(() => ({
  left: `${(paint.value.originX / CANVAS_W) * 100}%`,
  top: `${(paint.value.originY / CANVAS_H) * 100}%`,
  width: `${(paint.value.width / CANVAS_W) * 100}%`,
  height: `${(paint.value.height / CANVAS_H) * 100}%`,
}))

/** The members in rank order, so a stream snapshot that reorders them moves the rows. */
const ranked = computed(() => [...snapshot.value.members].sort((a, b) => a.rank - b.rank))

const house = computed(() => snapshot.value.house ?? null)

/** The signed-in member's own row, where the board carries one for them. */
const mine = computed(() => ownStanding(snapshot.value, store.getters.getLogin?.username))

/** The name the api prints for a row: a Discord tag where the member linked one, else their username. */
const nameOf = (member: {discordTag?: string | null, username?: string | null}): string =>
  member.discordTag ?? member.username ?? ""

const avatarOf = (member: {avatarUrl?: string | null}): string | null =>
  member.avatarUrl ? apiUrl(member.avatarUrl) : null

let closeStream: (() => void) | null = null

onMounted(async () => {
  paint.value = await loadPaintJob()
  snapshot.value = await loadLeaderboard()
  if (isMember.value) optedIn.value = await loadOptIn()
  // The stream drives the swap-without-reload; the GET above is the first paint and the fallback.
  closeStream = openLeaderboardStream((next) => {
    snapshot.value = next
  })
})

onBeforeUnmount(() => closeStream?.())

async function onOptIn(value: boolean | null): Promise<void> {
  const choice = value === true
  optedIn.value = choice
  optInError.value = null
  const result = await saveOptIn(choice)
  if (!result.ok) {
    optInError.value = result.reason
    optedIn.value = !choice
  }
}
</script>

<template>
  <v-container
    class="py-8"
    style="max-width: 1000px"
  >
    <h1 class="text-h4 mb-2">
      SNTPings
    </h1>
    <p class="text-body-2 mb-6">
      Watch the association paint the canvas live, and see who sends the most pings. The board moves
      on its own as members overtake each other.
    </p>

    <div
      class="snt-stage mb-8"
      :style="{aspectRatio: `${CANVAS_W} / ${CANVAS_H}`}"
    >
      <div
        class="snt-box"
        :style="boxStyle"
      >
        <img
          v-if="previewSrc"
          :src="previewSrc"
          class="snt-box__img"
          alt="What the association paints on the canvas"
          draggable="false"
        >
        <span
          v-else
          class="snt-box__empty"
        >The canvas is idle</span>
      </div>
    </div>

    <h2 class="text-h5 mb-4">
      Leaderboard
    </h2>

    <v-card
      v-if="house"
      class="snt-house mb-4 pa-4"
      data-testid="snt-house"
      variant="tonal"
    >
      <span class="snt-house__label">{{ house.label }}</span>
      <span class="snt-house__total">{{ house.totalSent }} pings</span>
      <span
        class="snt-dot"
        :class="{'snt-dot--on': house.online}"
        :title="house.online ? 'Online' : 'Offline'"
      />
    </v-card>

    <p
      v-if="ranked.length === 0"
      class="text-body-2 text-medium-emphasis"
      data-testid="snt-empty"
    >
      No members are on the board yet.
    </p>

    <transition-group
      v-else
      name="snt"
      tag="div"
      class="snt-board"
      data-testid="snt-board"
    >
      <div
        v-for="member in ranked"
        :key="member.memberId"
        class="snt-row"
        data-testid="snt-row"
      >
        <span class="snt-row__rank">{{ member.rank }}</span>
        <v-avatar
          size="32"
          class="snt-row__avatar"
        >
          <img
            v-if="avatarOf(member)"
            :src="avatarOf(member)!"
            alt=""
          >
          <span
            v-else
            class="snt-row__initial"
          >{{ nameOf(member).slice(0, 1).toUpperCase() }}</span>
        </v-avatar>
        <span class="snt-row__name">{{ nameOf(member) }}</span>
        <span
          class="snt-dot"
          :class="{'snt-dot--on': member.online}"
          :title="member.online ? 'Online' : 'Offline'"
        />
        <span class="snt-row__total">{{ member.totalSent }}</span>
      </div>
    </transition-group>

    <v-card
      v-if="isMember"
      class="snt-member mt-8 pa-4"
      data-testid="snt-member"
      variant="outlined"
    >
      <h2 class="text-h6 mb-2">
        Your pings
      </h2>
      <p
        v-if="mine"
        class="text-body-2 mb-4"
        data-testid="snt-mine"
      >
        You are ranked #{{ mine.rank }} with {{ mine.totalSent }} pings.
      </p>
      <p
        v-else-if="optedIn"
        class="text-body-2 mb-4"
        data-testid="snt-mine"
      >
        You appear on the board. No pings are counted for you yet.
      </p>
      <p
        v-else
        class="text-body-2 mb-4"
        data-testid="snt-mine"
      >
        Turn on the switch to appear on the public board.
      </p>

      <v-alert
        v-if="optInError"
        type="error"
        variant="tonal"
        class="mb-4"
      >
        {{ optInError }}
      </v-alert>

      <v-switch
        :model-value="optedIn"
        label="Appear on the public leaderboard"
        data-testid="snt-opt-in"
        @update:model-value="onOptIn"
      />

      <v-divider class="my-4" />

      <v-btn
        color="primary"
        disabled
        data-testid="snt-download"
      >
        Download the pinger
      </v-btn>
      <p class="text-caption mt-2 mb-0">
        The download arrives soon.
      </p>
    </v-card>
  </v-container>
</template>

<style scoped>
.snt-stage {
  position: relative;
  width: 100%;
  background: #0b1020;
  background-image: linear-gradient(rgba(255, 255, 255, 0.06) 1px, transparent 1px), linear-gradient(90deg, rgba(255, 255, 255, 0.06) 1px, transparent 1px);
  background-size: 10% 10%;
  border-radius: 6px;
  overflow: hidden;
}
.snt-box {
  position: absolute;
  border: 2px solid #7fd8ff;
}
.snt-box__img {
  width: 100%;
  height: 100%;
  object-fit: contain;
  user-select: none;
}
.snt-box__empty {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #7fd8ff;
  font-size: 0.8rem;
}
.snt-house {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}
.snt-house__label {
  font-weight: 600;
}
.snt-house__total {
  margin-left: auto;
}
.snt-board {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}
.snt-row {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.5rem 0.75rem;
  border-radius: 6px;
  background: rgba(127, 216, 255, 0.06);
}
.snt-row__rank {
  width: 1.75rem;
  text-align: right;
  font-variant-numeric: tabular-nums;
  opacity: 0.7;
}
.snt-row__initial {
  font-size: 0.85rem;
  font-weight: 600;
}
.snt-row__name {
  font-weight: 500;
}
.snt-row__total {
  margin-left: auto;
  font-variant-numeric: tabular-nums;
  font-weight: 600;
}
.snt-dot {
  width: 9px;
  height: 9px;
  border-radius: 50%;
  background: #6b7280;
}
.snt-dot--on {
  background: #22c55e;
}
/* The move is what makes a reorder legible without a reload; a reader who asked for less sits still. */
.snt-move {
  transition: transform 0.4s ease;
}
@media (prefers-reduced-motion: reduce) {
  .snt-move {
    transition: none;
  }
}
</style>
