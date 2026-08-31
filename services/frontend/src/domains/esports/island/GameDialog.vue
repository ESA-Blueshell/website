<script lang="ts" setup>
import {computed, ref, watch} from "vue"
import IslandDialog from "./IslandDialog.vue"
import ConfirmDialog from "./ConfirmDialog.vue"
import ImagePicker from "./ImagePicker.vue"
import IslandChoice from "./IslandChoice.vue"
import IslandPicker from "./IslandPicker.vue"
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
import {gameHoldsHistory} from "../refusals"
import {useGames} from "./useGames"

/**
 * A game, corrected or added: what it is called, what its page answers to and says, where it
 * sits among the others, and the art it is drawn with.
 *
 * Adding one asks first which kind of adding it is, at the top, where the answer changes what
 * the rest of the dialog is: a game the association has played before is picked out of the ones
 * it knows, and a game it has just started playing is described here in full. One way in from
 * the band and the choice made inside it — two plusses on the band would read as two different
 * things to do, when they are one intention answered two ways.
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
   * shown season by the same save. Adding it and then entering it would be two acts for one
   * decision, and would leave a game behind if the second half failed.
   */
  enterIn?: Season | null
  /** The games already in that season, which there is nothing to add. */
  alreadyIn?: string[]
  accent?: string
}>()

const emit = defineEmits<{
  (event: "update:open", open: boolean): void
  (event: "saved", game: GameRecord): void
  (event: "removed", game: GameRecord): void
}>()

/** Adding rather than correcting, which is the whole of what changes about this dialog. */
const adding = computed(() => props.game == null)

/** Which kind of adding, asked at the top because it decides what the rest of this is. */
type Kind = "played-before" | "new-game"
const kind = ref<Kind>("played-before")

const {games: allGames} = useGames()

/** Every game the association knows that is not already in the season being added to. */
const offered = computed(() =>
  allGames.value.filter(one => !(props.alreadyIn ?? []).includes(one.game)))

const entering = ref<string | null>(null)

/** A game it has played before is already described; what is being recorded is that it runs again. */
const enter = async (game: string) => {
  const season = props.enterIn
  if (!season || entering.value != null) return
  entering.value = game
  failure.value = null
  try {
    const added = await enterGameInSeason(season.id, game)
    if (!added) {
      failure.value = "That game could not be put into the season."
      return
    }
    await refreshGames()
    const record = allGames.value.find(one => one.game === game)
    if (record) emit("saved", record)
    emit("update:open", false)
  } finally {
    entering.value = null
  }
}

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
    kind.value = game == null ? "played-before" : "new-game"
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

