<script lang="ts" setup>
/* Every game: its games and esports channels, whether it is in competition and whether Discord has
   any of its channels otherwise than the site keeps them. Opening one renders the site's own game
   editor inside Management. */
import {computed, onMounted, ref} from "vue"
import CutButton from "@/components/island/CutButton.vue"
import FactList from "@/components/island/FactList.vue"
import SearchBox from "@/components/island/SearchBox.vue"
import StateMark from "@/components/island/StateMark.vue"
import ManagementPage from "@/components/management/ManagementPage.vue"
import ManagementRow from "@/components/management/ManagementRow.vue"
import ManagementTable, {type TableColumn} from "@/components/management/ManagementTable.vue"
import {type CataloguedChannel, listCatalogue} from "@/domains/discord"
import {type CasualGame, useCasualGames} from "@/domains/games"

defineOptions({name: "GameListPage"})

const {games, ready} = useCasualGames()
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

const COLUMNS: TableColumn[] = [
  {key: "name", label: "Game", wrap: true},
  {key: "channels", label: "Channels", wrap: true},
  {key: "kind", label: "Kind"},
  {key: "state", label: "State"},
]

// The games played first, the archived ones after them, each by name.
const shown = computed(() => {
  const needle = search.value.trim().toLowerCase()
  return games.value
    .filter((game) => needle === "" || [game.name, game.code, ...channelsOf(game).map((one) => one.name)].some((value) => value.toLowerCase().includes(needle)))
    .sort((a, b) => Number(a.archived) - Number(b.archived) || a.name.localeCompare(b.name))
})

const stateOf = (game: CasualGame): {kind: "not-compared" | "extra" | "in-step"; word: string} => {
  if (game.archived) return {kind: "not-compared", word: "Archived"}
  return differs(game) ? {kind: "extra", word: "Differs on Discord"} : {kind: "in-step", word: "In step"}
}

const facts = computed(() => {
  const live = games.value.filter((one) => !one.archived)
  return [
    {label: "Games", value: String(live.length), sub: `${games.value.length - live.length} archived`},
    {label: "In esports", value: String(live.filter((one) => one.inCompetition).length), sub: "The rest are casual"},
    {label: "Differ on Discord", value: String(live.filter(differs).length), sub: "A channel opened otherwise than set on the site"},
  ]
})

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
      testid="game-list-table"
      :to="(game) => `/management/games/${game.slug}`"
    >
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
        <span :class="{'mg-quiet': channelsOf(row).length === 0}">{{ named(row) }}</span>
      </template>
      <template #kind="{row}">
        {{ row.inCompetition ? "Esports" : "Casual" }}
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
          :meta="`${row.inCompetition ? 'Esports' : 'Casual'} · ${named(row)}`"
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
  </management-page>
</template>

<style scoped>
.games__facts {
  padding: 1.1rem 0 1.2rem;
}
</style>
