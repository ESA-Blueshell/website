<script lang="ts" setup>
import {computed, ref} from "vue"
import {useRouter} from "vue-router"
import CutButton from "@/components/island/CutButton.vue"
import Island from "@/components/island/Island.vue"
import {srcsetOf} from "@/components/island/pictures"
import {gameRoomUrl} from "@/domains/discord"
import ScopedEvents from "@/domains/events/island/ScopedEvents.vue"
import type {CasualGame} from "../adapters/games"
import ArchiveGameDialog from "../island/ArchiveGameDialog.vue"
import CasualGameDialog from "../island/CasualGameDialog.vue"
import RemoveGameDialog from "../island/RemoveGameDialog.vue"
import {useMayEditGames} from "../island/useMayEditGames"
import {initialsOf, useCasualGames} from "../useCasualGames"

defineOptions({name: "CasualGamePage"})

const {game} = defineProps<{game: CasualGame}>()

const router = useRouter()
const {refresh} = useCasualGames()
const mayEdit = useMayEditGames()

const editing = ref(false)
const archiving = ref(false)
const removing = ref(false)

/** Its address may have moved, and this page is at the old one. */
const saved = async (now: CasualGame) => {
  await refresh()
  if (now.slug !== game.slug) void router.replace(`/casual/${now.slug}`)
}

const removed = async () => {
  await refresh()
  void router.push("/casual")
}

const firstChannel = computed(() => game.channels[0] ?? null)

const accent = computed(() => game.accent || "var(--color-brand)")
const bannerSrcset = computed(() => srcsetOf(game.banner))
</script>

<template>
  <v-main>
    <island testid="casual-game-island">
      <div class="game-page__crumb-row">
        <router-link
          class="game-page__crumb"
          data-testid="casual-game-back"
          to="/casual"
        >
          <svg
            aria-hidden="true"
            fill="none"
            viewBox="0 0 20 12"
          ><path
            d="M20 6H3M7 1.5 1.5 6 7 10.5"
            stroke="currentColor"
            stroke-width="1.4"
          /></svg>
          Casual
        </router-link>
      </div>

      <section
        class="game-page__head"
        data-testid="casual-game-head"
        :style="{'--accent': accent}"
      >
        <div class="game-page__body">
          <div class="game-page__label">
            <p class="game-page__eyebrow">
              Casual
            </p>
            <span
              v-if="game.archived"
              class="game-page__tag"
              data-testid="casual-game-archived"
            >Archived</span>
          </div>
          <h1 class="game-page__title">
            <img
              v-if="game.icon"
              alt=""
              :src="game.icon.url"
            >{{ game.name }}
          </h1>
          <p
            v-if="game.intro"
            class="game-page__intro"
          >
            {{ game.intro }}
          </p>
          <div class="game-page__facts">
            <div class="game-page__fact">
              <p class="game-page__fact-label">
                Competition
              </p>
              <template v-if="game.inCompetition">
                <p class="game-page__fact-value">
                  <router-link
                    data-testid="casual-game-competition"
                    :to="`/competition/${game.slug}`"
                  >
                    Teams and seasons
                  </router-link>
                </p>
              </template>
              <p
                v-else
                class="game-page__fact-sub"
                data-testid="casual-game-not-competitive"
              >
                We don't currently play this game competitively
              </p>
            </div>
            <div
              v-if="game.channels.length > 0"
              class="game-page__fact"
              data-testid="casual-game-channels"
            >
              <p class="game-page__fact-label">
                {{ game.channels.length === 1 ? "Channel" : "Channels" }}
              </p>
              <p class="game-page__fact-value">
                <template
                  v-for="(channel, at) in game.channels"
                  :key="channel.id"
                >
                  <template v-if="at > 0">
                    ·
                  </template>
                  <a
                    :data-testid="`casual-game-channel-${channel.id}`"
                    :href="gameRoomUrl(channel)"
                    rel="noopener"
                    target="_blank"
                  >#{{ channel.name }}</a>
                </template>
              </p>
            </div>
          </div>
          <div
            v-if="firstChannel"
            class="game-page__acts"
          >
            <cut-button
              away
              :href="gameRoomUrl(firstChannel)"
              testid="casual-game-open-channel"
              tone="solid"
            >
              Open #{{ firstChannel.name }}
            </cut-button>
          </div>
          <div
            v-if="mayEdit"
            class="game-page__acts"
          >
            <cut-button
              testid="casual-game-edit"
              @click="editing = true"
            >
              Edit game
            </cut-button>
            <cut-button
              testid="casual-game-archive"
              tone="quiet"
              @click="archiving = true"
            >
              {{ game.archived ? "Bring it back" : "Archive" }}
            </cut-button>
            <cut-button
              v-if="game.archived"
              testid="casual-game-remove"
              tone="quiet"
              @click="removing = true"
            >
              Remove
            </cut-button>
          </div>
        </div>
        <div class="game-page__art">
          <img
            v-if="game.banner"
            alt=""
            sizes="(min-width: 768px) 36rem, 100vw"
            :src="game.banner.url"
            :srcset="bannerSrcset"
          >
          <span
            v-else
            aria-hidden="true"
            class="game-page__plate"
          ><span>{{ initialsOf(game.name) }}</span></span>
        </div>
      </section>

      <scoped-events
        :key="game.code"
        :scope="{gameCode: game.code}"
        testid="casual-game-events"
      >
        No event names {{ game.name }} yet. Events name their games from now on, so older ones are not listed here.
      </scoped-events>

      <slot />

      <template v-if="mayEdit">
        <casual-game-dialog
          v-model:open="editing"
          :game="game"
          @saved="saved"
        />
        <archive-game-dialog
          v-model:open="archiving"
          :game="game"
          @saved="refresh"
        />
        <remove-game-dialog
          v-if="removing"
          :game="game"
          open
          @removed="removed"
          @update:open="removing = $event"
        />
      </template>
    </island>
  </v-main>
