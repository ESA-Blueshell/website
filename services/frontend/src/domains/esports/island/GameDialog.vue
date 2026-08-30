<script lang="ts" setup>
import {computed, ref, watch} from "vue"
import IslandDialog from "./IslandDialog.vue"
import ConfirmDialog from "./ConfirmDialog.vue"
import ImagePicker from "./ImagePicker.vue"
import {
  addGameOrReason,
  enterGameInSeason,
  dropGameOrReason,
  loadGameContents,
  saveGameOrReason,
  type EsportsImage,
  type GameRecord,
  type Season,
} from "../adapters/esports"
import {FileType} from "@/services/api"
import {useGames} from "./useGames"

/**
 * Correcting a game from wherever it is shown: what it is called, what its page answers to and
 * says, where it sits among the others, and the art it is drawn with.
 *
 * A refusal keeps what was typed, the way the season dialog does. Losing an address because
 * another game claimed it would mean typing it again to find out what the objection was.
 */
defineOptions({name: "GameDialog"})

const props = defineProps<{
  open: boolean
  /** The game being corrected, or nothing where one is being added. */
  game: GameRecord | null
  /**
   * The season a game added here runs in, where one is being added.
   *
   * A game is added because the association has started playing it, so it is entered in the
   * season on show by the same save. Adding it and then entering it would be two acts for one
   * decision, and would leave a game behind if the second half failed.
   */
  enterIn?: Season | null
  accent?: string
}>()

const emit = defineEmits<{
  (event: "update:open", open: boolean): void
  (event: "saved", game: GameRecord): void
  (event: "removed", game: GameRecord): void
}>()

/** Adding rather than correcting, which is the whole of what changes about this dialog. */
const adding = computed(() => props.game == null)

const {refresh: refreshGames} = useGames()

const name = ref("")
const slug = ref("")
const intro = ref("")
/** The game's own colour, which is not the island accent this dialog is drawn on. */
const colour = ref("")
const icon = ref<EsportsImage | null>(null)
const banner = ref<EsportsImage | null>(null)
const sortIndex = ref(0)
const failure = ref<string | null>(null)
const saving = ref(false)

// Opening fills the form from the game as it stands; a reopen after a refusal starts clean.
watch(
  () => [props.open, props.game] as const,
  ([open]) => {
    if (!open) return
    const game = props.game
    name.value = game?.name ?? ""
    slug.value = game?.slug ?? ""
    intro.value = game?.intro ?? ""
    colour.value = game?.accent ?? ""
    icon.value = game?.icon ?? null
    banner.value = game?.banner ?? null
    sortIndex.value = game?.sortIndex ?? 0
    failure.value = null
  },
  {immediate: true},
)

/**
 * The address as it will be stored, so what is typed and what is reachable are the same thing.
 * GamePageService.addressFor is the rule; this shows it rather than deciding it.
 */
const addressPreview = computed(() =>
  slug.value.trim().toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-+|-+$/g, ""))

const complete = computed(() => name.value.trim() !== "" && addressPreview.value !== "")

const confirming = ref(false)
const removing = ref(false)
const removalFailure = ref<string | null>(null)
const holds = ref<{teams: number; players: number} | null>(null)

/**
 * How much a game holds is read before the question is put rather than after it is answered,
 * because what it holds is what decides whether it can go at all.
 */
const askToRemove = async () => {
  const game = props.game
  if (!game) return
  removalFailure.value = null
  failure.value = null
  const held = await loadGameContents(game.game)
  if (held == null) {
    // No question is put on a guess. Offering to remove a game while saying it holds nothing,
    // when what it holds could not be read, is the one wrong thing this dialog could say here.
    // Said on this dialog rather than the confirmation, which is precisely what does not open.
    failure.value = "What this game holds could not be read, so it cannot be removed yet. Try again."
    return
  }
  holds.value = held
  confirming.value = true
}

const countOf = (n: number, one: string, many: string) => `${n} ${n === 1 ? one : many}`

const question = computed(() => {
  const game = props.game
  if (!game) return ""
  const held = holds.value
  // `held` is never null here: the question is only put once it has been read.
  if (!held || held.teams === 0) {
    return `${game.name} holds no teams. Removing it takes it and its page off the site.`
  }
  return `${game.name} holds ${countOf(held.teams, "team", "teams")} and `
    + `${countOf(held.players, "roster place", "roster places")}, so it cannot be removed. `
    + "Everything it played stays readable, and it leaves the pages that show what the "
    + "association plays by not being entered in a season."
})

