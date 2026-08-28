<script lang="ts" setup>
import {computed, ref, watch} from "vue"
import IslandDialog from "./IslandDialog.vue"
import ConfirmDialog from "./ConfirmDialog.vue"
import {dropGameOrReason, loadGameContents, saveGameOrReason, type GameRecord} from "../adapters/esports"
import {useGames} from "./useGames"

/**
 * Edits a game from wherever it is shown: its name, page address, intro text, position, accent
 * colour and images.
 *
 * A rejected save keeps the entered values, as SeasonDialog does. Clearing the form because the
 * address was taken would mean retyping it just to see the error again.
 */
defineOptions({name: "GameDialog"})

const props = defineProps<{
  open: boolean
  game: GameRecord | null
  accent?: string
}>()

const emit = defineEmits<{
  (event: "update:open", open: boolean): void
  (event: "saved", game: GameRecord): void
  (event: "removed", game: GameRecord): void
}>()

const {refresh: refreshGames} = useGames()

const name = ref("")
const slug = ref("")
const intro = ref("")
/** The game's accent colour. Distinct from the `accent` prop, which styles the dialog itself. */
const colour = ref("")
const mark = ref("")
const banner = ref("")
const sortIndex = ref(0)
const fielded = ref(true)
const failure = ref<string | null>(null)
const saving = ref(false)

// Opening fills the form from the current record; reopening after a rejection starts clean.
watch(
  () => [props.open, props.game] as const,
  ([open]) => {
    if (!open) return
    const game = props.game
    name.value = game?.name ?? ""
    slug.value = game?.slug ?? ""
    intro.value = game?.intro ?? ""
    colour.value = game?.accent ?? ""
    mark.value = game?.mark ?? ""
    banner.value = game?.banner ?? ""
    sortIndex.value = game?.sortIndex ?? 0
    fielded.value = game?.fielded ?? true
    failure.value = null
  },
  {immediate: true},
)

/**
 * The address as it will be stored, so what is typed matches what the page is served from.
 * GamePageService.addressFor decides it; this only previews the same normalisation.
 */
const addressPreview = computed(() =>
  slug.value.trim().toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-+|-+$/g, ""))

const complete = computed(() => name.value.trim() !== "" && addressPreview.value !== "")

const confirming = ref(false)
const removing = ref(false)
const removalFailure = ref<string | null>(null)
const holds = ref<{teams: number; players: number} | null>(null)

/** The counts are fetched before the dialog opens, since they decide whether deleting is possible at all. */
const askToRemove = async () => {
  const game = props.game
  if (!game) return
  removalFailure.value = null
  holds.value = await loadGameContents(game.game)
  confirming.value = true
}

const countOf = (n: number, one: string, many: string) => `${n} ${n === 1 ? one : many}`

const question = computed(() => {
  const game = props.game
  if (!game) return ""
  const held = holds.value
  if (!held || held.teams === 0) {
    return `${game.name} has no teams. Deleting it removes the game and its page for good.`
  }
  return `${game.name} has ${countOf(held.teams, "team", "teams")} with `
    + `${countOf(held.players, "person", "people")} listed, so it cannot be deleted. `
    + "Uncheck \"Active\" instead to archive it: its page and history stay online."
})

const removeGame = async () => {
  const game = props.game
  if (!game || removing.value) return
  removing.value = true
  removalFailure.value = null
  try {
    const result = await dropGameOrReason(game.game)
    if (!result.ok) {
      // Nothing was deleted, so the dialog stays open and shows the reason.
      removalFailure.value = result.reason
      return
    }
    // Emitted before refetching: dropping the game unmounts the page this dialog is mounted on,
    // and an event from an unmounted component never reaches its handler.
    emit("removed", game)
    confirming.value = false
    emit("update:open", false)
    await refreshGames()
  } finally {
    removing.value = false
  }
}

