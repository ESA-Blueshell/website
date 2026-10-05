<script lang="ts" setup>
/* Every game: its games and esports channels, whether it is in competition and whether Discord has
   any of its channels otherwise than the site keeps them. The ticked games without a channel can be
   given one together. Opening one renders the site's own game editor inside Management. */
import {computed, onMounted, ref} from "vue"
import CutButton from "@/components/island/CutButton.vue"
import FactList from "@/components/island/FactList.vue"
import SearchBox from "@/components/island/SearchBox.vue"
import SelectionBar from "@/components/island/SelectionBar.vue"
import StateMark from "@/components/island/StateMark.vue"
import BulkAdd from "@/components/management/BulkAdd.vue"
import ManagementPage from "@/components/management/ManagementPage.vue"
import ManagementRow from "@/components/management/ManagementRow.vue"
import ManagementTable, {type TableColumn} from "@/components/management/ManagementTable.vue"
import RowCheck from "@/components/management/RowCheck.vue"
import {useUserSelection} from "@/composables/useUserSelection"
import {type CataloguedChannel, ChannelMark, GameChannelCategory, listCatalogue, makeGameChannel} from "@/domains/discord"
import {type CasualGame, addGameChannel, setGameArchived, useCasualGames} from "@/domains/games"

defineOptions({name: "GameListPage"})

const {games, ready, refresh} = useCasualGames()
const catalogue = ref<CataloguedChannel[]>([])
const loaded = ref(false)
const search = ref("")

const differing = computed(() => new Set(catalogue.value.filter((one) => one.access?.differs).map((one) => one.id)))
const channelsOf = (game: CasualGame) => [...game.channels, ...game.esportsChannels]
const differs = (game: CasualGame) => channelsOf(game).some((one) => differing.value.has(one.id))
const named = (game: CasualGame) => {
  const channels = channelsOf(game)
  return channels.length === 0 ? "No channel" : channels.map((one) => `#${one.name}`).join(", ")
}

/* A game is casual unless archived and in esports while a team is fielded in it, so it can be
   both: the two are not kinds of game. */
const playedAs = (game: CasualGame) => [game.archived ? null : "Casual", game.inCompetition ? "Esports" : null].filter(Boolean).join(" and ") || "Neither"

const COLUMNS: TableColumn<CasualGame>[] = [
  {key: "name", label: "Game", wrap: true, sortBy: (game) => game.name},
  {key: "channels", label: "Channels", wrap: true, sortBy: (game) => channelsOf(game).map((one) => one.name).join(", ")},
  {key: "kind", label: "Played as", sortBy: playedAs},
  {key: "state", label: "State", sortBy: (game) => stateOf(game).word},
]

// The games played first, the archived ones after them, each by name.
const shown = computed(() => {
  const needle = search.value.trim().toLowerCase()
  return games.value
    .filter((game) => needle === "" || [game.name, game.code, ...channelsOf(game).map((one) => one.name)].some((value) => value.toLowerCase().includes(needle)))
    .sort((a, b) => Number(a.archived) - Number(b.archived) || a.name.localeCompare(b.name))
})

const stateOf = (game: CasualGame): {kind: "not-compared" | "extra" | "in-sync"; word: string} => {
  if (game.archived) return {kind: "not-compared", word: "Archived"}
  return differs(game) ? {kind: "extra", word: "Differs on Discord"} : {kind: "in-sync", word: "In sync"}
}

const facts = computed(() => {
  const live = games.value.filter((one) => !one.archived)
  return [
    {label: "Games", value: String(live.length), sub: `${games.value.length - live.length} archived`},
    {label: "In esports", value: String(live.filter((one) => one.inCompetition).length), sub: "A team is fielded this season; casual as well"},
    {label: "Differ on Discord", value: String(live.filter(differs).length), sub: "A channel opened otherwise than set on the site"},
  ]
})

/* Ticked games are given a channel in the Games category together, named by the game's address. A
   game that has a channel, or is archived, is left out and said so before anything is made. */
const {selectedIdsArray, isSelected, toggle, headerState, toggleHeader, selectMany, clear: clearSelection} =
  useUserSelection(computed(() => shown.value.map((one) => one.code)))
const ticked = computed(() => games.value.filter((one) => isSelected(one.code)))
const adding = ref(false)

/* Archiving takes a game off the casual lists and bringing it back returns it; both in bulk, each
   leaving out the ticked games already there. */
const archiving = ref<boolean | null>(null)
const toArchive = computed(() => ticked.value
  .filter((one) => one.archived !== archiving.value)
  .map((one) => ({key: one.code, name: one.name, note: channelsOf(one).length ? named(one) : "", game: one})))
const notArchived = computed(() => ticked.value
  .filter((one) => one.archived === archiving.value)
  .map((one) => ({name: one.name, why: archiving.value ? "Archived already" : "Not archived"})))
const archive = async ({game}: {game: CasualGame}) => {
  const answered = await setGameArchived(game.code, archiving.value === true)
  return answered.ok ? {ok: true as const} : answered
}
const archiveWords = computed(() => {
  const count = `${toArchive.value.length} ${toArchive.value.length === 1 ? "game" : "games"}`
  return archiving.value
    ? {plan: "will be archived: off the casual lists and pickers, their pages and history kept. Nothing is changed yet.",
      ask: `Archive ${count} now? They can be brought back from this list.`, go: `Archive ${count}`, doing: "Archiving", done: "archived"}
    : {plan: "will be brought back onto the casual lists. Nothing is changed yet.",
      ask: `Bring ${count} back now?`, go: `Bring ${count} back`, doing: "Bringing back", done: "brought back"}
})

