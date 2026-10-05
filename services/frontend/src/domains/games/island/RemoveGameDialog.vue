<script lang="ts" setup>
import {computed, ref, watch} from "vue"
import CutButton from "@/components/island/CutButton.vue"
import FormField from "@/components/island/FormField.vue"
import ModalDialog from "@/components/island/ModalDialog.vue"
import TextInput from "@/components/island/TextInput.vue"
import {loadGameHoldings, removeCasualGame, type CasualGame, type GameHoldings} from "../adapters/games"
import {sentenceFor} from "../refusals"
import {countOf} from "@/utils/countOf"

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
    countOf(h.channels, "channel", "channels"),
    countOf(h.committees, "committee", "committees"),
    countOf(h.events, "event", "events"),
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
    cancel-testid="remove-game-cancel"
    danger
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
      <form-field
        v-slot="field"
        :label="`Type ${game.name} to remove it`"
      >
        <text-input
          v-model="typed"
          autocomplete="off"
          :control-id="field.controlId"
          testid="remove-game-name"
        />
      </form-field>
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
      <cut-button
        v-if="step === 'touches' && holdings && !held"
        testid="remove-game-next"
        tone="danger"
        @click="step = 'type-name'"
      >
        Remove
      </cut-button>
      <cut-button
        v-if="step === 'type-name'"
        :disabled="!matches || working"
        form="remove-game-form"
        submit
        testid="remove-game-confirm"
        tone="danger"
      >
        {{ working ? "Removing" : "Remove for good" }}
      </cut-button>
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

.remove-game__failure {
  margin: 0.8rem 0 0;
  font-size: 0.85rem;
  color: var(--color-danger);
}
</style>
