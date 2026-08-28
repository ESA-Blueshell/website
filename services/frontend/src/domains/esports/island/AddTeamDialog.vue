<script lang="ts" setup>
import {computed, ref, watch} from "vue"
import IslandDialog from "./IslandDialog.vue"
import {
  fieldTeamInSeason,
  loadRoster,
  loadTeamSeasons,
  loadTeams,
  saveTeamOrReason,
  addToRoster,
  type Game,
  type RosterEntry,
  type Season,
  type Team,
} from "../adapters/esports"

/**
 * Putting a team into the season on show: a new one, or one that played before.
 *
 * A team that played before usually brings the same people with it, so its last line-up is
 * offered — but shown in full and one name at a time, so last season's coach does not quietly
 * reappear. Nothing is written until the whole answer is given.
 */
defineOptions({name: "AddTeamDialog"})

const props = defineProps<{
  open: boolean
  season: Season | null
  /** The game being added to, where the page is about one game. */
  game?: Game
  /** The games to choose from, where it is not. */
  games?: Array<{game: Game; name: string}>
  /** Games already in the season on show, which there is nothing to add. */
  fieldedGames?: Game[]
  /** Teams already playing this season, which there is no sense in offering again. */
  fieldedTeamIds: number[]
  accent?: string
}>()

const emit = defineEmits<{
  (event: "update:open", open: boolean): void
  (event: "added", team: Team): void
}>()

type Source = "new" | "existing"

const source = ref<Source>("new")
const chosenGame = ref<Game | null>(null)
const name = ref("")
const chosenTeamId = ref<number | null>(null)
const teamSearch = ref("")
const candidates = ref<Team[]>([])
const lineup = ref<RosterEntry[]>([])
const lineupFrom = ref<Season | null>(null)
const dropped = ref<Set<number>>(new Set())
const failure = ref<string | null>(null)
const saving = ref(false)

const game = computed<Game | null>(() => props.game ?? chosenGame.value)

/**
 * A game already in the season on show is not one to add to it: putting another team into a
 * game that is already there is what that game's own page is for.
 */
const gamesToOffer = computed(() =>
  (props.games ?? []).filter(one => !(props.fieldedGames ?? []).includes(one.game)))

/**
 * Named for what it does. On a game's own page a team is being added; on the index a game
 * is, and a game arrives in a season by having a team fielded in it.
 */
const heading = computed(() => {
  const where = props.season ? ` to ${props.season.name}` : ""
  return props.game ? `Add a team${where}` : `Add a game${where}`
})

/** Teams of the game that are not already playing this season. */
const offered = computed<Team[]>(() =>
  candidates.value.filter(team => !props.fieldedTeamIds.includes(team.id)))

/**
 * What typing narrows the list to.
 *
 * A game the association has played for years has more teams than a picker can usefully
 * list, so the list is what was asked for rather than everything. Typing nothing offers
 * the first handful, which is enough where there are only a few.
 */
const matches = computed<Team[]>(() => {
  const term = teamSearch.value.trim().toLowerCase()
  const found = term === ""
    ? offered.value
    : offered.value.filter(team => team.name.toLowerCase().includes(term))
  return found.slice(0, 8)
})

const chosenTeam = computed<Team | null>(() =>
  offered.value.find(team => team.id === chosenTeamId.value) ?? null)

const pick = (team: Team) => {
  chosenTeamId.value = team.id
  teamSearch.value = ""
}

const carried = computed<RosterEntry[]>(() => lineup.value.filter(entry => !dropped.value.has(entry.id)))

const complete = computed(() => {
  if (game.value == null) return false
  return source.value === "new" ? name.value.trim() !== "" : chosenTeamId.value != null
})

const reset = () => {
  source.value = "new"
  chosenGame.value = props.game ?? null
  name.value = ""
  chosenTeamId.value = null
  teamSearch.value = ""
  candidates.value = []
  lineup.value = []
  lineupFrom.value = null
  dropped.value = new Set()
  failure.value = null
}

watch(() => props.open, (open) => {
  if (open) reset()
}, {immediate: true})

// The teams to choose from belong to the game, so choosing one asks for them again.
watch([game, source, () => props.open], async ([forGame, from, open]) => {
  if (!open || from !== "existing" || forGame == null) return
  candidates.value = await loadTeams(forGame)
})

/**
 * What the chosen team last fielded, and where it came from.
 *
 * The season it is offered from is named, because "its last line-up" is only useful if the
 * reader can tell which season that was.
 */
watch(chosenTeamId, async (teamId) => {
  lineup.value = []
  lineupFrom.value = null
  dropped.value = new Set()
  if (teamId == null) return
  const played = await loadTeamSeasons(teamId)
  const previous = played.find(one => one.id !== props.season?.id)
  if (!previous) return
  lineupFrom.value = previous
  lineup.value = await loadRoster(teamId, previous.id)
})

const drop = (id: number) => {
  const next = new Set(dropped.value)
  if (next.has(id)) next.delete(id)
  else next.add(id)
  dropped.value = next
}

