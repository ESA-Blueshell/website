<script lang="ts" setup>
import {computed, ref, watch} from "vue"
import IslandDialog from "@/components/island/IslandDialog.vue"
import ConfirmDialog from "@/components/island/ConfirmDialog.vue"
import ImagePicker from "@/components/island/ImagePicker.vue"
import type {Picture} from "@/components/island/pictures"
import {dropBoard, saveBoardOrReason, storeBoardPhoto, type Board} from "../adapters/boards"
import {inkOnAccent} from "../accent"
import {countOf} from "../copy"
import {boardName, romanNumeral} from "../reading"

/**
 * A board written down or corrected, from the page it is read on.
 *
 * Everything a board is: its number, the name it chose, the line it shouted, its colour, what
 * the year was about, the stretch it ran, and the photograph of the people who ran it. One
 * dialog for adding and for correcting, because they are the same fields: what changes is
 * whether a number is suggested and whether there is anything to remove.
 *
 * A refusal keeps what was typed, the way the esports dialogs do. Losing five fields because
 * the number was already taken would mean typing them again to find out what the objection
 * was.
 *
 * `candidate` is not a field here. The column is `NOT NULL` and nothing reads it, and the api
 * fills it from the name, or from the number where the board has no name, for a write that
 * carries none, which is exactly what this sends.
 */
defineOptions({name: "BoardDialog"})

const props = withDefaults(defineProps<{
  open: boolean
  /** The board being corrected, or nothing where one is being added. */
  board: Board | null
  /**
   * The number a board being added is suggested.
   *
   * Read off the boards recorded by the page rather than worked out here: which board comes
   * next is a fact about the line, and the dialog is handed one board at a time.
   */
  nextNumber?: number
  accent?: string
}>(), {nextNumber: 1, accent: undefined})

const emit = defineEmits<{
  (event: "update:open", open: boolean): void
  (event: "saved", board: Board): void
  (event: "removed", board: Board): void
}>()

/** Adding rather than correcting, which is what decides the title and the suggested number. */
const adding = computed(() => props.board == null)

const number = ref(1)
const name = ref("")
const cheer = ref("")
/** The board's own colour, which is not the island accent this dialog is drawn on. */
const colour = ref("")
const description = ref("")
const startDate = ref("")
const endDate = ref("")
/**
 * The photograph now held.
 *
 * Chosen bytes go into storage as they are chosen and reach the board only when this dialog
 * is saved, which is what lets cancelling leave the board on the photograph it had. The
 * picker's own doc comment is the long version.
 */
const photo = ref<Picture | null>(null)
const failure = ref<string | null>(null)
const saving = ref(false)

/** The day part of a date, so a stored timestamp still fills a date field. */
const dayOf = (date?: string | null): string => (date ?? "").trim().slice(0, 10)

// Opening fills the form from the board as it stands; a reopen after a refusal starts clean.
watch(
  () => [props.open, props.board] as const,
  ([open]) => {
    if (!open) return
    const board = props.board
    number.value = board?.number ?? props.nextNumber
    name.value = board?.name ?? ""
    cheer.value = board?.cheer ?? ""
    colour.value = board?.accent ?? ""
    description.value = board?.description ?? ""
    startDate.value = dayOf(board?.startDate)
    endDate.value = dayOf(board?.endDate)
    photo.value = board?.photo ?? null
    failure.value = null
  },
  {immediate: true},
)

const title = computed(() =>
  (props.board ? `Edit ${boardName(props.board.number, props.board.name)}` : "Add a board"))

/** The numeral beside the field, which is what the swatch previews the colour under. */
const numeral = computed(() => romanNumeral(number.value))

/**
 * The colour as it will be painted, and the ink that reads on it.
 *
 * The accent is a fill rather than an ink, so the swatch is a filled block and its label is
 * set in whichever ink the fill takes: the swatch demonstrates the pairing rather than
 * describing it. A blank field paints nothing and the stylesheet's own blue shows through,
 * which is the same colour the page would draw the board in.
 */
const fill = computed(() => colour.value.trim())
const ink = computed(() => (inkOnAccent(fill.value) === "light" ? "#f4f6f8" : "#1c1c1c"))

/** The api wants a number and a first day; everything else about a board may be unknown. */
const complete = computed(() => Number.isInteger(number.value) && number.value > 0 && startDate.value !== "")

const confirming = ref(false)
const removing = ref(false)
const removalFailure = ref<string | null>(null)

/** How many members the board holds, which is what the question counts. */
const membersHeld = computed(() => props.board?.members.length ?? 0)

/**
 * What removing this board would take with it, said before the question is put.
 *
 * The members are already in hand (the page read the board whole) so nothing has to be asked
 * of the api to say how many people are in the way. Whether that is a refusal is still the
 * api's answer rather than this dialog's guess.
 */
