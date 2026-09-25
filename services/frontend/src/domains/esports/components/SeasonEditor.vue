<script lang="ts" setup>
import {computed, onMounted, ref} from "vue"
import ConfirmDialog from "@/components/island/ConfirmDialog.vue"
import EditPage from "@/components/island/EditPage.vue"
import PreviewFrame from "@/components/island/PreviewFrame.vue"
import SearchPicker from "@/components/island/SearchPicker.vue"
import Timeline from "@/components/island/Timeline.vue"
import {
  dropSeasonOrReason,
  enterGameInSeason,
  leaveGameInSeason,
  loadSeasonContents,
  loadSeasonGames,
  saveSeasonOrReason,
  type Season,
  type SeasonGame,
} from "../adapters/esports"
import {countOf} from "../copy"
import {forgetCompetitionReads} from "../island/forgetCompetitionReads"
import {seasonStops} from "../island/seasonAxis"
import {useGames} from "../island/useGames"
import {useSeasons} from "../island/useSeasons"

/**
 * A season written down or corrected on its own page, with the strip it will sit on drawn beside
 * the form. An existing season also lists the games entered in it: one the association has played
 * before is entered from here, and one that fields nobody is taken out.
 *
 * A refusal keeps what was typed. Losing three fields because the dates overlapped another season
 * would mean typing them again to find out what the objection was.
 */
defineOptions({name: "SeasonEditor"})

const props = defineProps<{
  /** The season being changed, or nothing to add one. */
  season: Season | null
  /** Where the page goes back to: the competition page it was opened from. */
  back: string
}>()

const emit = defineEmits<{
  (event: "saved", season: Season): void
  (event: "removed", season: Season): void
  (event: "cancel"): void
}>()

const {seasons, refresh: refreshSeasons} = useSeasons()
const {games, identityOf} = useGames()

const name = ref(props.season?.name ?? "")
const startDate = ref(props.season?.startDate ?? "")
const endDate = ref(props.season?.endDate ?? "")
const failure = ref<string | null>(null)
const saving = ref(false)

/** A new season has no id yet; the preview marks its stop with one nothing else can hold. */
const DRAFT_ID = -1

/** The strip as it will read once this is saved: this season, as typed, among the others. */
const previewStops = computed(() => {
  const own = {
    id: props.season?.id ?? DRAFT_ID,
    name: name.value.trim() || "New season",
    startDate: startDate.value || "9999-01-01",
    endDate: endDate.value || "9999-01-01",
    played: props.season?.played ?? false,
  }
  return seasonStops([...seasons.value.filter(one => one.id !== own.id), own])
})

const complete = computed(() => name.value.trim() !== "" && startDate.value !== "" && endDate.value !== "")

const submit = async () => {
  if (!complete.value || saving.value) return
  saving.value = true
  failure.value = null
  try {
    const result = await saveSeasonOrReason({
      id: props.season?.id,
      name: name.value.trim(),
      startDate: startDate.value,
      endDate: endDate.value,
    })
    if (!result.ok) {
      failure.value = result.reason
      return
    }
    forgetCompetitionReads()
    await refreshSeasons()
    emit("saved", result.season)
  } finally {
    saving.value = false
  }
}

/** The games entered in this season, and nothing for a season not yet written down. */
const entered = ref<SeasonGame[]>([])
const gamesFailure = ref<string | null>(null)
const readGames = async () => {
  if (props.season) entered.value = await loadSeasonGames(props.season.id)
}
onMounted(readGames)

const offered = computed(() => games.value
  .filter(one => !entered.value.some(held => held.game === one.code))
  .map(one => ({key: one.code, label: one.name})))

const enter = async (game: string) => {
  const season = props.season
  if (!season) return
  gamesFailure.value = null
  const result = await enterGameInSeason(season.id, game)
  if (!result.ok) {
    gamesFailure.value = result.reason
    return
  }
  forgetCompetitionReads()
  await readGames()
}

const takeOut = async (game: string) => {
  const season = props.season
  if (!season) return
  gamesFailure.value = null
  const result = await leaveGameInSeason(season.id, game)
  if (!result.ok) {
    gamesFailure.value = result.reason
    return
  }
  forgetCompetitionReads()
  await readGames()
}

