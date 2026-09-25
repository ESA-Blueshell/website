<script lang="ts" setup>
import {computed, ref} from "vue"
import {useRouter} from "vue-router"
import ArtCells from "@/components/island/ArtCells.vue"
import BandHead from "@/components/island/BandHead.vue"
import CutButton from "@/components/island/CutButton.vue"
import Island from "@/components/island/Island.vue"
import LeadBand from "@/components/island/LeadBand.vue"
import RecordFact from "@/components/island/RecordFact.vue"
import RecordHead from "@/components/island/RecordHead.vue"
import ScopedEvents from "@/domains/events/island/ScopedEvents.vue"
import {cellOf as gameCellOf, useCasualGames} from "@/domains/games"
import $markdownToHtml from "@/plugins/markdownToHtml"
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
const {committees, refresh} = useCommittees()
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

/** Its address may have moved, and this page is at the old one. */
const saved = async (now: Committee) => {
  await refresh()
  emit("changed", now)
  if (now.slug !== page.slug) void router.replace(`/committees/${now.slug}`)
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
        :initials="initialsOf(page.name)"
        testid="committee"
        :title="page.name"
      >
        <!-- eslint-disable-next-line vue/no-v-html -- the markdown renderer escapes what it is given -->
        <div v-html="$markdownToHtml(page.description)" />
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
      </record-head>

      <lead-band
        v-if="page.members.length > 0"
        testid="committee-members"
      >
        <band-head
          eyebrow="Who sits on it"
          heading="Members"
        />
        <ul class="committee-page__seats">
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
            <span
              v-else
              aria-hidden="true"
              class="committee-page__avatar committee-page__avatar--none"
            />
            <span class="committee-page__who">
              <span
                v-if="seat.discordTag"
                class="committee-page__tag"
              >@{{ seat.discordTag }}</span>
              <span
                v-else
                class="committee-page__tag committee-page__tag--none"
              >Discord not linked</span>
              <span
                v-if="seat.role"
                class="committee-page__role"
              >{{ seat.role }}</span>
            </span>
          </li>
        </ul>
      </lead-band>

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
          @saved="saved"
        />
      </template>
    </island>
  </v-main>
</template>

<style scoped>
.committee-page__seats {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(15rem, 1fr));
  gap: 0.9rem 1.6rem;
  margin: 1.4rem 0 0;
  padding: 0;
  list-style: none;
}

.committee-page__seat {
  display: flex;
  gap: 0.8rem;
  align-items: center;
  min-width: 0;
}

.committee-page__avatar {
  flex: none;
  width: 2.6rem;
  height: 2.6rem;
  border-radius: 50%;
}

.committee-page__avatar--none {
  background-color: color-mix(in oklab, var(--color-chalk) 10%, transparent);
}

.committee-page__who {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.committee-page__tag {
  overflow: hidden;
  font-size: 0.95rem;
  color: var(--color-chalk);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.committee-page__tag--none {
  color: var(--color-ash);
}

.committee-page__role {
  font-size: 0.8rem;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--color-ash);
}

.committee-page__games {
  margin-top: 1.4rem;
}
</style>
