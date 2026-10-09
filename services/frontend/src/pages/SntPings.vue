<script lang="ts" setup>
import {computed, onBeforeUnmount, onMounted, ref, watch} from "vue"
import Island from "@/components/island/Island.vue"
import HeaderBand from "@/components/island/HeaderBand.vue"
import LeadBand from "@/components/island/LeadBand.vue"
import CallBand from "@/components/island/CallBand.vue"
import BandRule from "@/components/island/BandRule.vue"
import BandHead from "@/components/island/BandHead.vue"
import CutButton from "@/components/island/CutButton.vue"
import NoticeBox from "@/components/island/NoticeBox.vue"
import StateTag from "@/components/island/StateTag.vue"
import Leaderboard from "@/components/pinger/Leaderboard.vue"
import CanvasStage from "@/components/pinger/CanvasStage.vue"
import PingerProgress from "@/components/pinger/PingerProgress.vue"
import {
  appDownloadUrl,
  DEFAULT_PAINT,
  EMPTY_LEADERBOARD,
  loadLeaderboard,
  loadPaintJob,
  openLeaderboardStream,
  ownStanding,
  type AppOs,
  type Leaderboard as LeaderboardSnapshot,
  type PaintJob,
} from "@/domains/pinger"
import store from "@/plugins/store"

defineOptions({name: "SntPingsPage"})

const CANVAS_W = 3840
const CANVAS_H = 2160

// The SNTPings event start. The countdown runs to this and hides once it passes. CET is UTC+1.
const EVENT_START = new Date("2025-12-05T18:00:00+01:00").getTime()

const paint = ref<PaintJob>(DEFAULT_PAINT)
const snapshot = ref<LeaderboardSnapshot>(EMPTY_LEADERBOARD)

// Only a signed-in member sees the block below the board; a visitor never does.
const isMember = computed<boolean>(() => store.getters.isMember)

// The installers are unsigned, so each platform needs its own first-run step to open them.
const downloads: {os: AppOs, label: string, openStep: string}[] = [
  {os: "macos", label: "macOS", openStep: "Right-click the app and choose Open the first time."},
  {os: "windows", label: "Windows", openStep: "On the SmartScreen prompt choose More info then Run anyway."},
  {os: "linux", label: "Linux", openStep: "Run chmod +x on the AppImage then launch it."},
]

const downloadUrl = (os: AppOs): string => appDownloadUrl(os)

/** The members in rank order, so a stream snapshot that reorders them moves the rows. */
const ranked = computed(() => [...snapshot.value.members].sort((a, b) => a.rank - b.rank))

const house = computed(() => snapshot.value.house ?? null)

/** The signed-in member's own row, where the board carries one for them. */
const mine = computed(() => ownStanding(snapshot.value, store.getters.getLogin?.username))
const mineId = computed<number | null>(() => mine.value?.memberId ?? null)

/** Whether any pinger is reporting right now, which lights the canvas as live rather than idle. */
const anyPainting = computed<boolean>(() =>
  (house.value?.online ?? false) || ranked.value.some(row => row.online))

/** Total pings across SiteCie and every member, which fills the canvas and drives the plate and meter. */
const siteSent = computed<number>(() =>
  (house.value?.totalSent ?? 0) + ranked.value.reduce((sum, row) => sum + row.totalSent, 0))

/**
 * SiteCie's live rate, read off the board rather than a separate feed: the api does not hand out a
 * pps, so it is the growth of the total between two pushes, smoothed. That is enough to animate the
 * fill between pushes and to draw the rate chart.
 */
const sitePps = ref<number>(0)
let lastSiteSent = 0
let lastSiteAt = 0
watch(siteSent, (value) => {
  const at = Date.now()
  if (lastSiteAt > 0 && value > lastSiteSent) {
    const instant = (value - lastSiteSent) / Math.max(0.25, (at - lastSiteAt) / 1000)
    // Smoothed, so a push that lands a touch late does not spike the chart.
    sitePps.value = Math.round(sitePps.value === 0 ? instant : sitePps.value * 0.5 + instant * 0.5)
  }
  lastSiteSent = value
  lastSiteAt = at
})

/** Pixels in one pass, measured from the logo by the canvas plate and shared with the meter. */
const passTotal = ref<number>(0)

/** The target prefix the board set, shown on the plate as the old watch page did. */
const prefixLabel = computed<string>(() => paint.value.prefix ?? "no prefix set")

/** A live clock so the band shows the event is running now, not a frozen snapshot. */
const clock = ref<string>("")
let clockTimer = 0
// A live now, so both the clock and the countdown advance on the one-second tick.
const now = ref<number>(Date.now())
const tickClock = (): void => {
  now.value = Date.now()
  clock.value = new Date(now.value).toLocaleTimeString("en-GB", {hour: "2-digit", minute: "2-digit", second: "2-digit"})
}

/** Whether the event is still ahead, which keeps the countdown on screen until it starts. */
const beforeEvent = computed<boolean>(() => EVENT_START - now.value > 0)

