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
import {cellOf as committeeCellOf, useCommittees} from "@/domains/committees"
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
const {listed: committees, refresh: refreshCommittees} = useCommittees()
const mayEdit = useMayEditGames()

const editing = ref(false)
const archiving = ref(false)
const removing = ref(false)

/** Its address may have moved, and this page is at the old one. Its organisers may have too. */
const saved = async (now: CasualGame) => {
  await Promise.all([refresh(), refreshCommittees()])
  if (now.slug !== game.slug) void router.replace(`/casual/${now.slug}`)
}

const removed = async () => {
  await refresh()
  void router.push("/casual")
}

const go = (to: {href: string}) => void router.push(to.href)

/** An archived game keeps its channels named, but offers none to open. */
const firstChannel = computed(() => (game.archived ? null : game.channels[0] ?? null))
const organisers = computed(() => committees.value.filter(committee => committee.gameCodes.includes(game.code)))
const organiserCells = computed(() => organisers.value.map(committee => committeeCellOf(committee)))

const accent = computed(() => game.accent || "var(--color-brand)")
</script>

<template>
  <v-main>
    <island testid="casual-game-island">
      <record-head
        :accent="accent"
        :archived="game.archived"
        :back="{to: '/casual', label: 'Casual'}"
        :banner="game.banner"
        eyebrow="Casual"
        :icon="game.icon?.url"
        :initials="initialsOf(game.name)"
        testid="casual-game"
        :title="game.name"
      >
        <template
          v-if="game.intro"
          #default
        >
          {{ game.intro }}
        </template>
        <template #facts>
          <record-fact
            v-if="game.inCompetition"
            label="Competition"
          >
            <router-link
              data-testid="casual-game-competition"
              :to="`/competition/${game.slug}`"
            >
              Teams and seasons
            </router-link>
          </record-fact>
          <record-fact
            v-else
            data-testid="casual-game-not-competitive"
            label="Competition"
            quiet
          >
            We don't currently play this game competitively
          </record-fact>
          <record-fact
            v-if="game.channels.length > 0"
            data-testid="casual-game-channels"
            :label="game.channels.length === 1 ? 'Channel' : 'Channels'"
          >
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
          </record-fact>
          <record-fact
            v-if="organisers.length === 0"
            data-testid="casual-game-committees"
            label="Committees"
            quiet
          >
            None yet
          </record-fact>
          <record-fact
            v-else
            data-testid="casual-game-committees"
            :label="organisers.length === 1 ? 'Committee' : 'Committees'"
          >
            <template
              v-for="(committee, at) in organisers"
              :key="committee.id"
            >
              <template v-if="at > 0">
                ·
              </template>
              <router-link :to="`/committees/${committee.slug}`">
                {{ committee.name }}
              </router-link>
            </template>
          </record-fact>
        </template>
        <template
          v-if="firstChannel || mayEdit"
          #acts
        >
          <cut-button
            v-if="firstChannel"
            away
            :href="gameRoomUrl(firstChannel)"
            testid="casual-game-open-channel"
            tone="solid"
          >
            Open #{{ firstChannel.name }}
          </cut-button>
          <template v-if="mayEdit">
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
          </template>
        </template>
      </record-head>

      <lead-band
        v-if="organiserCells.length > 0"
        testid="casual-game-organisers"
      >
        <band-head
          eyebrow="Committees"
          heading="Who organises events for it"
        />
        <art-cells
          class="game-page__organisers"
          :cells="organiserCells"
          testid-prefix="casual-game-organisers"
          @go="go"
        />
      </lead-band>

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
.game-page__organisers {
  margin-top: 1.4rem;
}
</style>