const removeGame = async () => {
  const game = props.game
  if (!game || removing.value) return
  removing.value = true
  removalFailure.value = null
  try {
    const result = await dropGameOrReason(game.game)
    if (!result.ok) {
      // Nothing has gone, so the dialog stands and says why.
      removalFailure.value = result.reason
      return
    }
    // Said before the records are re-read: forgetting the game unmounts the page this dialog
    // is on, and an emit from a component that is going nowhere reaches nobody.
    emit("removed", game)
    confirming.value = false
    emit("update:open", false)
    await refreshGames()
  } finally {
    removing.value = false
  }
}

const submit = async () => {
  if (!complete.value || saving.value) return
  saving.value = true
  failure.value = null
  try {
    if (adding.value) {
      await add()
      return
    }
    const game = props.game!
    const result = await saveGameOrReason(game.game, {
      name: name.value.trim(),
      slug: slug.value.trim(),
      intro: intro.value.trim() || null,
      accent: colour.value.trim() || null,
      banner: banner.value?.path ?? null,
      icon: icon.value?.path ?? null,
      sortIndex: sortIndex.value,
    })
    if (!result.ok) {
      failure.value = result.reason
      return
    }
    // Every page draws this game from the records, so they are what has to be brought up to date.
    await refreshGames()
    emit("saved", result.game)
    emit("update:open", false)
  } finally {
    saving.value = false
  }
}

/**
 * A game added, described in full and entered in the season on show.
 *
 * Two requests behind one Save, because the api records the game and the season it runs in
 * separately — but a refusal on the first leaves nothing written, and the whole form stands
 * with what was typed so the address can be corrected without typing it all again.
 */
const add = async () => {
  const made = await addGameOrReason({
    name: name.value.trim(),
    slug: slug.value.trim(),
    intro: intro.value.trim() || null,
    accent: colour.value.trim() || null,
    banner: banner.value?.path ?? null,
    icon: icon.value?.path ?? null,
    sortIndex: sortIndex.value,
  })
  if (!made.ok) {
    failure.value = made.reason
    return
  }
  const season = props.enterIn
  if (season) await enterGameInSeason(season.id, made.game.game)
  await refreshGames()
  emit("saved", made.game)
  emit("update:open", false)
}
</script>

<template>
  <island-dialog
    :accent="colour || props.accent"
    :open="open"
    testid="game-dialog"
    :title="adding ? 'A game we have started playing' : `Edit ${game?.name}`"
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
        <!-- The code is what a team, a roster and a member's handle already point at. It is
             taken from the name when the game is added, and never changes after. -->
        <span
          v-if="!adding"
          class="game-form__hint"
        >Known to everything else as {{ game?.game }}, which does not change</span>
        <span
          v-else
          class="game-form__hint"
        >Its name settles what everything else will call it, which never changes after</span>
      </label>

      <label class="game-form__field">
        <span class="game-form__label">Address</span>
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
        <span class="game-form__label">What the page says</span>
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
          <span class="game-form__label">Colour</span>
          <input
            v-model="colour"
            class="game-form__input"
            data-testid="game-dialog-accent"
            maxlength="32"
            placeholder="#ff4655"
            type="text"
          >
          <span class="game-form__hint">Empty reads on the association's own blue</span>
        </label>
        <label class="game-form__field game-form__field--narrow">
          <span class="game-form__label">Order</span>
          <input
            v-model.number="sortIndex"
            class="game-form__input"
            data-testid="game-dialog-order"
            type="number"
          >
        </label>
      </div>

      <!-- Both held until Save, like every other field here: closing without saving leaves
           the game drawn on the pictures it was drawn on. They are the picture in the game's
           slice on the index and the logo beside its name there, and a game has no others. -->
      <image-picker
        :kind="FileType.GAME_BANNER"
        label="Banner"
        :picture="banner"
        testid="game-dialog-banner"
        @update:picture="banner = $event"
      />

      <image-picker
        :kind="FileType.GAME_ICON"
        label="Icon"
        :picture="icon"
        testid="game-dialog-icon"
        @update:picture="icon = $event"
      />

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
          v-if="!adding"
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