/** Time left until the event, as `Dd HH:MM:SS` (the day part drops inside a day). */
const countdown = computed<string>(() => {
  const total = Math.max(0, Math.floor((EVENT_START - now.value) / 1000))
  const days = Math.floor(total / 86400)
  const pad = (n: number): string => String(n).padStart(2, "0")
  const hms = `${pad(Math.floor((total % 86400) / 3600))}:${pad(Math.floor((total % 3600) / 60))}:${pad(total % 60)}`
  return days > 0 ? `${days}d ${hms}` : hms
})

let closeStream: (() => void) | null = null

onMounted(async () => {
  tickClock()
  clockTimer = window.setInterval(tickClock, 1000)
  paint.value = await loadPaintJob()
  snapshot.value = await loadLeaderboard()
  // The stream drives the swap-without-reload; the GET above is the first paint and the fallback.
  closeStream = openLeaderboardStream((next) => {
    snapshot.value = next
  })
})

onBeforeUnmount(() => {
  closeStream?.()
  clearInterval(clockTimer)
})
</script>

<template>
  <v-main>
    <island testid="sntpings-island">
      <header-band
        body="Watch us paint onto the SNTPings canvas live, and help us stay on top! You can see your performance on the leaderboard :)"
        eyebrow="SNTPings · Live event"
        heading="Paint the canvas,"
        heading-tail="top the board"
      >
        <template #acts>
          <p
            v-if="beforeEvent"
            class="snt-countdown"
            data-testid="snt-countdown"
          >
            Event starts in <b>{{ countdown }}</b>
          </p>
          <cut-button
            href="https://pings.utwente.io"
            away
            testid="snt-watch"
            tone="solid"
          >
            Watch the live canvas
          </cut-button>
        </template>
      </header-band>

      <!-- The canvas is a photographic band, so it keeps the dark set in light mode too. -->
      <section
        id="snt-canvas"
        class="canvas island-dark"
        data-testid="snt-canvas"
      >
        <div class="canvas__inner">
          <band-head
            eyebrow="The live canvas"
            heading="What we are painting"
            testid="snt-canvas-head"
          >
            <state-tag
              :tone="anyPainting ? 'ok' : 'quiet'"
              testid="snt-canvas-state"
            >
              {{ anyPainting ? "Painting now" : "Canvas idle" }}
            </state-tag>
          </band-head>

          <p
            class="canvas__status"
            data-testid="snt-canvas-status"
          >
            <span
              class="canvas__dot"
              :class="{'canvas__dot--on': anyPainting}"
            />
            <span>{{ anyPainting ? "Event running" : "Waiting for the canvas" }}</span>
            <span class="canvas__sep">·</span>
            <span>Painting onto <b>{{ prefixLabel }}</b></span>
            <span class="canvas__sep">·</span>
            <span>{{ clock }}</span>
          </p>

          <div class="canvas__grid">
            <canvas-stage
              :placements="paint.placements"
              :pps="sitePps"
              :prefix-label="prefixLabel"
              :running="anyPainting"
              :sent="siteSent"
              @passtotal="passTotal = $event"
            />
            <pinger-progress
              :pass-total="passTotal"
              :pps="sitePps"
              :running="anyPainting"
              :sent="siteSent"
            />
          </div>

          <p class="canvas__note">
            The shell lands in a box on the {{ CANVAS_W }}&times;{{ CANVAS_H }} canvas and fills in
            pixel by pixel as the cluster paints it. An admin sets the picture, the box and the target
            from the management page.
          </p>
        </div>
      </section>

      <band-rule testid="snt-rule" />

      <lead-band
        accent="var(--color-acid)"
        testid="snt-leaderboard"
      >
        <band-head
          :count="ranked.length"
          count-said="members on the board"
          eyebrow="Who is sending the most"
          heading="Leaderboard"
          testid="snt-leaderboard-head"
        />
        <div class="board-wrap">
          <leaderboard
            :house="house"
            :mine-id="mineId"
            :rows="ranked"
          />
        </div>
      </lead-band>

      <template v-if="isMember">
        <band-rule
          mirrored
          testid="snt-rule-foot"
        />

        <lead-band
          accent="var(--color-brand)"
          testid="snt-member"
        >
          <band-head
            eyebrow="Members only"
            heading="Get on the leaderboard!"
            testid="snt-member-head"
          />

          <div class="member">
            <p
              class="permission"
              data-testid="snt-permission"
            >
              By contributing you'll get listed on the leaderboards too!
            </p>

            <div class="panel downloads-panel">
              <h3 class="panel__title">
                Download the pinger
              </h3>

              <div class="downloads">
                <div
                  v-for="d in downloads"
                  :key="d.os"
                  class="downloads__one"
                >
                  <cut-button
                    :data-os="d.os"
                    :href="downloadUrl(d.os)"
                    testid="snt-download"
                    tone="solid"
                  >
                    {{ d.label }}
                  </cut-button>
                  <span class="downloads__step">{{ d.openStep }}</span>
                </div>
              </div>

              <notice-box
                class="member__caveat"
                tone="warning"
              >
                The installers are unsigned, so your system warns before the first run.
              </notice-box>
            </div>
          </div>
        </lead-band>
      </template>

      <call-band
        body="SNTPings is a members-only event. Join the association and send your share of the canvas."
        eyebrow="Not a member yet"
        headline="Get on the board"
        :actions="[{label: 'Become a member', href: '/membership/signup', tone: 'solid', testid: 'snt-call-signup'}]"
        testid="snt-call"
      />
    </island>
  </v-main>