const question = computed(() => {
  const board = props.board
  if (!board) return ""
  const named = boardName(board.number, board.name)
  if (membersHeld.value === 0) {
    return `${named} holds no members. Removing it takes it off the timeline.`
  }
  return `${named} holds ${countOf(membersHeld.value, "member", "members")}, and every one of them `
    + "is somebody's place in the association's history."
})

const askToRemove = () => {
  if (!props.board) return
  failure.value = null
  removalFailure.value = null
  confirming.value = true
}

const removeBoard = async () => {
  const board = props.board
  if (!board || removing.value) return
  removing.value = true
  removalFailure.value = null
  try {
    const result = await dropBoard(board.id)
    if (!result.ok) {
      // Nothing has gone, so the question stands and says how many members are in the way.
      removalFailure.value = result.reason
      return
    }
    emit("removed", board)
    confirming.value = false
    emit("update:open", false)
  } finally {
    removing.value = false
  }
}

const submit = async () => {
  if (!complete.value || saving.value) return
  saving.value = true
  failure.value = null
  try {
    const result = await saveBoardOrReason({
      id: props.board?.id,
      number: number.value,
      name: name.value.trim() || null,
      cheer: cheer.value.trim() || null,
      accent: colour.value.trim() || null,
      description: description.value.trim() || null,
      startDate: startDate.value,
      endDate: endDate.value || null,
      // The asset file name the early history still points at, carried through rather than
      // shown: a save replaces every field, so leaving it out would quietly clear it.
      image: props.board?.image ?? null,
      photo: photo.value?.path ?? null,
      version: props.board?.version,
    })
    if (!result.ok) {
      failure.value = result.reason
      return
    }
    emit("saved", result.board)
    emit("update:open", false)
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <island-dialog
    :accent="accent"
    :open="open"
    testid="board-dialog"
    :title="title"
    @update:open="emit('update:open', $event)"
  >
    <form
      id="board-dialog-form"
      class="board-form"
      @submit.prevent="submit"
    >
      <div class="board-form__row">
        <label class="board-form__field board-form__field--narrow">
          <span class="board-form__label">Number</span>
          <input
            v-model.number="number"
            class="board-form__input"
            data-testid="board-dialog-number"
            min="1"
            required
            type="number"
          >
        </label>
        <label class="board-form__field">
          <span class="board-form__label">Name</span>
          <input
            v-model="name"
            class="board-form__input"
            data-testid="board-dialog-name"
            maxlength="255"
            type="text"
          >
          <span class="board-form__hint">
            <!-- A board is free to have chosen no name, and half the history has not. -->
            Leave empty for a board with no name of its own; it reads as
            Board {{ numeral }}
          </span>
        </label>
      </div>

      <span
        v-if="adding"
        class="board-form__hint"
        data-testid="board-dialog-suggested"
      >Board {{ numeral }} is the next one recorded. Type over it if it is not.</span>

      <label class="board-form__field">
        <span class="board-form__label">Cheer</span>
        <input
          v-model="cheer"
          class="board-form__input"
          data-testid="board-dialog-cheer"
          maxlength="255"
          type="text"
        >
        <span class="board-form__hint">The line the board shouted, where it had one</span>
      </label>

      <div class="board-form__row">
        <label class="board-form__field">
          <span class="board-form__label">Colour</span>
          <span class="board-form__colour">
            <input
              v-model="colour"
              class="board-form__input"
              data-testid="board-dialog-accent"
              maxlength="32"
              placeholder="#3387fa"
              type="text"
            >
            <!--
              The colour is a fill, so the swatch is filled with it and its numeral is set in
              whichever ink reads on it. A preview of the field beside it rather than a fact of
              its own, which is why it is not spoken: the value is what the input says.
            -->
            <span
              aria-hidden="true"
              class="board-form__swatch"
              data-testid="board-dialog-swatch"
              :style="{background: fill || undefined, color: ink}"
            >{{ numeral }}</span>
          </span>
          <span class="board-form__hint">
            Leave empty to draw the board in the association's blue
          </span>
        </label>
      </div>

      <label class="board-form__field">
        <span class="board-form__label">Description</span>
        <textarea
          v-model="description"
          class="board-form__input board-form__input--tall"
          data-testid="board-dialog-description"
          maxlength="4000"
          rows="4"
        />
      </label>

      <div class="board-form__row">
        <label class="board-form__field">
          <span class="board-form__label">Takes office</span>
          <input
            v-model="startDate"
            class="board-form__input"
            data-testid="board-dialog-start"
            required
            type="date"
          >
        </label>
        <label class="board-form__field">
          <span class="board-form__label">Hands over</span>
          <input
            v-model="endDate"
            class="board-form__input"
            data-testid="board-dialog-end"
            type="date"
          >
          <span class="board-form__hint">Empty while a board is still in office</span>
        </label>
      </div>

      <!-- Held until Save, like every other field here: closing without saving leaves the
           board on the photograph it was drawn with. -->
      <image-picker
        label="Board photo"
        :picture="photo"
        :store="storeBoardPhoto"
        testid="board-dialog-photo"
        @update:picture="photo = $event"
      />

      <p
        v-if="failure"
        class="board-form__failure"
        data-testid="board-dialog-failure"
        role="alert"
      >
        {{ failure }}
      </p>
    </form>

    <!-- In the footer, like the other dialogs on the island. Save names the form it submits
         rather than sitting inside it, which is what lets it stand out here. -->
    <template #footer>
      <div class="board-form__actions">
        <button
          v-if="board"
          class="board-form__button board-form__button--drop"
          data-testid="board-dialog-remove"
          type="button"
          @click="askToRemove"
        >
          Remove
        </button>
        <button
          class="board-form__button board-form__button--ghost"
          data-testid="board-dialog-cancel"
          type="button"
          @click="emit('update:open', false)"
        >
          Cancel
        </button>
        <button
          class="board-form__button board-form__button--go"
          data-testid="board-dialog-save"
          :disabled="!complete || saving"
          form="board-dialog-form"
          type="submit"
        >
          {{ saving ? "Saving" : "Save" }}
        </button>
      </div>
    </template>
  </island-dialog>

  <confirm-dialog
    :accent="accent"
    confirm-label="Remove the board"
    :failure="removalFailure"
    :open="confirming"
    :question="question"
    testid="board-remove-dialog"
    title="Remove this board?"
    :working="removing"
    @confirm="removeBoard"
    @update:open="confirming = $event"
  />
</template>

<style>
/* Unscoped: the dialog is portalled out of this component's subtree. */
.board-form {
  display: flex;
  flex-direction: column;
  gap: 0.9rem;
  padding-bottom: 0.35rem;
}

.board-form__row {
  display: flex;
  flex-wrap: wrap;
  gap: 0.85rem;
}

.board-form__field {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 0.3rem;
  min-width: 0;
}

.board-form__field--narrow {
  flex: 0 0 5.5rem;
}

.board-form__label {
  padding: 0;
  font-family: var(--font-display);
  font-size: 0.62rem;
  color: var(--color-ash);
  letter-spacing: 0.1em;
  text-transform: uppercase;
}

.board-form__hint {
  font-size: 0.72rem;
  color: color-mix(in oklab, var(--color-ash) 80%, transparent);
  word-break: break-word;
}

/* One field style across the island: flat, square, and lit by the focus ring rather than by
   a border that competes with the labels above it. */
.board-form__input {
  width: 100%;
  padding: 0.55rem 0.75rem;
  font-family: inherit;
  font-size: 0.92rem;
  color: var(--color-chalk);
  background-color: color-mix(in oklab, var(--color-chalk) 7%, transparent);
  border: 0;
}

.board-form__input::placeholder {
  color: var(--color-ash);
}

.board-form__input--tall {
  resize: vertical;
}

.board-form__input:focus-visible {
  outline: none;
  border-color: var(--dialog-accent, var(--color-brand));
}

/* The field and its swatch on one line, so the colour is beside the value it is. */
.board-form__colour {
  display: flex;
  gap: 0.5rem;
  align-items: stretch;
}

/*
 * The colour as the page paints it: a fill with the board's numeral on it, which is exactly
 * what the band is for a board with no photograph. The blue shows through while the field is
 * empty and while it holds something no browser can read as a colour, both of which are a
 * board drawn in the association's blue.
 */
.board-form__swatch {
  display: grid;
  flex: 0 0 3.25rem;
  place-items: center;
  background: var(--color-brand);
  font-family: var(--font-display);
  font-size: 0.9rem;
  line-height: 1;
}

.board-form__failure {
  margin: 0;
  color: var(--color-danger);
  font-size: 0.85rem;
}

/* Its own rule and its own spacing: see the footer in IslandDialog. */
.board-form__actions {
  display: flex;
  gap: 0.5rem;
  justify-content: flex-end;
  margin-top: 1rem;
  padding-top: 0.85rem;
  border-top: 1px solid color-mix(in oklab, var(--color-chalk) 12%, transparent);
}

.board-form__button {
  padding: 0.45rem 0.9rem;
  border: 1px solid color-mix(in oklab, var(--color-chalk) 16%, transparent);
  color: var(--color-chalk);
  cursor: pointer;
  font-family: inherit;
  font-size: 0.85rem;
}

.board-form__button--ghost {
  background: transparent;
}

/* First in the row and set apart, the way the esports dialogs set their own removal apart. */
.board-form__button--drop {
  margin-right: auto;
  background: color-mix(in oklab, var(--color-danger-tint) 18%, transparent);
  color: var(--color-danger-ink);
}

.board-form__button--drop:hover {
  background: color-mix(in oklab, var(--color-danger-tint) 34%, transparent);
  color: var(--color-danger-ink-strong);
}

.board-form__button--go {
  background: var(--dialog-accent, var(--color-brand));
  border-color: transparent;
  color: var(--color-void);
}

.board-form__button:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}
</style>
