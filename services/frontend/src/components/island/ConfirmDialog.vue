<script lang="ts" setup>
import IslandDialog from "./IslandDialog.vue"

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
  <island-dialog
    :accent="accent"
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

      <div class="confirm__actions">
        <button
          class="confirm__button confirm__button--ghost"
          data-testid="confirm-cancel"
          type="button"
          @click="emit('update:open', false)"
        >
          Keep it
        </button>
        <button
          class="confirm__button confirm__button--go"
          data-testid="confirm-go"
          :disabled="working"
          type="button"
          @click="emit('confirm')"
        >
          {{ working ? workingLabel : confirmLabel }}
        </button>
      </div>
    </div>
  </island-dialog>
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

.confirm__actions {
  display: flex;
  justify-content: flex-end;
  gap: 0.6rem;
}

.confirm__button {
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

.confirm__button--ghost {
  background: var(--color-raised);
  color: var(--color-ash);
}

/* The fill does not follow the theme, so its ink must not either. */
.confirm__button--go {
  background: var(--color-danger-fill);
  color: var(--color-danger-on-fill);
}

.confirm__button:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}
</style>
