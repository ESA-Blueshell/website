<script lang="ts" setup>
import {computed, ref, watch} from "vue"
import IslandDialog from "./IslandDialog.vue"
import ConfirmDialog from "./ConfirmDialog.vue"
import {dropSeason, loadSeasonContents, saveSeasonOrReason, type Season} from "../adapters/esports"

/**
 * Writing a season down, from wherever the seasons are shown: an existing one to change it,
 * nothing to add one.
 *
 * A refusal keeps what was typed. Losing three fields because the dates overlapped another
 * season would mean typing them again to find out what the objection was.
 */
defineOptions({name: "SeasonDialog"})

const props = defineProps<{
  open: boolean
  /** The season being changed, or nothing to add one. */
  season: Season | null
  accent?: string
}>()

const emit = defineEmits<{
  (event: "update:open", open: boolean): void
  (event: "saved", season: Season): void
  (event: "removed", season: Season): void
}>()

const name = ref("")
const startDate = ref("")
const endDate = ref("")
const failure = ref<string | null>(null)
const saving = ref(false)

// Opening fills the form from the season as it stands; a reopen after a refusal starts clean.
watch(
  () => [props.open, props.season] as const,
  ([open]) => {
    if (!open) return
    name.value = props.season?.name ?? ""
    startDate.value = props.season?.startDate ?? ""
    endDate.value = props.season?.endDate ?? ""
    failure.value = null
  },
  {immediate: true},
)

const title = computed(() => (props.season ? "Edit season" : "Add season"))

const confirming = ref(false)
const removing = ref(false)
const removalFailure = ref<string | null>(null)
const holds = ref<{teams: number; players: number} | null>(null)

/**
 * Taking a season away hides everything recorded against it, so how much that is is read
 * before the question is put rather than after it is answered.
 */
const askToRemove = async () => {
  if (!props.season) return
  removalFailure.value = null
  holds.value = await loadSeasonContents(props.season.id)
  confirming.value = true
}

const countOf = (n: number, one: string, many: string) => `${n} ${n === 1 ? one : many}`

const question = computed(() => {
  const season = props.season
  if (!season) return ""
  const held = holds.value
  if (!held || (held.teams === 0 && held.players === 0)) {
    return `${season.name} holds no teams. Removing it takes it off the strip.`
  }
  return `${season.name} holds ${countOf(held.teams, "team", "teams")} and `
    + `${countOf(held.players, "roster place", "roster places")}. Removing the season takes them with it.`
})

const removeSeason = async () => {
  const season = props.season
  if (!season || removing.value) return
  removing.value = true
  removalFailure.value = null
  try {
    await dropSeason(season.id)
    emit("removed", season)
    confirming.value = false
    emit("update:open", false)
  } catch (error) {
    // Nothing has gone, so the dialog stands and says why.
    const body = (error as {detail?: string; title?: string})
    removalFailure.value = body?.detail || body?.title || "The season could not be removed."
  } finally {
    removing.value = false
  }
}

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
    if (result.season) emit("saved", result.season)
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
    testid="season-dialog"
    :title="title"
    @update:open="emit('update:open', $event)"
  >
    <form
      id="season-dialog-form"
      class="season-form"
      @submit.prevent="submit"
    >
      <label class="season-form__field">
        <span class="season-form__label">Name</span>
        <input
          v-model="name"
          class="season-form__input"
          data-testid="season-dialog-name"
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
            data-testid="season-dialog-start"
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
            data-testid="season-dialog-end"
            name="endDate"
            required
            type="date"
          >
        </label>
      </div>

      <p
        v-if="failure"
        class="season-form__failure"
        data-testid="season-dialog-failure"
        role="alert"
      >
        {{ failure }}
      </p>
    </form>

    <!-- In the footer, like the other dialogs on the island. Save names the form it submits
         rather than sitting inside it, which is what lets it stand out here. -->
    <template #footer>
      <div class="season-form__actions">
        <button
          v-if="season"
          class="season-form__button season-form__button--drop"
          data-testid="season-dialog-remove"
          type="button"
          @click="askToRemove"
        >
          Remove
        </button>
        <button
          class="season-form__button season-form__button--ghost"
          data-testid="season-dialog-cancel"
          type="button"
          @click="emit('update:open', false)"
        >
          Cancel
        </button>
        <button
          class="season-form__button season-form__button--go"
          data-testid="season-dialog-save"
          :disabled="!complete || saving"
          form="season-dialog-form"
          type="submit"
        >
          {{ saving ? "Saving" : "Save" }}
        </button>
      </div>
    </template>
  </island-dialog>

  <confirm-dialog
    :accent="accent"
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

<style>
/* Unscoped: the dialog is portalled out of this component's subtree. */
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
  color: #a0a6ac;
  font-size: 0.72rem;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.season-form__input {
  width: 100%;
  padding: 0.5rem 0.6rem;
  background: #1c1c1c;
  border: 1px solid rgb(255 255 255 / 12%);
  color: #f2f4f6;
  font-family: inherit;
  font-size: 0.95rem;
}

.season-form__input:focus-visible {
  border-color: var(--dialog-accent, #3387fa);
  outline: none;
}

.season-form__failure {
  margin: 0;
  color: #ff6b6b;
  font-size: 0.85rem;
}

/* Its own rule and its own spacing: see the footer in IslandDialog. */
.season-form__actions {
  display: flex;
  justify-content: flex-end;
  gap: 0.6rem;
  margin-top: 1rem;
  padding-top: 0.85rem;
  border-top: 1px solid color-mix(in oklab, var(--color-chalk) 12%, transparent);
}

.season-form__button {
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

.season-form__button--drop {
  margin-right: auto;
  background: color-mix(in oklab, #e0696c 18%, transparent);
  color: #eba7a7;
}

.season-form__button--drop:hover {
  background: color-mix(in oklab, #e0696c 34%, transparent);
  color: #fff2f2;
}

.season-form__button--ghost {
  background: #2e2e2e;
  color: #a0a6ac;
}

.season-form__button--go {
  background: var(--dialog-accent, #3387fa);
  color: #0f1115;
}

.season-form__button:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}
</style>
