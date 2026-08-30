<script lang="ts" setup>
import {computed, ref, watch} from "vue"
import IslandDialog from "./IslandDialog.vue"
import {fieldTeamInSeason, loadTeams, type Game, type Season, type Team} from "../adapters/esports"

/**
 * Putting a team that played before into the season on show.
 *
 * The pool is the association's rather than this game's, so every team is offered — including
 * one that has only ever played something else, which is the whole point of the pool being
 * shared. Fielding BS HyperS in a game it has never played is reachable here and nowhere else.
 *
 * The line-up it last had in this game comes with it. A team fielded again usually brings the
 * same people, and a board that had to retype five handles would be worse off than before this
 * pane existed. Which line-up, and who of it, is a question the import control answers.
 */
defineOptions({name: "FieldTeamDialog"})

const props = defineProps<{
  open: boolean
  game: Game
  season: Season | null
  /** Teams already fielded in this game this season, which there is nothing to add. */
  alreadyFielded: number[]
  accent?: string
}>()

const emit = defineEmits<{
  (event: "update:open", open: boolean): void
  (event: "fielded", team: Team): void
}>()

const pool = ref<Team[]>([])
const search = ref("")
const failure = ref<string | null>(null)
const fielding = ref<number | null>(null)

watch(() => props.open, async (open) => {
  if (!open) return
  search.value = ""
  failure.value = null
  fielding.value = null
  pool.value = await loadTeams()
}, {immediate: true})

/** Every team the association has, less the ones already playing this game this season. */
const offered = computed(() =>
  pool.value.filter(team => !props.alreadyFielded.includes(team.id)))

/**
 * What typing narrows the list to.
 *
 * One flat list in the order the api gives them, which is by name. A game the association has
 * played for years has more teams than a picker can usefully show at once, so the list is what
 * was asked for rather than everything; typing nothing offers the first handful, which is
 * enough where there are only a few.
 */
const matches = computed(() => {
  const term = search.value.trim().toLowerCase()
  const found = term === ""
    ? offered.value
    : offered.value.filter(team => team.name.toLowerCase().includes(term))
  return found.slice(0, 8)
})

const field = async (team: Team) => {
  const season = props.season
  if (!season || fielding.value != null) return
  fielding.value = team.id
  failure.value = null
  try {
    const done = await fieldTeamInSeason(team.id, props.game, season.id, true)
    if (!done) {
      failure.value = "That team could not be fielded this season."
      return
    }
    emit("fielded", team)
    emit("update:open", false)
  } finally {
    fielding.value = null
  }
}
</script>

<template>
  <island-dialog
    :accent="accent"
    :open="open"
    testid="field-team-dialog"
    :title="season ? `A team that played before, in ${season.name}` : 'A team that played before'"
    @update:open="emit('update:open', $event)"
  >
    <div class="field-team">
      <label class="field-team__field">
        <span class="field-team__label">Team</span>
        <input
          v-model="search"
          aria-label="Search the teams the association has"
          class="field-team__input"
          data-testid="field-team-search"
          placeholder="Search every team"
          type="text"
        >
      </label>

      <ul
        v-if="matches.length > 0"
        class="field-team__list"
        data-testid="field-team-matches"
      >
        <li
          v-for="team in matches"
          :key="team.id"
        >
          <button
            class="field-team__pick"
            :data-testid="`field-team-${team.id}`"
            :disabled="fielding != null"
            type="button"
            @click="field(team)"
          >
            {{ team.name }}
          </button>
        </li>
      </ul>

      <p
        v-else
        class="field-team__note"
        data-testid="field-team-no-matches"
      >
        {{ offered.length === 0
          ? "Every team the association has already plays this game this season."
          : "No team answers to that." }}
      </p>

      <p
        v-if="failure"
        class="field-team__failure"
        data-testid="field-team-failure"
        role="alert"
      >
        {{ failure }}
      </p>
    </div>
  </island-dialog>
</template>

<style scoped>
.field-team {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.field-team__field {
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
}

.field-team__label {
  font-family: var(--font-body);
  font-size: 0.75rem;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  opacity: 0.8;
}

.field-team__input {
  padding: 0.5rem 0.6rem;
  font-family: var(--font-body);
  font-size: 0.95rem;
  color: inherit;
  background: rgb(255 255 255 / 6%);
  border: 1px solid rgb(255 255 255 / 16%);
  border-radius: 2px;
}

.field-team__list {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  padding: 0;
  margin: 0;
  list-style: none;
}

.field-team__pick {
  width: 100%;
  padding: 0.6rem 0.75rem;
  font-family: var(--font-body);
  font-size: 0.95rem;
  color: inherit;
  text-align: left;
  cursor: pointer;
  background: rgb(255 255 255 / 4%);
  border: 1px solid rgb(255 255 255 / 12%);
  border-radius: 2px;
}

.field-team__pick:hover:not(:disabled) {
  background: rgb(255 255 255 / 10%);
}

.field-team__pick:disabled {
  cursor: default;
  opacity: 0.6;
}

.field-team__note,
.field-team__failure {
  margin: 0;
  font-family: var(--font-body);
  font-size: 0.85rem;
}

.field-team__failure {
  color: var(--color-danger, #ff6b6b);
}
</style>
