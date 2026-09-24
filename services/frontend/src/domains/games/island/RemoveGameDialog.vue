<script lang="ts" setup>
import {computed, ref, watch} from "vue"
import ModalDialog from "@/components/island/ModalDialog.vue"
import {loadGameHoldings, removeCasualGame, type CasualGame, type GameHoldings} from "../adapters/games"
import {plural, sentenceFor} from "../refusals"

/**
 * Removing a game, confirmed twice because it is easy to regret.
 *
 * The first step says what the removal touches, read before the question is put; the second
 * asks for the game's name typed out. A game with teams in competition cannot go at all, and the
 * first step says so instead of asking. Removing is soft: the row stays, so it can be restored.
 */
defineOptions({name: "RemoveGameDialog"})

const props = defineProps<{open: boolean; game: CasualGame}>()

const emit = defineEmits<{
  (event: "update:open", open: boolean): void
  (event: "removed", game: CasualGame): void
}>()

type Step = "reading" | "touches" | "type-name"
const step = ref<Step>("reading")
const holdings = ref<GameHoldings | null>(null)
const typed = ref("")
const failure = ref<string | null>(null)
const working = ref(false)

watch(() => props.open, async open => {
  if (!open) return
  step.value = "reading"
  typed.value = ""
  failure.value = null
  holdings.value = await loadGameHoldings(props.game.code)
  // No question is put on a guess: what it holds decides whether it can go at all.
  if (holdings.value == null) failure.value = "What this game holds could not be read, so it cannot be removed yet. Try again."
  step.value = "touches"
}, {immediate: true})


const held = computed(() => (holdings.value?.teams ?? 0) > 0)

const touches = computed(() => {
  const h = holdings.value
  if (!h) return ""
  if (held.value) {
    // The refusal the api would answer, said before the question rather than after it.
    return sentenceFor({code: "GameHoldsHistory", gameName: props.game.name, teams: h.teams, players: h.players}) ?? ""
  }
  const parts = [
    plural(h.channels, "channel", "channels"),
    plural(h.committees, "committee", "committees"),
    plural(h.events, "event", "events"),
  ]
  return `Removing takes ${props.game.name} off every page, list and picker. It is linked to ${parts[0]}, `
    + `${parts[1]} and ${parts[2]}, which stop naming it.`
})

const matches = computed(() => typed.value.trim() === props.game.name)

const remove = async () => {
  if (!matches.value || working.value) return
  working.value = true
  failure.value = null
  try {
    const result = await removeCasualGame(props.game.code)
    if (!result.ok) {
      failure.value = result.reason
      return
    }
    emit("removed", props.game)
    emit("update:open", false)
  } finally {
    working.value = false
  }
}
</script>

<template>
  <modal-dialog
    :open="open"
    testid="remove-game-dialog"
    :title="`Remove ${game.name}?`"
    @update:open="emit('update:open', $event)"
  >
    <p
      v-if="step === 'reading'"
      class="remove-game__said"
      data-testid="remove-game-reading"
    >
      Reading what {{ game.name }} holds.
    </p>
    <p
      v-else-if="step === 'touches' && holdings"
      class="remove-game__said"
      data-testid="remove-game-touches"
    >
      {{ touches }}
    </p>
    <form
      v-else-if="step === 'type-name'"
      id="remove-game-form"
      class="remove-game__form"
      @submit.prevent="remove"
    >
      <label class="remove-game__field">
        <span class="remove-game__label">Type {{ game.name }} to remove it</span>
        <input
          v-model="typed"
          autocomplete="off"
          class="remove-game__input"
          data-testid="remove-game-name"
          type="text"
        >
      </label>
    </form>
    <p
      v-if="failure"
      class="remove-game__failure"
      data-testid="remove-game-failure"
      role="alert"
    >
      {{ failure }}
    </p>

    <template #footer>
      <div class="remove-game__actions">
        <button
          class="remove-game__button"
          data-testid="remove-game-cancel"
          type="button"
          @click="emit('update:open', false)"
        >
          Keep it
        </button>
        <button
          v-if="step === 'touches' && holdings && !held"
          class="remove-game__button remove-game__button--drop"
          data-testid="remove-game-next"
          type="button"
          @click="step = 'type-name'"
        >
          Remove
        </button>
        <button
          v-if="step === 'type-name'"
          class="remove-game__button remove-game__button--drop"
          data-testid="remove-game-confirm"
          :disabled="!matches || working"
          form="remove-game-form"
          type="submit"
        >
          {{ working ? "Removing" : "Remove for good" }}
        </button>
      </div>
    </template>
  </modal-dialog>
</template>

<style scoped>
.remove-game__said {
  margin: 0;
  font-size: 0.95rem;
  line-height: 1.55;
  color: var(--color-chalk);
}

.remove-game__form {
  display: flex;
  flex-direction: column;
}

.remove-game__field {
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
}

.remove-game__label {
  font-family: var(--font-display);
  font-size: 0.62rem;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: var(--color-ash);
}

.remove-game__input {
  width: 100%;
  padding: 0.55rem 0.75rem;
  font-family: inherit;
  font-size: 0.92rem;
  color: var(--color-chalk);
  background-color: color-mix(in oklab, var(--color-chalk) 7%, transparent);
  border: 0;
}

.remove-game__failure {
  margin: 0.8rem 0 0;
  font-size: 0.85rem;
  color: var(--color-danger);
}

.remove-game__actions {
  display: flex;
  gap: 0.5rem;
  justify-content: flex-end;
  margin-top: 1rem;
  padding-top: 0.85rem;
  border-top: 1px solid color-mix(in oklab, var(--color-chalk) 12%, transparent);
}

.remove-game__button {
  padding: 0.45rem 0.9rem;
  font-family: inherit;
  font-size: 0.85rem;
  color: var(--color-chalk);
  cursor: pointer;
  background: transparent;
  border: 1px solid color-mix(in oklab, var(--color-chalk) 16%, transparent);
}

.remove-game__button--drop {
  color: var(--color-danger-ink);
  background: color-mix(in oklab, var(--color-danger-tint) 18%, transparent);
}

.remove-game__button--drop:hover {
  color: var(--color-danger-ink-strong);
  background: color-mix(in oklab, var(--color-danger-tint) 34%, transparent);
}

.remove-game__button:disabled {
  cursor: default;
  opacity: 0.5;
}
</style>
