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
import {PeopleList} from "@/domains/discord"
import ScopedEvents from "@/domains/events/island/ScopedEvents.vue"
import {cellOf as gameCellOf, useCasualGames} from "@/domains/games"
import type {Committee, CommitteePage} from "../adapters/committees"
import ArchiveCommitteeDialog from "../island/ArchiveCommitteeDialog.vue"
import {useCommitteeRights} from "../island/useCommitteeRights"
import {useCommittees} from "../useCommittees"
import {initialsOf} from "@/utils/initials"
import {BRAND_ACCENT} from "@/utils/brand"

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
        :accent="BRAND_ACCENT"
        :archived="page.archived"
        :archived-since="page.archivedAt"
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
          <people-list
            item-testid="committee-seat"
            :people="page.members"
            testid="committee-members"
          />
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

.committee-page__games {
  margin-top: 1.4rem;
}
</style>
