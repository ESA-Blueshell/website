<script lang="ts" setup>
/* The association's competition teams: the games each has played and its newest season. Opening one
   renders the site's own line-up editor inside Management, on that newest fielding. */
import {computed, onMounted, ref} from "vue"
import FactList from "@/components/island/FactList.vue"
import SearchBox from "@/components/island/SearchBox.vue"
import StateMark from "@/components/island/StateMark.vue"
import ManagementPage from "@/components/management/ManagementPage.vue"
import ManagementRow from "@/components/management/ManagementRow.vue"
import ManagementTable, {type TableColumn} from "@/components/management/ManagementTable.vue"
import {type Fielding, type Team, loadTeamSeasons, loadTeams, useGames} from "@/domains/esports"

defineOptions({name: "TeamListPage"})

const {recordOf, ready} = useGames()
const teams = ref<Team[]>([])
const fieldings = ref<Map<number, Fielding[]>>(new Map())
const loaded = ref(false)
const search = ref("")

const fieldedIn = (team: Team) => fieldings.value.get(team.id) ?? []
const newest = (team: Team) => [...fieldedIn(team)].sort((a, b) => b.season.startDate.localeCompare(a.season.startDate))[0] ?? null
const gamesOf = (team: Team) => [...new Set(fieldedIn(team).map((one) => recordOf(one.game)?.name ?? one.game))]
const linkOf = (team: Team) => {
  const latest = newest(team)
  const slug = latest ? recordOf(latest.game)?.slug : null
  return slug && latest ? `/management/competition/${slug}/teams/${team.id}?season=${latest.season.id}` : null
}

const COLUMNS: TableColumn[] = [
  {key: "name", label: "Team", wrap: true},
  {key: "games", label: "Games", wrap: true},
  {key: "season", label: "Newest season"},
  {key: "state", label: "State"},
]

// The teams fielded first, the archived ones after them, each by name.
const shown = computed(() => {
  const needle = search.value.trim().toLowerCase()
  return teams.value
    .filter((team) => needle === "" || [team.name, ...gamesOf(team)].some((value) => value.toLowerCase().includes(needle)))
    .sort((a, b) => Number(a.archived) - Number(b.archived) || a.name.localeCompare(b.name))
})

const facts = computed(() => {
  const live = teams.value.filter((one) => !one.archived)
  return [
    {label: "Teams", value: String(live.length), sub: `${teams.value.length - live.length} archived`},
    {label: "Games played", value: String(new Set(live.flatMap(gamesOf)).size), sub: "Across every season"},
    {label: "Never fielded", value: String(live.filter((team) => fieldedIn(team).length === 0).length), sub: "In no season yet"},
  ]
})

onMounted(async () => {
  const [read] = await Promise.all([loadTeams(), ready])
  teams.value = read
  const seasons = await Promise.all(read.map(async (team) => [team.id, await loadTeamSeasons(team.id)] as const))
  fieldings.value = new Map(seasons)
  loaded.value = true
})
</script>

<template>
  <management-page
    eyebrow="Content"
    testid="team-list"
    title="Competition"
  >
    <template #lede>
      The teams the association fields, the games they play and their newest season. A team is added from its game's
      page.
    </template>

    <fact-list
      class="teams__facts"
      :facts="facts"
    />

    <management-table
      :columns="COLUMNS"
      :row-key="(team) => team.id"
      :row-testid="(team) => `team-row-${team.id}`"
      :rows="shown"
      testid="team-list-table"
    >
      <template #count>
        <b>{{ shown.length }}</b> of {{ teams.length }} teams
      </template>
      <template #search>
        <search-box
          v-model="search"
          label="Search teams"
          testid="team-list-search"
        />
      </template>
      <template
        v-if="loaded"
        #empty
      >
        <span data-testid="team-list-empty">No team matches.</span>
      </template>
      <template #name="{row}">
        <router-link
          v-if="linkOf(row)"
          class="mg-name"
          :to="linkOf(row)!"
        >
          {{ row.name }}
        </router-link>
        <span
          v-else
          class="mg-name"
        >{{ row.name }}</span>
      </template>
      <template #games="{row}">
        <span :class="{'mg-quiet': gamesOf(row).length === 0}">{{ gamesOf(row).join(", ") || "Never fielded" }}</span>
      </template>
      <template #season="{row}">
        <span :class="{'mg-quiet': !newest(row)}">{{ newest(row)?.season.name ?? "·" }}</span>
      </template>
      <template #state="{row}">
        <state-mark :kind="row.archived ? 'not-compared' : 'in-step'">
          {{ row.archived ? "Archived" : "Active" }}
        </state-mark>
      </template>
      <template #phone="{row}">
        <management-row
          :meta="[gamesOf(row).join(', ') || 'Never fielded', newest(row)?.season.name].filter(Boolean).join(' · ')"
          :name="row.name"
          :testid="`team-row-${row.id}`"
          :to="linkOf(row) ?? ''"
        >
          <state-mark :kind="row.archived ? 'not-compared' : 'in-step'">
            {{ row.archived ? "Archived" : "Active" }}
          </state-mark>
        </management-row>
      </template>
    </management-table>
  </management-page>
</template>

<style scoped>
.teams__facts {
  padding: 1.1rem 0 1.2rem;
}
</style>
