<script setup lang="ts">
import ModalDialog from "@/components/island/ModalDialog.vue"
import {AnnounceChoice} from "@/services/api"

/** The board's choice of when an approved event's events-info post goes out. Nothing is preselected. */
defineOptions({name: "AnnounceDialog"})

defineProps<{
  open: boolean
  /** The later choice, already said in the viewer's own time. */
  later: string
}>()

const emit = defineEmits<{(event: "answer", choice: AnnounceChoice | null): void}>()
</script>

<template>
  <modal-dialog
    :open="open"
    testid="announce-dialog"
    title="When does the announcement go out?"
    @update:open="!$event && emit('answer', null)"
  >
    <div class="announce">
      <p class="announce__question">
        Approving posts the event in #events-info, pinging its roles.
      </p>
      <div class="announce__actions">
        <button
          class="announce__button announce__button--ghost"
          data-testid="announce-cancel"
          type="button"
          @click="emit('answer', null)"
        >
          Cancel
        </button>
        <button
          class="announce__button"
          data-testid="announce-later"
          type="button"
          @click="emit('answer', AnnounceChoice.NEXT_MORNING)"
        >
          {{ later }}
        </button>
        <button
          class="announce__button"
          data-testid="announce-now"
          type="button"
          @click="emit('answer', AnnounceChoice.NOW)"
        >
          Post now
        </button>
      </div>
    </div>
  </modal-dialog>
</template>

<style>
/* Unscoped, as ConfirmDialog's: the dialog is portalled out of this component's subtree. */
.announce {
  display: flex;
  flex-direction: column;
  gap: 0.85rem;
}

.announce__question {
  margin: 0;
  color: var(--color-chalk);
  font-size: 0.95rem;
  line-height: 1.45;
}

.announce__actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 0.6rem;
}

.announce__button {
  padding: 0.45rem 1.1rem;
  border: 0;
  clip-path: polygon(10px 0, 100% 0, calc(100% - 10px) 100%, 0 100%);
  font-family: "Shellhouse One", system-ui, sans-serif;
  font-size: 0.8rem;
  font-style: italic;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  cursor: pointer;
  background: var(--color-brand);
  color: var(--color-void);
}

.announce__button:hover,
.announce__button:focus-visible {
  background: var(--color-brand-lit);
}

.announce__button--ghost,
.announce__button--ghost:hover,
.announce__button--ghost:focus-visible {
  background: var(--color-raised);
  color: var(--color-ash);
}
</style>
