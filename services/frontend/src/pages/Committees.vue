<script lang="ts" setup>
import {computed, ref} from "vue"
import type {ArtCell} from "@/components/island/ArtCells.vue"
import CutButton from "@/components/island/CutButton.vue"
import type {ReelItem} from "@/components/island/FlickReel.vue"
import RecordIndex from "@/components/island/RecordIndex.vue"
import {DISCORD_INVITE} from "@/components/island/socialGlyphs"
import {cellOf, driftItemOf, reelItemOf, useCommitteeRights, useCommittees, type Committee} from "@/domains/committees"
import ArchiveCommitteeDialog from "@/domains/committees/island/ArchiveCommitteeDialog.vue"
import {useCasualGames} from "@/domains/games"

defineOptions({name: "CommitteesPage"})

/**
 * The committees index: the committees that run on the reel, the ones we used to have drifting
 * past under it, and then every listed committee. Unlisted committees appear nowhere here.
 */
const {committees, live, archived, refresh} = useCommittees()
const {games} = useCasualGames()
const {isBoard} = useCommitteeRights()

/** The games a committee organises events for, by name, drawn as its chips. */
const gameNames = (codes: string[]) => codes
  .map(code => games.value.find(game => game.code === code)?.name)
  .filter(name => name !== undefined)

const reel = computed<ReelItem[]>(() => live.value.map(committee => reelItemOf(committee, gameNames)))
// The committees that run first, then the archived ones, each in their own order.
const every = computed<ArtCell[]>(() => [...live.value, ...archived.value].map(committee => cellOf(committee, gameNames)))

const archiving = ref<Committee | null>(null)
const committeeOf = (id: string | number) => committees.value.find(committee => committee.id === id) ?? null
</script>

<template>
  <v-main>
    <record-index
      body="Committees are groups of members who organise the events and run the things the association does, and have a good time doing it. Want to join one, or start something new? Ask the board on Discord."
      each="committee"
      every-heading="Every committee"
      :every="every"
      eyebrow="Run by members"
      heading="Committees"
      :may-edit="isBoard"
      :olden="archived.map(driftItemOf)"
      olden-heading="The committees we used to have"
      olden-line="Want to bring one back, or start something new? Ask the board on Discord. A few members with a plan is all a committee needs, and any member can run a one-off event as a member's initiative."
      :reel="reel"
      reel-eyebrow="Pick a committee"
      reel-heading="Our committees"
      testid="committees"
      @archive="archiving = committeeOf($event)"
    >
      <template #acts>
        <cut-button
          away
          :href="DISCORD_INVITE"
          testid="committees-ask"
          tone="solid"
        >
          Ask on Discord
        </cut-button>
        <cut-button
          v-if="isBoard"
          href="/committees/new"
          testid="committees-add"
        >
          Add a committee
        </cut-button>
      </template>

      <archive-committee-dialog
        v-if="isBoard && archiving"
        :committee="archiving"
        open
        @saved="refresh"
        @update:open="archiving = null"
      />
    </record-index>
  </v-main>
</template>