const toAdd = computed(() => ticked.value
  .filter((one) => !one.archived && one.channels.length === 0)
  .map((one) => ({key: one.code, name: one.name, note: `#${one.slug} under Games`, game: one})))
const leftOut = computed(() => ticked.value
  .filter((one) => one.archived || one.channels.length > 0)
  .map((one) => ({name: one.name, why: one.archived ? "Archived" : "Has a channel already"})))
const addChannel = async ({game}: {game: CasualGame}) => {
  const made = await makeGameChannel(game.slug, GameChannelCategory.GAMES)
  if (!made.ok) return made
  const saved = await addGameChannel(game, {id: made.saved.id, guildId: made.saved.guildId, name: made.saved.name})
  return saved.ok ? {ok: true as const} : saved
}
const added = async () => {
  await refresh()
  catalogue.value = await listCatalogue()
  clearSelection()
}

onMounted(async () => {
  const [, listed] = await Promise.all([ready, listCatalogue()])
  catalogue.value = listed
  loaded.value = true
})
</script>

<template>
  <management-page
    eyebrow="Content"
    testid="game-list"
    title="Games"
  >
    <template #lede>
      Every game, its channels on Discord and where Discord differs from the access set on the site.
    </template>
    <template #actions>
      <cut-button
        href="/management/games/new"
        testid="game-list-new"
      >
        Add a game
      </cut-button>
    </template>

    <fact-list
      class="games__facts"
      :facts="facts"
    />

    <management-table
      :columns="COLUMNS"
      :row-key="(game) => game.code"
      :row-testid="(game) => `game-row-${game.code}`"
      :rows="shown"
      :header-state="headerState"
      :selected-count="selectedIdsArray.length"
      testid="game-list-table"
      :to="(game) => `/management/games/${game.slug}`"
      :total="games.length"
      @clear-selection="clearSelection"
      @select-all="selectMany(games.map((one) => one.code))"
      @toggle-shown="toggleHeader"
    >
      <template #check="{row}">
        <row-check
          :checked="isSelected(row.code)"
          :label="`Select ${row.name}`"
          :testid="`game-check-${row.code}`"
          @toggle="toggle(row.code)"
        />
      </template>
      <template #count>
        <b>{{ shown.length }}</b> of {{ games.length }} games
      </template>
      <template #search>
        <search-box
          v-model="search"
          label="Search games"
          testid="game-list-search"
        />
      </template>
      <template
        v-if="loaded"
        #empty
      >
        <span data-testid="game-list-empty">No game matches.</span>
      </template>
      <template #name="{row}">
        <router-link
          class="mg-name"
          :to="`/management/games/${row.slug}`"
        >
          {{ row.name }}
        </router-link>
      </template>
      <template #channels="{row}">
        <span
          v-if="channelsOf(row).length > 0"
          class="games__channels"
        >
          <channel-mark
            v-for="channel in channelsOf(row)"
            :id="channel.id"
            :key="channel.id"
            :guild-id="channel.guildId"
            :name="channel.name"
          />
        </span>
        <span
          v-else
          class="mg-quiet"
        >No channel</span>
      </template>
      <template #kind="{row}">
        {{ playedAs(row) }}
      </template>
      <template #state="{row}">
        <state-mark
          :kind="stateOf(row).kind"
          :testid="`game-differs-${row.code}`"
        >
          {{ stateOf(row).word }}
        </state-mark>
      </template>
      <template #phone="{row}">
        <management-row
          :meta="`${playedAs(row)} · ${named(row)}`"
          :name="row.name"
          :testid="`game-row-${row.code}`"
          :to="`/management/games/${row.slug}`"
        >
          <state-mark
            :kind="stateOf(row).kind"
            :testid="`game-differs-${row.code}`"
          >
            {{ stateOf(row).word }}
          </state-mark>
        </management-row>
      </template>
    </management-table>

    <selection-bar
      always
      :count="selectedIdsArray.length"
      testid="game-list-selection"
      @clear="clearSelection"
    >
      <cut-button
        small
        testid="game-add-channels"
        tone="solid"
        @click="adding = true"
      >
        Add Discord channel
      </cut-button>
      <cut-button
        small
        testid="game-archive"
        @click="archiving = true"
      >
        Archive
      </cut-button>
      <cut-button
        small
        testid="game-unarchive"
        @click="archiving = false"
      >
        Bring back
      </cut-button>
    </selection-bar>

    <bulk-add
      each="a channel in the Games category on Discord"
      :items="toAdd"
      :noun="['game', 'games']"
      :open="adding"
      :run="addChannel"
      :skipped="leftOut"
      testid="game-bulk-add"
      title="Add Discord channels"
      @done="added"
      @update:open="adding = $event"
    />

    <bulk-add
      :items="toArchive"
      :noun="['game', 'games']"
      :open="archiving !== null"
      :run="archive"
      :skipped="notArchived"
      testid="game-bulk-archive"
      :title="archiving ? 'Archive games' : 'Bring games back'"
      :words="archiveWords"
      @done="added"
      @update:open="archiving = null"
    />
  </management-page>
</template>

<style scoped>
.games__channels {
  display: inline-flex;
  flex-wrap: wrap;
  gap: 0.3rem 0.5rem;
}

.games__facts {
  padding: 1.1rem 0 1.2rem;
}
</style>
