<script lang="ts" setup>
import {computed, ref} from "vue"
import {useRouter} from "vue-router"
import ArtCells from "@/components/island/ArtCells.vue"
import BandHead from "@/components/island/BandHead.vue"
import CutButton from "@/components/island/CutButton.vue"
import Island from "@/components/island/Island.vue"
import LeadBand from "@/components/island/LeadBand.vue"
import RecordFact from "@/components/island/RecordFact.vue"
import MarkdownView from "@/components/island/MarkdownView.vue"
import RecordHead from "@/components/island/RecordHead.vue"
import ScopedEvents from "@/domains/events/island/ScopedEvents.vue"
import {cellOf as gameCellOf, useCasualGames} from "@/domains/games"
import type {Committee, CommitteePage} from "../adapters/committees"
import ArchiveCommitteeDialog from "../island/ArchiveCommitteeDialog.vue"
import {useCommitteeRights} from "../island/useCommitteeRights"
import {initialsOf, useCommittees} from "../useCommittees"

defineOptions({name: "CommitteePage"})

/**
 * One committee's page: its banner, name and description, who sits on it by Discord only, the
 * games it organises events for and its events. The board edits and archives it; its own members
 * edit its page and add its events.
 */
const {page} = defineProps<{page: CommitteePage}>()

const emit = defineEmits<{(event: "changed", committee: Committee): void}>()

const router = useRouter()
const {refresh} = useCommittees()
const {games} = useCasualGames()
const {isBoard, sitsOn} = useCommitteeRights()

const mayEdit = computed(() => isBoard.value || sitsOn(page.id))
const mayAddEvent = computed(() => mayEdit.value && !page.archived)

const named = computed(() => page.gameCodes
  .map(code => games.value.find(game => game.code === code))
  .filter(game => game !== undefined))
const gameCells = computed(() => named.value.map(game => gameCellOf(game)))

const go = (to: {href: string}) => void router.push(to.href)

const archiving = ref(false)

/** Archived or brought back: the listing and this page read it again. */
const archived = async (now: Committee) => {
  await refresh()
  emit("changed", now)
}
</script>

<template>
  <v-main>
    <island testid="committee-island">
      <record-head
        accent="var(--color-brand)"
        :archived="page.archived"
        :back="{to: '/committees', label: 'Committees'}"
        :banner="page.banner"
        eyebrow="Committee"
        :icon="page.icon?.url ?? null"
        :initials="initialsOf(page.name)"
        testid="committee"
        :title="page.name"
      >
        <markdown-view :source="page.description" />
        <template
          v-if="named.length > 0"
          #facts
        >
          <record-fact
            data-testid="committee-games"
            :label="named.length === 1 ? 'Game' : 'Games'"
          >
            <template
              v-for="(game, at) in named"
              :key="game.code"
            >
              <template v-if="at > 0">
                ·
              </template>
              <router-link :to="`/casual/${game.slug}`">
                {{ game.name }}
              </router-link>
            </template>
          </record-fact>
        </template>
        <template
          v-if="mayEdit"
          #acts
        >
          <cut-button
            v-if="mayAddEvent"
            :href="`/events/create?committee=${page.id}`"
            testid="committee-add-event"
            tone="solid"
          >
            Add an event
          </cut-button>
          <cut-button
            :href="`/committees/${page.slug}/edit`"
            testid="committee-edit"
          >
            Edit committee
          </cut-button>
          <cut-button
            v-if="isBoard"
            testid="committee-archive"
            tone="quiet"
            @click="archiving = true"
          >
            {{ page.archived ? "Bring it back" : "Archive" }}
          </cut-button>
        </template>
        <template
          v-if="page.members.length > 0"
          #people
        >
          <p class="committee-page__people-label">
            The people behind its events
          </p>
          <ul
            class="committee-page__seats"
            data-testid="committee-members"
          >
            <li
              v-for="(seat, at) in page.members"
              :key="at"
              class="committee-page__seat"
              :data-testid="`committee-seat-${at}`"
            >
              <img
                v-if="seat.avatar"
                alt=""
                class="committee-page__avatar"
                :src="seat.avatar"
              >
              <span class="committee-page__who">
                <span
                  class="committee-page__name"
                  :class="{'committee-page__name--none': !seat.discordName}"
                >{{ seat.discordName ?? "Discord not linked" }}</span>
                <span
                  v-if="seat.role"
                  class="committee-page__role"
                >{{ seat.role }}</span>
              </span>
            </li>
          </ul>
        </template>
      </record-head>

      <lead-band
        v-if="gameCells.length > 0"
        testid="committee-games-band"
      >
        <band-head
          eyebrow="What it plays"
          heading="Games"
        />
        <art-cells
          class="committee-page__games"
          :cells="gameCells"
          testid-prefix="committee-games"
          @go="go"
        />
      </lead-band>

      <scoped-events
        :key="page.id"
        :scope="{committeeId: page.id}"
        testid="committee-events"
      />

      <template v-if="mayEdit">
        <archive-committee-dialog
          v-if="isBoard"
          v-model:open="archiving"
          :committee="page"
          @saved="archived"
        />
      </template>
    </island>
  </v-main>
</template>

<style scoped>
.committee-page__people-label {
  margin: 0;
  font-size: 0.6rem;
  letter-spacing: 0.18em;
  text-transform: uppercase;
  color: var(--color-ash);
}

/*
 * One row parted by a rule at the lean the buttons are cut on, as the partners are. The rule
 * stands just left of each member and the row clips its left edge, so a member that wraps to the
 * start of a line has none before it.
 */
.committee-page__seats {
  display: flex;
  flex-wrap: wrap;
  row-gap: 0.9rem;
  margin: 0.7rem 0 0 -1.5rem;
  padding: 0;
  overflow: hidden;
  list-style: none;
}

.committee-page__seat {
  position: relative;
  display: flex;
  gap: 0.75rem;
  align-items: center;
  min-width: 0;
  padding: 0 1.5rem;
}

.committee-page__seat::before {
  position: absolute;
  top: 0.2rem;
  bottom: 0.2rem;
  left: -4px;
  width: 1px;
  content: "";
  background-color: var(--color-hairline);
  transform: skewX(-12deg);
}

.committee-page__avatar {
  flex: none;
  width: 2.6rem;
  height: 2.6rem;
  border-radius: 50%;
}

.committee-page__who {
  display: flex;
  flex-direction: column;
  min-width: 0;
  line-height: 1.2;
}

.committee-page__name {
  font-size: 1.05rem;
  color: var(--color-chalk);
  white-space: nowrap;
}

.committee-page__name--none {
  color: var(--color-ash);
}

.committee-page__role {
  font-size: 0.72rem;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--color-ash);
}

.committee-page__games {
  margin-top: 1.4rem;
}
</style>
