<script lang="ts" setup>
/* The association's competition teams: the games each has played and its newest season. Opening one
   renders the site's own line-up editor inside Management, on that newest fielding. */
import {computed, onMounted, ref} from "vue"
import FoldOut from "@/components/island/FoldOut.vue"
import SearchBox from "@/components/island/SearchBox.vue"
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

const shown = computed(() => {
  const needle = search.value.trim().toLowerCase()
  return teams.value
    .filter((team) => needle === "" || [team.name, ...gamesOf(team)].some((value) => value.toLowerCase().includes(needle)))
    .sort((a, b) => a.name.localeCompare(b.name))
})
const live = computed(() => shown.value.filter((one) => !one.archived))
const archived = computed(() => shown.value.filter((one) => one.archived))

onMounted(async () => {
  const [read] = await Promise.all([loadTeams(), ready])
  teams.value = read
  const seasons = await Promise.all(read.map(async (team) => [team.id, await loadTeamSeasons(team.id)] as const))
  fieldings.value = new Map(seasons)
  loaded.value = true
})
</script>

<template>
  <div
    class="teams"
    data-testid="team-list"
  >
    <header class="teams__head">
      <p class="teams__eyebrow">
        Content
      </p>
      <h1 class="teams__title">
        Competition
      </h1>
      <p class="teams__note">
        The teams the association fields, the games they play and their newest season. A team is added from its game's page.
      </p>
    </header>

    <search-box
      v-model="search"
      label="Search teams"
      testid="team-list-search"
    />

    <p
      v-if="loaded && shown.length === 0"
      class="teams__note"
      data-testid="team-list-empty"
    >
      No team matches.
    </p>

    <component
      :is="index === 1 ? FoldOut : 'section'"
      v-for="(group, index) in [live, archived]"
      :key="index"
      v-bind="index === 1 ? {label: `Archived · ${group.length}`, testid: 'team-list-archived'} : {}"
    >
      <ul
        v-if="group.length"
        class="teams__rows"
      >
        <li
          v-for="team in group"
          :key="team.id"
          class="teams__row"
          :data-testid="`team-row-${team.id}`"
        >
          <span class="teams__name">
            <router-link
              v-if="linkOf(team)"
              :to="linkOf(team)!"
            >{{ team.name }}</router-link>
            <template v-else>{{ team.name }}</template>
          </span>
          <span class="teams__sub">{{ gamesOf(team).join(", ") || "Never fielded" }}</span>
          <span class="teams__sub">{{ newest(team)?.season.name ?? "" }}</span>
        </li>
      </ul>
    </component>
  </div>
</template>

<style scoped>
.teams {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  max-width: 76rem;
  padding: 2rem 2.4rem 3rem;
}

.teams__head {
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
}

.teams__eyebrow {
  margin: 0;
  font-size: 11px;
  letter-spacing: 0.3em;
  text-transform: uppercase;
  color: var(--color-eyebrow, var(--color-ash));
}

.teams__title {
  margin: 0;
  font-family: var(--font-display);
  font-size: clamp(1.4rem, 3vw, 2rem);
}

.teams__note {
  margin: 0;
  max-width: 48rem;
  color: var(--color-ash);
}

.teams__rows {
  display: flex;
  flex-direction: column;
  margin: 0;
  padding: 0;
  list-style: none;
  border-top: 1px solid var(--color-hairline);
}

.teams__row {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(0, 1.4fr) 10rem;
  align-items: center;
  gap: 1rem;
  padding: 0.65rem 0.4rem;
  border-bottom: 1px solid var(--color-hairline);
}

.teams__name {
  overflow: hidden;
  font-weight: 600;
  text-overflow: ellipsis;
}

.teams__name a {
  color: var(--color-chalk);
}

.teams__sub {
  overflow: hidden;
  font-size: 0.84rem;
  color: var(--color-ash);
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (max-width: 839px) {
  .teams {
    padding: 1.2rem 1.1rem 2rem;
  }

  .teams__row {
    grid-template-columns: minmax(0, 1fr) auto;
  }
}
</style>