const reasonFrom = (error: unknown): string => {
  const body = (error as {detail?: string; title?: string})
  return body?.detail || body?.title || "The team could not be added."
}

const submit = async () => {
  const season = props.season
  const forGame = game.value
  if (!complete.value || saving.value || season == null || forGame == null) return
  saving.value = true
  failure.value = null
  try {
    let team: Team | null
    if (source.value === "new") {
      const created = await saveTeamOrReason({game: forGame, name: name.value.trim()})
      if (!created.ok) {
        failure.value = created.reason
        return
      }
      team = created.team
    } else {
      team = candidates.value.find(one => one.id === chosenTeamId.value) ?? null
    }
    if (!team) {
      failure.value = "That team could not be found."
      return
    }

    // Everybody kept is the one request that carries them; anything less is carried by hand,
    // so nobody who was dropped is written down and then deleted.
    const carryWhole = source.value === "existing" && lineup.value.length > 0
      && carried.value.length === lineup.value.length
    const fielded = await fieldTeamInSeason(team.id, season.id, carryWhole)
    if (!fielded) {
      failure.value = "The team could not be added."
      return
    }
    if (!carryWhole) {
      for (const entry of carried.value) {
        await addToRoster(team.id, {
          seasonId: season.id,
          handle: entry.handle,
          role: entry.role,
          userId: entry.userId,
          displayName: entry.displayName,
        })
      }
    }

    emit("added", team)
    emit("update:open", false)
  } catch (error) {
    failure.value = reasonFrom(error)
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <island-dialog
    :accent="accent"
    :open="open"
    testid="add-team-dialog"
    :title="heading"
    @update:open="emit('update:open', $event)"
  >
    <form
      class="team-form"
      @submit.prevent="submit"
    >
      <label
        v-if="gamesToOffer.length > 0"
        class="team-form__field"
      >
        <span class="team-form__label">Game</span>
        <select
          v-model="chosenGame"
          class="team-form__input"
          data-testid="add-team-game"
          required
        >
          <option :value="null">
            Pick a game
          </option>
          <option
            v-for="option in gamesToOffer"
            :key="option.game"
            :value="option.game"
          >
            {{ option.name }}
          </option>
        </select>
      </label>

      <p
        v-else-if="games && games.length > 0"
        class="team-form__note"
        data-testid="add-team-no-games"
      >
        Every game the association knows already plays this season.
      </p>

      <div
        class="team-form__choice"
        role="radiogroup"
      >
        <button
          :aria-checked="source === 'new'"
          class="team-form__toggle"
          :class="{'team-form__toggle--on': source === 'new'}"
          data-testid="add-team-source-new"
          role="radio"
          type="button"
          @click="source = 'new'"
        >
          A new team
        </button>
        <button
          :aria-checked="source === 'existing'"
          class="team-form__toggle"
          :class="{'team-form__toggle--on': source === 'existing'}"
          data-testid="add-team-source-existing"
          role="radio"
          type="button"
          @click="source = 'existing'"
        >
          One that played before
        </button>
      </div>


      <label
        v-if="source === 'new'"
        class="team-form__field"
      >
        <span class="team-form__label">Name</span>
        <input
          v-model="name"
          class="team-form__input"
          data-testid="add-team-name"
          maxlength="128"
          required
          type="text"
        >
      </label>

      <div
        v-else
        class="team-form__field"
      >
        <span class="team-form__label">Team</span>

        <!-- Chosen, and shown as chosen: the search is put away until it is wanted again. -->
        <span
          v-if="chosenTeam"
          class="team-form__chosen"
          data-testid="add-team-chosen"
        >
          {{ chosenTeam.name }}
          <button
            :aria-label="`Choose a different team than ${chosenTeam.name}`"
            class="team-form__unpick"
            data-testid="add-team-unpick"
            type="button"
            @click="chosenTeamId = null"
          >&times;</button>
        </span>

        <template v-else>
          <input
            v-model="teamSearch"
            aria-label="Search the teams that played before"
            class="team-form__input"
            data-testid="add-team-existing"
            placeholder="Search a team that played before"
            type="text"
          >
          <ul
            v-if="matches.length > 0"
            class="team-form__matches"
            data-testid="add-team-matches"
          >
            <li
              v-for="team in matches"
              :key="team.id"
            >
              <button
                class="team-form__match"
                :data-testid="`add-team-match-${team.id}`"
                type="button"
                @click="pick(team)"
              >
                {{ team.name }}
              </button>
            </li>
          </ul>
          <p
            v-else
            class="team-form__note"
            data-testid="add-team-no-matches"
          >
            {{ offered.length === 0
              ? "Every team of this game already plays this season."
              : "No team of this game answers to that." }}
          </p>
        </template>
      </div>

      <fieldset
        v-if="source === 'existing' && lineup.length > 0"
        class="team-form__lineup"
        data-testid="add-team-lineup"
      >
        <legend class="team-form__label">
          Bring across from {{ lineupFrom?.name }}
        </legend>
        <label
          v-for="entry in lineup"
          :key="entry.id"
          class="team-form__player"
          :data-testid="`add-team-player-${entry.id}`"
        >
          <input
            :checked="!dropped.has(entry.id)"
            type="checkbox"
            @change="drop(entry.id)"
          >
          <span class="team-form__handle">{{ entry.handle }}</span>
          <span
            v-if="entry.displayName"
            class="team-form__player-name"
          >{{ entry.displayName }}</span>
        </label>
      </fieldset>

      <p
        v-else-if="source === 'existing' && chosenTeamId != null"
        class="team-form__note"
        data-testid="add-team-no-lineup"
      >
        Nobody has played for this team before, so it starts empty.
      </p>

      <p
        v-if="failure"
        class="team-form__failure"
        data-testid="add-team-failure"
        role="alert"
      >
        {{ failure }}
      </p>

      <div class="team-form__actions">
        <button
          class="team-form__button team-form__button--ghost"
          data-testid="add-team-cancel"
          type="button"
          @click="emit('update:open', false)"
        >
          Cancel
        </button>
        <button
          class="team-form__button team-form__button--go"
          data-testid="add-team-save"
          :disabled="!complete || saving"
          type="submit"
        >
          {{ saving ? "Adding" : "Add" }}
        </button>
      </div>
    </form>
  </island-dialog>
</template>

<style>
/* Unscoped: the dialog is portalled out of this component's subtree. */
.team-form {
  display: flex;
  flex-direction: column;
  gap: 0.85rem;
}

.team-form__choice {
  display: flex;
  gap: 0.4rem;
}

.team-form__toggle {
  flex: 1;
  padding: 0.4rem 0.5rem;
  background: #1c1c1c;
  border: 1px solid rgb(255 255 255 / 12%);
  color: #a0a6ac;
  font-family: inherit;
  font-size: 0.85rem;
  cursor: pointer;
}

.team-form__toggle--on {
  background: var(--dialog-accent, #3387fa);
  border-color: transparent;
  color: #0f1115;
}

.team-form__field {
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
}

.team-form__label {
  padding: 0;
  color: #a0a6ac;
  font-size: 0.72rem;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.team-form__input {
  width: 100%;
  padding: 0.5rem 0.6rem;
  background: #1c1c1c;
  border: 1px solid rgb(255 255 255 / 12%);
  color: #f2f4f6;
  font-family: inherit;
  font-size: 0.95rem;
}

/* The same shape the member picker in the line-up editor uses, so the two read alike. */
.team-form__matches {
  display: flex;
  flex-direction: column;
  gap: 0.1rem;
  margin: 0;
  padding: 0;
  list-style: none;
}

.team-form__match {
  width: 100%;
  padding: 0.35rem 0.5rem;
  background: #262626;
  border: 0;
  color: #f2f4f6;
  cursor: pointer;
  font-family: inherit;
  font-size: 0.9rem;
  text-align: left;
}

.team-form__match:hover,
.team-form__match:focus-visible {
  background: color-mix(in oklab, var(--dialog-accent, #3387fa) 30%, #262626);
}

.team-form__chosen {
  display: inline-flex;
  align-items: center;
  gap: 0.45rem;
  padding: 0.4rem 0.5rem;
  background: #1c1c1c;
  border: 1px solid rgb(255 255 255 / 12%);
  color: #f2f4f6;
  font-size: 0.95rem;
}

.team-form__unpick {
  background: none;
  border: 0;
  color: #a0a6ac;
  cursor: pointer;
  font-size: 1rem;
  line-height: 1;
}

.team-form__lineup {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  margin: 0;
  padding: 0.6rem;
  border: 1px solid rgb(255 255 255 / 10%);
}

.team-form__player {
  display: flex;
  align-items: baseline;
  gap: 0.5rem;
  font-size: 0.92rem;
}

.team-form__player-name {
  color: #a0a6ac;
  font-size: 0.8rem;
}

.team-form__note,
.team-form__failure {
  margin: 0;
  font-size: 0.85rem;
}

.team-form__note {
  color: #a0a6ac;
}

.team-form__failure {
  color: #ff6b6b;
}

.team-form__actions {
  display: flex;
  justify-content: flex-end;
  gap: 0.6rem;
  margin-top: 0.35rem;
}

.team-form__button {
  padding: 0.45rem 1.1rem;
  border: 0;
  clip-path: polygon(10px 0, 100% 0, calc(100% - 10px) 100%, 0 100%);
  font-family: "Fugaz One", system-ui, sans-serif;
  font-size: 0.8rem;
  font-style: italic;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  cursor: pointer;
}

.team-form__button--ghost {
  background: #2e2e2e;
  color: #a0a6ac;
}

.team-form__button--go {
  background: var(--dialog-accent, #3387fa);
  color: #0f1115;
}

.team-form__button:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}
</style>