</template>

<style scoped>
/* The canvas band, dark whatever the viewer reads in: a painted picture reads on its own ground,
   not on the page's. */
.canvas {
  width: 100%;
  background-color: var(--color-ground);
}

.canvas__inner {
  width: 100%;
  max-width: 82rem;
  margin: 0 auto;
  padding: 2.5rem 2rem;
}

/* This page runs a notch wider than the default island column: the header, the bands and the
   canvas all share the one width so their edges still line up. Only the cap grows, so a phone is
   unchanged and nothing scrolls sideways. */
:deep(.island-header .max-w-6xl),
:deep(.lead-band__inner) {
  max-width: 82rem;
}

/* A live line under the head: the state, the target prefix and a ticking clock, so the band reads
   as the event happening now rather than a frozen picture. */
.canvas__status {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 0.5rem;
  margin-top: 0.9rem;
  font-size: 0.82rem;
  color: var(--color-ash);
}

.canvas__status b {
  font-family: var(--font-bitmap, ui-monospace, monospace);
  font-size: 0.72rem;
  letter-spacing: 0.02em;
  color: var(--color-brand-lit);
}

.canvas__sep {
  opacity: 0.5;
}

.canvas__dot {
  width: 8px;
  height: 8px;
  border-radius: 9999px;
  background: var(--color-ash);
  opacity: 0.6;
}

.canvas__dot--on {
  background: var(--color-ok);
  opacity: 1;
  box-shadow: 0 0 0 3px color-mix(in oklab, var(--color-ok) 24%, transparent);
}

/* The plate and its progress sit side by side on a wide screen and stack on a narrow one. */
.canvas__grid {
  display: grid;
  grid-template-columns: minmax(0, 1.25fr) minmax(0, 1fr);
  gap: 2rem 2.5rem;
  align-items: center;
  margin-top: 1.5rem;
}

.canvas__note {
  margin-top: 1.1rem;
  max-width: 34rem;
  font-size: 0.82rem;
  line-height: 1.5;
  color: var(--color-ash);
}

.board-wrap {
  margin-top: 1.75rem;
}

.member {
  margin-top: 1.5rem;
  display: flex;
  flex-direction: column;
  gap: 1.1rem;
  max-width: 52rem;
}

/* The house cut, so each part of the member block reads as a panel of the page rather than a plain
   box. A brand bar down the lean marks it as the member's own, the way their row is marked. */
.panel {
  position: relative;
  padding: 1.15rem calc(0.7rem + 1.1rem);
  clip-path: polygon(0.7rem 0, 100% 0, calc(100% - 0.7rem) 100%, 0 100%);
  background-color: var(--band-ground);
  box-shadow: inset 3px 0 0 var(--color-brand);
}

/* Contributing is the permission, so this says so rather than offering a switch. */
.permission {
  font-size: 0.95rem;
  line-height: 1.5;
  color: var(--color-ash);
  max-width: 44rem;
}

.panel__eyebrow {
  font-family: var(--font-body);
  font-size: 11px;
  font-weight: 500;
  letter-spacing: 0.3em;
  text-transform: uppercase;
  color: var(--color-eyebrow);
}

.panel__title {
  margin-top: 0.4rem;
  font-family: var(--font-display);
  font-size: 1.15rem;
  text-transform: uppercase;
}

.downloads-panel {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

/* The three platform buttons sit side by side, each over its own open-step, and wrap on a phone. */
.downloads {
  display: flex;
  flex-direction: row;
  flex-wrap: wrap;
  gap: 1rem 1.25rem;
  margin-top: 0.5rem;
}

.downloads__one {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 0.5rem;
  flex: 1 1 11rem;
  min-width: 0;
}

.downloads__one :deep(.island-cut) {
  min-width: 7.5rem;
  justify-content: center;
}

.downloads__step {
  font-size: 0.82rem;
  color: var(--color-ash);
}

.member__caveat {
  margin-top: 0.5rem;
}

.snt-countdown {
  margin: 0 0 0.75rem;
  font-variant-numeric: tabular-nums;
  color: var(--color-ash);
}

.snt-countdown b {
  color: var(--color-chalk);
  font-weight: 700;
}

@media (max-width: 959px) {
  .canvas__grid {
    grid-template-columns: minmax(0, 1fr);
    gap: 1.75rem;
  }
}

@media (max-width: 639px) {
  .canvas__inner {
    padding: 1.75rem 1.25rem;
  }
}
</style>
