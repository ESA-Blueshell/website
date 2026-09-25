<script lang="ts" setup>
import {computed, onMounted, ref} from "vue"
import ConfirmDialog from "@/components/island/ConfirmDialog.vue"
import EditPage from "@/components/island/EditPage.vue"
import PreviewFrame from "@/components/island/PreviewFrame.vue"
import SearchPicker from "@/components/island/SearchPicker.vue"
import CutButton from "@/components/island/CutButton.vue"
import FormControl from "@/components/island/FormControl.vue"
import FormField from "@/components/island/FormField.vue"
import FormFields from "../../../components/island/FormFields.vue"
import FormSection from "../../../components/island/FormSection.vue"
import IconButton from "@/components/island/IconButton.vue"
import NoticeBox from "@/components/island/NoticeBox.vue"
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
    <template
      v-if="season"
      #actions
    >
      <cut-button
        testid="season-edit-remove"
        tone="quiet"
        @click="askToRemove"
      >
        Remove season
      </cut-button>
    </template>

    <form
      id="season-edit-form"
      class="season-form"
      @submit.prevent="submit"
    >
      <form-section title="The season">
        <form-fields>
          <div class="form-span">
            <form-control
              v-model="name"
              data-testid="season-edit-name"
              label="Name*"
              maxlength="64"
            />
          </div>
          <form-control
            v-model="startDate"
            data-testid="season-edit-start"
            kind="date"
            label="Starts*"
          />
          <form-control
            v-model="endDate"
            data-testid="season-edit-end"
            kind="date"
            label="Ends*"
            :min="startDate || undefined"
          />
        </form-fields>
        <notice-box
          v-if="failure"
          testid="season-edit-failure"
          tone="danger"
        >
          {{ failure }}
        </notice-box>
      </form-section>

      <!-- Only for a season that exists: entering a game is recorded against the season, so there
           has to be one to record it against. Each change lands as it is made. -->
      <form-section
        v-if="season"
        testid="season-edit-games"
        :title="`Games in ${season.name}`"
      >
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
            <span class="season-form__game-name">{{ identityOf(one.game).name || one.game }}</span>
            <span class="season-form__game-teams">{{ countOf(one.teams.length, "team", "teams") }}</span>
            <icon-button
              danger
              :label="`Take ${identityOf(one.game).name || one.game} out`"
              :testid="`season-edit-take-out-${one.game}`"
              @click="takeOut(one.game)"
            >
              <svg
                aria-hidden="true"
                fill="none"
                stroke="currentColor"
                stroke-width="1.6"
                viewBox="0 0 24 24"
              ><path d="M4.5 7h15M9.5 7V4.5h5V7M6.5 7l1 13h9l1-13" /></svg>
            </icon-button>
          </li>
        </ul>
        <p
          v-else
          class="season-form__note"
        >
          No games are entered in this season yet.
        </p>
        <form-fields>
          <form-field
            class="form-span"
            label="Enter a game played before"
            variant="inside"
          >
            <template #default="{controlId, labelId}">
              <search-picker
                :control-id="controlId"
                empty-note="Every game the association knows is already in this season."
                :labelled-by="labelId"
                :options="offered"
                testid-prefix="season-edit-enter"
                @pick="enter"
              />
            </template>
          </form-field>
        </form-fields>
        <div>
          <cut-button
            :href="`/competition/new?season=${season.id}`"
            testid="season-edit-new-game"
            tone="quiet"
          >
            Add a new game
          </cut-button>
        </div>
        <notice-box
          v-if="gamesFailure"
          testid="season-edit-games-failure"
          tone="danger"
        >
          {{ gamesFailure }}
        </notice-box>
      </form-section>
      <button
        class="season-form__enter"
        tabindex="-1"
        type="submit"
      />
    </form>

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
        <cut-button
          testid="season-edit-cancel"
          tone="quiet"
          @click="emit('cancel')"
        >
          Cancel
        </cut-button>
        <cut-button
          :disabled="!complete || saving"
          testid="season-edit-save"
          tone="solid"
          @click="submit"
        >
          {{ saving ? "Saving" : season ? "Save season" : "Add the season" }}
        </cut-button>
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
/* Enter in a field saves, as in any form; the bar's Save is the button a pointer finds. */
.season-form__enter {
  display: none;
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
  padding: 0.35rem 0.5rem 0.35rem 1rem;
  background-color: var(--band-ground);
}

.season-form__game-name {
  font-family: var(--font-display);
  font-size: 0.95rem;
  text-transform: uppercase;
  color: var(--color-chalk);
}

.season-form__game-teams {
  margin-right: auto;
  font-size: 0.82rem;
  color: var(--color-ash);
}

.season-form__note {
  font-size: 0.9rem;
  color: var(--color-ash);
}

.season-form__actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 0.5rem;
}
</style>
