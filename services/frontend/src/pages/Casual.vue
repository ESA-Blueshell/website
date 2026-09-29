<script lang="ts" setup>
import {computed, ref} from "vue"
import type {ArtCell} from "@/components/island/ArtCells.vue"
import CutButton from "@/components/island/CutButton.vue"
import type {ReelItem} from "@/components/island/FlickReel.vue"
import RecordIndex from "@/components/island/RecordIndex.vue"
import {DISCORD_INVITE} from "@/components/island/socialGlyphs"
import {useCommittees} from "@/domains/committees"
import {cellOf, driftItemOf, reelItemOf, useCasualGames, type CasualGame} from "@/domains/games"
import ArchiveGameDialog from "@/domains/games/island/ArchiveGameDialog.vue"
import RemoveGameDialog from "@/domains/games/island/RemoveGameDialog.vue"
import {useIsBoard} from "@/composables/useIsBoard"
import {BRAND_ACCENT} from "@/utils/brand"

defineOptions({name: "CasualPage"})

/**
 * The games index: what members play together now on the reel, the games we used to play
 * drifting past under it, and then every game, archived ones included.
 */
const {games, live, archived, refresh} = useCasualGames()
const mayEdit = useIsBoard()

const {committees} = useCommittees()
/** The committees that organise events for a game, by name, drawn as its chips. */
const organisersOf = (code: string) => committees.value.filter(committee => committee.gameCodes.includes(code)).map(committee => committee.name)

/** The way to add a game rides the reel as its last slice, for whoever may add one. */
const ADD: ReelItem = {id: "add", title: "Add a game", href: "/casual/new", accent: BRAND_ACCENT, initials: "+", plus: true}
const reel = computed<ReelItem[]>(() => [
  ...live.value.map(game => reelItemOf(game, organisersOf)),
  ...(mayEdit.value ? [ADD] : []),
])
// The played games first, then the archived ones, each in their own order.
const every = computed<ArtCell[]>(() => [...live.value, ...archived.value].map(game => cellOf(game, organisersOf)))

const archiving = ref<CasualGame | null>(null)
const removing = ref<CasualGame | null>(null)
const gameOf = (id: string | number) => games.value.find(game => game.code === id) ?? null
</script>

<template>
  <v-main>
    <record-index
      body="The games members play together outside competition. Every game has its channel on the Blueshell Discord: join the server, open the channel and there is usually somebody up for a round."
      each="game"
      every-heading="Every game"
      :every="every"
      eyebrow="Casual gaming"
      heading="Casual"
      :may-edit="mayEdit"
      :olden="archived.map(driftItemOf)"
      olden-heading="The games we used to play"
      olden-line="Want one of these back, or a game that is not here at all? Ask on Discord. If people want to play it, we bring it back or open a channel for it."
      :reel="reel"
      reel-eyebrow="Pick a game"
      reel-heading="The games we play"
      removable
      testid="casual"
      @archive="archiving = gameOf($event)"
      @remove="removing = gameOf($event)"
    >
      <template #acts>
        <cut-button
          away
          :href="DISCORD_INVITE"
          testid="casual-join"
          tone="solid"
        >
          Join the Discord
        </cut-button>
      </template>

      <remove-game-dialog
        v-if="removing"
        :game="removing"
        open
        @removed="refresh"
        @update:open="removing = null"
      />
      <archive-game-dialog
        v-if="archiving"
        :game="archiving"
        open
        @saved="refresh"
        @update:open="archiving = null"
      />
    </record-index>
  </v-main>
</template>
