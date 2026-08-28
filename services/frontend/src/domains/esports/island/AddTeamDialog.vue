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
const candidates = ref<Team[]>([])
const lineup = ref<RosterEntry[]>([])
const lineupFrom = ref<Season | null>(null)
const dropped = ref<Set<number>>(new Set())
const failure = ref<string | null>(null)
const saving = ref(false)

const game = computed<Game | null>(() => props.game ?? chosenGame.value)

/** Teams of the game that are not already playing this season. */
const offered = computed<Team[]>(() =>
  candidates.value.filter(team => !props.fieldedTeamIds.includes(team.id)))

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
    :title="season ? `Add a team to ${season.name}` : 'Add a team'"
    @update:open="emit('update:open', $event)"
  >
    <form
      class="team-form"
      @submit.prevent="submit"
    >
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
        v-if="games && games.length > 0"
        class="team-form__field"
      >
        <span class="team-form__label">Game</span>
        <select
          v-model="chosenGame"
          class="team-form__input"
          data-testid="add-team-game"
          required
        >
          <option
            v-for="option in games"
            :key="option.game"
            :value="option.game"
          >
            {{ option.name }}
          </option>
        </select>
      </label>

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

      <label
        v-else
        class="team-form__field"
      >
        <span class="team-form__label">Team</span>
        <select
          v-model="chosenTeamId"
          class="team-form__input"
          data-testid="add-team-existing"
          required
        >
          <option :value="null">
            Pick a team
          </option>
          <option
            v-for="team in offered"
            :key="team.id"
            :value="team.id"
          >
            {{ team.name }}
          </option>
        </select>
      </label>

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