const confirming = ref(false)
const removing = ref(false)
const removalFailure = ref<string | null>(null)
const holds = ref<{teams: number; players: number} | null>(null)

/**
 * Taking a season away hides everything recorded against it, so how much that is is read before
 * the question is put rather than after it is answered.
 */
const askToRemove = async () => {
  if (!props.season) return
  removalFailure.value = null
  holds.value = await loadSeasonContents(props.season.id)
  confirming.value = true
}

const question = computed(() => {
  const season = props.season
  if (!season) return ""
  const held = holds.value
  if (!held || (held.teams === 0 && held.players === 0)) {
    return `${season.name} holds no teams. Removing it takes it off the strip.`
  }
  return `${season.name} holds ${countOf(held.teams, "team", "teams")} and `
    + `${countOf(held.players, "person", "people")}. Removing the season takes them with it.`
})

const removeSeason = async () => {
  const season = props.season
  if (!season || removing.value) return
  removing.value = true
  removalFailure.value = null
  try {
    const result = await dropSeasonOrReason(season.id)
    if (!result.ok) {
      removalFailure.value = result.reason
      return
    }
    confirming.value = false
    forgetCompetitionReads()
    await refreshSeasons()
    emit("removed", season)
  } finally {
    removing.value = false
  }
}
</script>

<template>
  <edit-page
    :back="{to: back, label: 'Competition'}"
    :eyebrow="season ? season.name : 'Competition'"
    testid="season-edit"
    :title="season ? 'Edit season' : 'Add a season'"
  >
    <form
      id="season-edit-form"
      class="season-form"
      @submit.prevent="submit"
    >
      <label class="season-form__field">
        <span class="season-form__label">Name</span>
        <input
          v-model="name"
          class="season-form__input"
          data-testid="season-edit-name"
          maxlength="64"
          name="name"
          required
          type="text"
        >
      </label>

      <div class="season-form__row">
        <label class="season-form__field">
          <span class="season-form__label">Starts</span>
          <input
            v-model="startDate"
            class="season-form__input"
            data-testid="season-edit-start"
            name="startDate"
            required
            type="date"
          >
        </label>
        <label class="season-form__field">
          <span class="season-form__label">Ends</span>
          <input
            v-model="endDate"
            class="season-form__input"
            data-testid="season-edit-end"
            name="endDate"
            required
            type="date"
          >
        </label>
      </div>

      <p
        v-if="failure"
        class="season-form__failure"
        data-testid="season-edit-failure"
        role="alert"
      >
        {{ failure }}
      </p>
    </form>

    <!-- Only for a season that exists: entering a game is recorded against the season, so there
         has to be one to record it against. Each change lands as it is made. -->
    <section
      v-if="season"
      class="season-form__games"
      data-testid="season-edit-games"
    >
      <h2 class="season-form__heading">
        Games in {{ season.name }}
      </h2>
      <ul
        v-if="entered.length > 0"
        class="season-form__entered"
      >
        <li
          v-for="one in entered"
          :key="one.game"
          class="season-form__game"
          :data-testid="`season-edit-game-${one.game}`"
        >
          <span>{{ identityOf(one.game).name || one.game }}</span>
          <span class="season-form__game-teams">{{ countOf(one.teams.length, "team", "teams") }}</span>
          <button
            class="season-form__take-out"
            :data-testid="`season-edit-take-out-${one.game}`"
            type="button"
            @click="takeOut(one.game)"
          >
            Take out
          </button>
        </li>
      </ul>
      <p
        v-else
        class="season-form__note"
      >
        No games are entered in this season yet.
      </p>
      <search-picker
        empty-note="Every game the association knows is already in this season."
        :options="offered"
        placeholder="Enter a game played before"
        testid-prefix="season-edit-enter"
        @pick="enter"
      />
      <router-link
        class="season-form__new-game"
        data-testid="season-edit-new-game"
        :to="`/competition/new?season=${season.id}`"
      >
        A game the association has not played before →
      </router-link>
      <p
        v-if="gamesFailure"
        class="season-form__failure"
        data-testid="season-edit-games-failure"
        role="alert"
      >
        {{ gamesFailure }}
      </p>
    </section>

    <template #preview>
      <preview-frame>
        <timeline
          accent="var(--color-brand)"
          :selected-id="season?.id ?? DRAFT_ID"
          :stops="previewStops"
          testid-prefix="season-edit-preview"
        />
      </preview-frame>
    </template>

    <template #footer>
      <div class="season-form__actions">
        <button
          v-if="season"
          class="season-form__button season-form__button--drop"
          data-testid="season-edit-remove"
          type="button"
          @click="askToRemove"
        >
          Remove
        </button>
        <button
          class="season-form__button season-form__button--ghost"
          data-testid="season-edit-cancel"
          type="button"
          @click="emit('cancel')"
        >
          Cancel
        </button>
        <button
          class="season-form__button season-form__button--go"
          data-testid="season-edit-save"
          :disabled="!complete || saving"
          form="season-edit-form"
          type="submit"
        >
          {{ saving ? "Saving" : "Save" }}
        </button>
      </div>
    </template>
  </edit-page>

  <confirm-dialog
    :failure="removalFailure"
    :open="confirming"
    :question="question"
    testid="season-remove-dialog"
    title="Remove this season?"
    :working="removing"
    @confirm="removeSeason"
    @update:open="confirming = $event"
  />
