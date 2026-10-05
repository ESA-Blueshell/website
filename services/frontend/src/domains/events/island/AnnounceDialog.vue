<script setup lang="ts">
import CutButton from "@/components/island/CutButton.vue"
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
    cancel-testid="announce-cancel"
    :open="open"
    testid="announce-dialog"
    title="When does the announcement go out?"
    @update:open="!$event && emit('answer', null)"
  >
    <p class="announce__question">
      Approving posts the event in #events-info, pinging its roles.
    </p>
    <template #footer>
      <cut-button
        testid="announce-later"
        @click="emit('answer', AnnounceChoice.NEXT_MORNING)"
      >
        {{ later }}
      </cut-button>
      <cut-button
        testid="announce-now"
        tone="solid"
        @click="emit('answer', AnnounceChoice.NOW)"
      >
        Post now
      </cut-button>
    </template>
  </modal-dialog>
</template>

<style>
/* Unscoped, as ConfirmDialog's: the dialog is portalled out of this component's subtree. */
.announce__question {
  margin: 0;
  color: var(--color-chalk);
  font-size: 0.95rem;
  line-height: 1.45;
}
</style>
