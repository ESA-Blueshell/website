<script lang="ts" setup>
import CutButton from "./CutButton.vue"
import ModalDialog from "./ModalDialog.vue"

/**
 * Asking before something is taken away.
 *
 * The question names what goes, so it can be answered without remembering what was clicked.
 * A refusal is reported here rather than closing on a removal that did not happen.
 */
defineOptions({name: "ConfirmDialog"})

withDefaults(defineProps<{
  open: boolean
  title: string
  /** What will go, said plainly enough to decide on. */
  question: string
  confirmLabel?: string
  /** Said while it runs, because "Removing" is wrong over a button that says Delete. */
  workingLabel?: string
  failure?: string | null
  working?: boolean
  accent?: string
  testid?: string
}>(), {
  confirmLabel: "Remove",
  workingLabel: "Removing",
  failure: null,
  working: false,
  accent: undefined,
  testid: "confirm-dialog",
})

const emit = defineEmits<{
  (event: "update:open", open: boolean): void
  (event: "confirm"): void
}>()
</script>

<template>
  <modal-dialog
    :accent="accent"
    cancel-testid="confirm-cancel"
    danger
    :open="open"
    :testid="testid"
    :title="title"
    @update:open="emit('update:open', $event)"
  >
    <div class="confirm">
      <p
        class="confirm__question"
        data-testid="confirm-question"
      >
        {{ question }}
      </p>

      <p
        v-if="failure"
        class="confirm__failure"
        data-testid="confirm-failure"
        role="alert"
      >
        {{ failure }}
      </p>
    </div>
    <template #footer>
      <cut-button
        :disabled="working"
        testid="confirm-go"
        tone="danger"
        @click="emit('confirm')"
      >
        {{ working ? workingLabel : confirmLabel }}
      </cut-button>
    </template>
  </modal-dialog>
</template>

<style>
/* Unscoped: the dialog is portalled out of this component's subtree. */
.confirm {
  display: flex;
  flex-direction: column;
  gap: 0.85rem;
}

.confirm__question {
  margin: 0;
  color: var(--color-chalk);
  font-size: 0.95rem;
  line-height: 1.45;
}

.confirm__failure {
  margin: 0;
  color: var(--color-danger);
  font-size: 0.85rem;
}

</style>