</template>

<style scoped>
.game-page__crumb-row {
  width: 100%;
  max-width: 72rem;
  margin: 0 auto;
  padding: 0 2rem;
}

.game-page__crumb {
  display: inline-flex;
  gap: 0.6rem;
  align-items: center;
  padding: 1.1rem 0;
  font-size: 0.85rem;
  letter-spacing: 0.04em;
  color: var(--color-ash);
  text-decoration: none;
}

.game-page__crumb:hover {
  color: var(--color-chalk);
}

.game-page__crumb svg {
  width: 18px;
  height: 11px;
}

/* The banner band heading the page, square as every banner is: the art is never cut. */
.game-page__head {
  position: relative;
  isolation: isolate;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 36rem;
  min-height: 26rem;
  background-color: var(--band-ground);
}

.game-page__head::before {
  content: "";
  position: absolute;
  inset: 0;
  z-index: -1;
  pointer-events: none;
  background: radial-gradient(70% 120% at 0 0, color-mix(in oklab, var(--accent) 10%, transparent) 0%, transparent 62%);
}

.game-page__body {
  display: flex;
  flex-direction: column;
  gap: 1.2rem;
  justify-content: center;
  min-width: 0;
  padding: 2.5rem 3rem 2.75rem max(2rem, calc((100vw - 72rem) / 2 + 2rem));
}

.game-page__label {
  display: flex;
  flex-wrap: wrap;
  gap: 0.8rem;
  align-items: center;
}

.game-page__eyebrow {
  font-size: 11px;
  font-weight: 500;
  letter-spacing: 0.3em;
  text-transform: uppercase;
  color: var(--color-eyebrow);
}

.game-page__tag {
  padding: 0.26rem 0.55rem;
  font-size: 0.68rem;
  font-weight: 600;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: var(--color-ash);
  border: 1px solid currentcolor;
}

.game-page__title {
  display: flex;
  gap: 1rem;
  align-items: center;
  font-family: var(--font-display);
  font-size: 4rem;
  line-height: 0.95;
  text-transform: uppercase;
  color: var(--color-chalk);
}

.game-page__title img {
  width: 3.6rem;
  height: 3.6rem;
  object-fit: contain;
}

.game-page__intro {
  max-width: 38rem;
  font-size: 1.05rem;
  line-height: 1.6;
  color: color-mix(in oklab, var(--color-chalk) 86%, transparent);
}

.game-page__facts {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  row-gap: 1.2rem;
}

.game-page__fact {
  position: relative;
  min-width: 0;
  padding-inline: 1.25rem;
}

.game-page__fact::before {
  content: "";
  position: absolute;
  top: 0.2rem;
  bottom: 0.2rem;
  left: 0;
  width: 1px;
  background-color: var(--color-hairline);
  transform: skewX(-12deg);
}

.game-page__fact:first-child {
  padding-inline-start: 0;
}

.game-page__fact:first-child::before {
  display: none;
}

.game-page__fact-label {
  font-size: 11px;
  font-weight: 500;
  letter-spacing: 0.3em;
  text-transform: uppercase;
  color: var(--color-ash);
}

.game-page__fact-value {
  margin-top: 0.45rem;
  font-family: var(--font-display);
  font-size: 1.05rem;
  line-height: 1.2;
  text-transform: uppercase;
  color: var(--color-chalk);
}

.game-page__fact-value a {
  color: inherit;
}

.game-page__fact-value a:hover {
  text-decoration: underline;
  text-underline-offset: 3px;
}

.game-page__fact-sub {
  margin-top: 0.35rem;
  font-size: 0.9rem;
  line-height: 1.4;
  color: var(--color-ash);
}

.game-page__acts {
  display: flex;
  flex-wrap: wrap;
  gap: 0.6rem;
  align-items: center;
}

.game-page__art {
  position: relative;
  overflow: hidden;
  background-color: var(--color-surface);
}

.game-page__art img {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.game-page__plate {
  position: absolute;
  inset: 0;
  overflow: hidden;
  background:
    radial-gradient(110% 90% at 0 0, color-mix(in oklab, var(--accent) 30%, transparent), transparent 70%),
    repeating-linear-gradient(-62deg, transparent 0 22px, color-mix(in oklab, var(--accent) 7%, transparent) 22px 24px),
    var(--color-pit);
}

.game-page__plate > span {
  position: absolute;
  top: -3rem;
  right: -0.4rem;
  font-family: var(--font-display);
  font-size: 16rem;
  line-height: 1;
  color: color-mix(in oklab, var(--accent) 22%, transparent);
}

@media (max-width: 639px) {
  .game-page__crumb-row {
    padding: 0 1.25rem;
  }

  .game-page__head {
    grid-template-columns: 1fr;
    min-height: 0;
  }

  .game-page__art {
    order: -1;
    aspect-ratio: 16 / 9;
  }

  .game-page__plate > span {
    top: -1.5rem;
    font-size: 9rem;
  }

  .game-page__body {
    padding: 1.4rem 1.25rem 1.8rem;
  }

  .game-page__title {
    gap: 0.7rem;
    font-size: 2.3rem;
  }

  .game-page__title img {
    width: 2.4rem;
    height: 2.4rem;
  }

  .game-page__facts {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