</template>

<style scoped>
.season-form {
  display: flex;
  flex-direction: column;
  gap: 0.85rem;
}

.season-form__row {
  display: flex;
  gap: 0.85rem;
}

.season-form__field {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 0.3rem;
  min-width: 0;
}

.season-form__label {
  color: var(--color-ash);
  font-size: 0.72rem;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.season-form__input {
  width: 100%;
  padding: 0.5rem 0.6rem;
  background: var(--color-pit);
  border: 1px solid color-mix(in oklab, var(--color-chalk) 12%, transparent);
  color: var(--color-chalk);
  font-family: inherit;
  font-size: 0.95rem;
}

.season-form__input:focus-visible {
  border-color: var(--edit-accent);
  outline: none;
}

.season-form__failure {
  margin: 0;
  color: var(--color-danger);
  font-size: 0.85rem;
}

.season-form__games {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  margin-top: 1.75rem;
  padding-top: 1.25rem;
  border-top: 1px solid var(--color-hairline);
}

.season-form__heading {
  font-size: 11px;
  font-weight: 500;
  letter-spacing: 0.3em;
  text-transform: uppercase;
  color: var(--color-eyebrow);
}

.season-form__entered {
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.season-form__game {
  display: flex;
  gap: 1rem;
  align-items: center;
  padding: 0.55rem 0.75rem;
  background-color: var(--color-surface);
}

.season-form__game-teams {
  font-size: 0.8rem;
  color: var(--color-ash);
}

.season-form__take-out {
  margin-left: auto;
  padding: 0;
  font-family: inherit;
  font-size: 0.8rem;
  color: var(--color-ash);
  text-decoration: underline;
  cursor: pointer;
  background: none;
  border: 0;
}

.season-form__take-out:hover {
  color: var(--color-chalk);
}

.season-form__note {
  font-size: 0.9rem;
  color: var(--color-ash);
}

.season-form__new-game {
  align-self: flex-start;
  font-family: var(--font-display);
  font-size: 0.75rem;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--edit-accent);
}

.season-form__actions {
  display: flex;
  justify-content: flex-end;
  gap: 0.6rem;
}

.season-form__button {
  padding: 0.45rem 1.1rem;
  border: 0;
  clip-path: polygon(10px 0, 100% 0, calc(100% - 10px) 100%, 0 100%);
  font-family: "Shellhouse One", system-ui, sans-serif;
  font-size: 0.8rem;
  font-style: italic;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  cursor: pointer;
}

.season-form__button--drop {
  margin-right: auto;
  background: color-mix(in oklab, var(--color-danger-tint) 18%, transparent);
  color: var(--color-danger-ink);
}

.season-form__button--drop:hover {
  background: color-mix(in oklab, var(--color-danger-tint) 34%, transparent);
  color: var(--color-danger-ink-strong);
}

.season-form__button--ghost {
  background: var(--color-raised);
  color: var(--color-ash);
}

.season-form__button--go {
  background: var(--edit-accent);
  color: var(--color-void);
}

.season-form__button:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}
</style>