const submit = async () => {
  const game = props.game
  if (!complete.value || saving.value || !game) return
  saving.value = true
  failure.value = null
  try {
    const result = await saveGameOrReason(game.game, {
      name: name.value.trim(),
      slug: slug.value.trim(),
      intro: intro.value.trim() || null,
      accent: colour.value.trim() || null,
      mark: mark.value.trim() || null,
      banner: banner.value.trim() || null,
      sortIndex: sortIndex.value,
      fielded: fielded.value,
    })
    if (!result.ok) {
      failure.value = result.reason
      return
    }
    // Every page reads this game from the shared records, so refetch them.
    await refreshGames()
    emit("saved", result.game)
    emit("update:open", false)
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <island-dialog
    :accent="colour || props.accent"
    :open="open"
    testid="game-dialog"
    :title="`Edit ${game?.name ?? 'game'}`"
    @update:open="emit('update:open', $event)"
  >
    <form
      class="game-form"
      @submit.prevent="submit"
    >
      <label class="game-form__field">
        <span class="game-form__label">Name</span>
        <input
          v-model="name"
          class="game-form__input"
          data-testid="game-dialog-name"
          maxlength="64"
          required
          type="text"
        >
        <!-- The code is what teams, rosters and game handles reference, so it is fixed. -->
        <span class="game-form__hint">ID: {{ game?.game }} — used internally, cannot be changed</span>
      </label>

      <label class="game-form__field">
        <span class="game-form__label">Page address</span>
        <input
          v-model="slug"
          class="game-form__input"
          data-testid="game-dialog-slug"
          maxlength="64"
          required
          type="text"
        >
        <span class="game-form__hint">esa-blueshell.nl/esports/{{ addressPreview }}</span>
      </label>

      <label class="game-form__field">
        <span class="game-form__label">Intro text</span>
        <textarea
          v-model="intro"
          class="game-form__input game-form__input--tall"
          data-testid="game-dialog-intro"
          maxlength="4000"
          rows="4"
        />
      </label>

      <div class="game-form__row">
        <label class="game-form__field">
          <span class="game-form__label">Accent colour</span>
          <input
            v-model="colour"
            class="game-form__input"
            data-testid="game-dialog-accent"
            maxlength="32"
            placeholder="#ff4655"
            type="text"
          >
          <span class="game-form__hint">Leave empty to use the default blue</span>
        </label>
        <label class="game-form__field game-form__field--narrow">
          <span class="game-form__label">Position</span>
          <input
            v-model.number="sortIndex"
            class="game-form__input"
            data-testid="game-dialog-order"
            type="number"
          >
        </label>
      </div>

      <div class="game-form__row">
        <label class="game-form__field">
          <span class="game-form__label">Icon</span>
          <input
            v-model="mark"
            class="game-form__input"
            data-testid="game-dialog-mark"
            maxlength="255"
            placeholder="valorant.png"
            type="text"
          >
        </label>
        <label class="game-form__field">
          <span class="game-form__label">Background image</span>
          <input
            v-model="banner"
            class="game-form__input"
            data-testid="game-dialog-banner"
            maxlength="255"
            placeholder="valorantesports1.jpg"
            type="text"
          >
        </label>
      </div>

      <label class="game-form__check">
        <input
          v-model="fielded"
          data-testid="game-dialog-fielded"
          type="checkbox"
        >
        <span>
          Active
          <span class="game-form__hint">
            Uncheck to archive. The game's page and history stay online, it just stops appearing in
            menus and when adding a team.
          </span>
        </span>
      </label>

      <p
        v-if="failure"
        class="game-form__failure"
        data-testid="game-dialog-failure"
        role="alert"
      >
        {{ failure }}
      </p>

      <div class="game-form__actions">
        <button
          class="game-form__button game-form__button--drop"
          data-testid="game-dialog-remove"
          type="button"
          @click="askToRemove"
        >
          Remove
        </button>
        <button
          class="game-form__button game-form__button--ghost"
          data-testid="game-dialog-cancel"
          type="button"
          @click="emit('update:open', false)"
        >
          Cancel
        </button>
        <button
          class="game-form__button game-form__button--go"
          data-testid="game-dialog-save"
          :disabled="!complete || saving"
          type="submit"
        >
          {{ saving ? "Saving" : "Save" }}
        </button>
      </div>
    </form>
  </island-dialog>

  <confirm-dialog
    :accent="colour || props.accent"
    :failure="removalFailure"
    :open="confirming"
    :question="question"
    testid="game-remove-dialog"
    title="Remove this game?"
    :working="removing"
    @confirm="removeGame"
    @update:open="confirming = $event"
  />
</template>

<style>
/* Unscoped: the dialog is portalled out of this component's subtree. */
.game-form {
  display: flex;
  flex-direction: column;
  gap: 0.85rem;
}

.game-form__row {
  display: flex;
  gap: 0.7rem;
}

.game-form__field {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 0.3rem;
}

.game-form__field--narrow {
  flex: 0 0 6rem;
}

.game-form__label {
  padding: 0;
  color: #a0a6ac;
  font-size: 0.72rem;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.game-form__hint {
  color: #7d848b;
  font-size: 0.75rem;
  word-break: break-all;
}

.game-form__input {
  width: 100%;
  padding: 0.5rem 0.6rem;
  background: #1c1c1c;
  border: 1px solid rgb(255 255 255 / 12%);
  color: #f2f4f6;
  font-family: inherit;
  font-size: 0.95rem;
}

.game-form__input--tall {
  resize: vertical;
}

.game-form__input:focus-visible {
  border-color: var(--accent, #2f80ed);
  outline: none;
}

.game-form__check {
  display: flex;
  gap: 0.5rem;
  align-items: flex-start;
  color: #f2f4f6;
  font-size: 0.9rem;
}

.game-form__check .game-form__hint {
  display: block;
}

.game-form__failure {
  margin: 0;
  color: #ff6b6b;
  font-size: 0.85rem;
}

.game-form__actions {
  display: flex;
  gap: 0.5rem;
  justify-content: flex-end;
}

.game-form__button {
  padding: 0.45rem 0.9rem;
  border: 1px solid rgb(255 255 255 / 16%);
  color: #f2f4f6;
  cursor: pointer;
  font-family: inherit;
  font-size: 0.85rem;
}

.game-form__button--ghost {
  background: transparent;
}

/* First in the row and set apart, the way the season dialog sets its own removal apart. */
.game-form__button--drop {
  margin-right: auto;
  background: transparent;
  color: #ff9d9d;
}

.game-form__button--go {
  background: var(--accent, #2f80ed);
  border-color: transparent;
  color: #0d0d0d;
}

.game-form__button--go:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>
