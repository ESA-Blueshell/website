<script lang="ts" setup>
/* The association's esports teams: the games each has played, its latest season, and its role and
   channels on Discord. Opening one renders the site's own line-up editor inside Management, on
   that latest fielding. */
import {computed, onMounted, ref} from "vue"
import FactList from "@/components/island/FactList.vue"
import SearchBox from "@/components/island/SearchBox.vue"
import StateMark from "@/components/island/StateMark.vue"
import ManagementPage from "@/components/management/ManagementPage.vue"
import ManagementRow from "@/components/management/ManagementRow.vue"
import ManagementTable, {type TableColumn} from "@/components/management/ManagementTable.vue"
import {type CohortSummary, type SummaryTarget, TargetMark, TargetSystem, fetchCohorts} from "@/domains/cohorts"
import {type CataloguedChannel, isArchive, listCatalogue} from "@/domains/discord"
import {type Fielding, type Team, loadFieldings, loadTeamSeasons, loadTeams, useGames} from "@/domains/esports"

defineOptions({name: "TeamListPage"})

const {recordOf, ready} = useGames()
const teams = ref<Team[]>([])
const fieldings = ref<Map<number, Fielding[]>>(new Map())
const cohorts = ref<CohortSummary[]>([])
const channels = ref<CataloguedChannel[]>([])
const loaded = ref(false)
const search = ref("")

const fieldedIn = (team: Team) => fieldings.value.get(team.id) ?? []
const latest = (team: Team) => [...fieldedIn(team)].sort((a, b) => b.season.startDate.localeCompare(a.season.startDate))[0] ?? null
const gamesOf = (team: Team) => [...new Set(fieldedIn(team).map((one) => recordOf(one.game)?.name ?? one.game))]
const linkOf = (team: Team) => {
  const newest = latest(team)
  const slug = newest ? recordOf(newest.game)?.slug : null
  return slug && newest ? `/management/competition/${slug}/teams/${team.id}?season=${newest.season.id}` : null
}

/* A team's role is its cohort's Discord target, and its channels are the ones that role has access to. */
const targetsOf = (team: Team): SummaryTarget[] => cohorts.value.find((one) => one.definitionKey === `TEAM_PLAYERS:${team.id}`)?.targets ?? []
const channelsOf = (team: Team): string[] => {
  const role = targetsOf(team).find((one) => one.system === TargetSystem.DISCORD && one.made)?.externalId
  if (!role) return []
  return channels.value.filter((one) => one.kind !== "CATEGORY" && !isArchive(one.category) && one.roleIds.includes(role)).map((one) => `#${one.name}`)
}

const COLUMNS: TableColumn[] = [
  {key: "name", label: "Team", wrap: true},
  {key: "games", label: "Games", wrap: true},
  {key: "season", label: "Latest season"},
  {key: "discord", label: "Discord"},
  {key: "channels", label: "Channels", wrap: true},
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

/* The teams are shown only once their fieldings are known: a team drawn before that reads as never
   fielded and has no page to open. Where the one read gives no answer, each team is asked for its own. */
onMounted(async () => {
  const [read, all, summaries, catalogue] = await Promise.all([
    loadTeams(), loadFieldings(), fetchCohorts().catch(() => []), listCatalogue(), ready,
  ])
  fieldings.value = all ?? new Map(await Promise.all(read.map(async (team) => [team.id, await loadTeamSeasons(team.id)] as const)))
  cohorts.value = summaries
  channels.value = catalogue
  teams.value = read
  loaded.value = true
})
</script>

<template>
  <management-page
    eyebrow="Content"
    testid="team-list"
    title="Esports"
  >
    <template #lede>
      The teams the association fields, the games they play, their latest season and what each has on Discord. A team
      is added from its game's page.
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
      :to="linkOf"
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
        <span :class="{'mg-quiet': !latest(row)}">{{ latest(row)?.season.name ?? "·" }}</span>
      </template>
      <template #discord="{row}">
        <target-mark
          :quiet="row.archived"
          :system="TargetSystem.DISCORD"
          :targets="targetsOf(row)"
          :testid="`team-discord-${row.id}`"
        />
      </template>
      <template #channels="{row}">
        <span
          :class="{'mg-quiet': channelsOf(row).length === 0}"
          :data-testid="`team-channels-${row.id}`"
        >{{ channelsOf(row).join(", ") || "No channel" }}</span>
      </template>
      <template #state="{row}">
        <state-mark :kind="row.archived ? 'not-compared' : 'in-step'">
          {{ row.archived ? "Archived" : "Active" }}
        </state-mark>
      </template>
      <template #phone="{row}">
        <management-row
          :meta="[gamesOf(row).join(', ') || 'Never fielded', latest(row)?.season.name, ...channelsOf(row)].filter(Boolean).join(' · ')"
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