const question = computed(() => {
  const game = props.game
  if (!game) return ""
  const held = holds.value
  // `held` is never null here: the question is only put once it has been read.
  if (!held || held.teams === 0) {
    return `${game.name} holds no teams. Deleting it takes it and its page off the site.`
  }
  // The same sentence the api's refusal composes, from the one function that writes it: the
  // question before the act and the answer after it cannot say different things.
  return gameHoldsHistory(game.name, held.teams, held.players)
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
 * A game added, described in full and entered in the shown season.
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
    :title="adding ? 'Add a game to the season' : `Edit ${game?.name}`"
    @update:open="emit('update:open', $event)"
  >
    <!--
      Asked first, because the answer decides what the rest of this dialog is. One way in from
      the band and the choice made here: two plusses on the band would read as two different
      things to do, when they are one intention answered two ways.
    -->
    <island-choice
      v-if="adding"
      v-model="kind"
      :options="[
        {key: 'played-before', label: 'An existing game'},
        {key: 'new-game', label: 'A new game'},
      ]"
      testid-prefix="game-dialog-kind"
    />

    <div
      v-if="adding && kind === 'played-before'"
      class="game-form"
    >
      <island-picker
        :disabled="entering != null"
        empty-note="Every game the association knows is already in this season."
        :options="offered.map(one => ({key: one.game, label: one.name}))"
        placeholder="Search every game"
        testid-prefix="game-dialog-known"
        @pick="enter"
      />

      <p
        v-if="failure"
        class="game-form__failure"
        data-testid="game-dialog-failure"
        role="alert"
      >
        {{ failure }}
      </p>
    </div>

    <form
      v-else
      id="game-dialog-form"
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
        >Code: {{ game?.game }} — set when the game was added, and never changes</span>
        <span
          v-else
          class="game-form__hint"
        >The code everything else files this game under is taken from the name, and never
          changes after</span>
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
          <span class="game-form__label">Colour</span>
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
      <!-- Side by side, because they are decided together and are the two halves of how a
           game is drawn. They wrap onto their own lines where there is no room for both. -->
      <div class="game-form__pictures">
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
      </div>

      <p
        v-if="failure"
        class="game-form__failure"
        data-testid="game-dialog-failure"
        role="alert"
      >
        {{ failure }}
      </p>
    </form>

    <!--
      In the footer, like the team dialog's: the way out of a dialog belongs where it was the
      last time you looked, not at the far end of a form that scrolls. Save names the form it
      submits rather than sitting inside it, which is what lets it stand out here at all.

      Nothing to put here while an existing game is being picked: choosing one enters it, so
      there is no answer to confirm.
    -->
    <template #footer>
      <div
        v-if="!(adding && kind === 'played-before')"
        class="game-form__actions"
      >
        <button
          v-if="!adding"
          class="game-form__button game-form__button--drop"
          data-testid="game-dialog-remove"
          type="button"
          @click="askToRemove"
        >
          Delete
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
          form="game-dialog-form"
          type="submit"
        >
          {{ saving ? "Saving" : "Save" }}
        </button>
      </div>
    </template>
  </island-dialog>

  <confirm-dialog
    :accent="colour || props.accent"
    confirm-label="Delete the game"
    :failure="removalFailure"
    :open="confirming"
    :question="question"
    testid="game-remove-dialog"
    title="Delete this game?"
    :working="removing"
    working-label="Deleting"
    @confirm="removeGame"
    @update:open="confirming = $event"
  />
</template>

<style>
/* Unscoped: the dialog is portalled out of this component's subtree. */
.game-form {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  padding-bottom: 0.35rem;
}

.game-form__row {
  display: flex;
  flex-wrap: wrap;
  gap: 0.9rem;
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
  font-family: var(--font-display);
  font-size: 0.62rem;
  color: var(--color-ash);
  letter-spacing: 0.1em;
  text-transform: uppercase;
}

.game-form__hint {
  font-size: 0.72rem;
  color: color-mix(in oklab, var(--color-ash) 80%, transparent);
  word-break: break-word;
}

/* One field style across the island: flat, square, and lit by the focus ring rather than by
   a border that competes with the labels above it. */
.game-form__input {
  width: 100%;
  padding: 0.55rem 0.75rem;
  font-family: inherit;
  font-size: 0.92rem;
  color: var(--color-chalk);
  background-color: color-mix(in oklab, var(--color-chalk) 7%, transparent);
  border: 0;
}

.game-form__input::placeholder {
  color: var(--color-ash);
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

/* Side by side, because they are decided together and are the two halves of how a game is
   drawn. Aligned along the bottom so a wide banner and a square logo share a baseline. */
.game-form__pictures {
  display: flex;
  flex-wrap: wrap;
  gap: 1.1rem;
  align-items: flex-end;
  padding-top: 0.15rem;
}

.game-form__failure {
  margin: 0;
  color: #ff6b6b;
  font-size: 0.85rem;
}

/* Its own rule and its own spacing: see the footer in IslandDialog. */
.game-form__actions {
  display: flex;
  gap: 0.5rem;
  justify-content: flex-end;
  margin-top: 1rem;
  padding-top: 0.85rem;
  border-top: 1px solid color-mix(in oklab, var(--color-chalk) 12%, transparent);
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
  background: color-mix(in oklab, #e0696c 18%, transparent);
  color: #eba7a7;
}

.game-form__button--drop:hover {
  background: color-mix(in oklab, #e0696c 34%, transparent);
  color: #fff2f2;
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
