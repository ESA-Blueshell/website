<script lang="ts" setup>
import {useTemplateRef} from "vue"
import {DialogClose, DialogContent, DialogOverlay, DialogPortal, DialogRoot, DialogTitle} from "reka-ui"
import CutButton from "./CutButton.vue"
import {focusFirstField} from "@/utils/firstField"
import {useMotionAllowed} from "./useMotionAllowed"

/**
 * A dialog that belongs to the island rather than to the rest of the site.
 *
 * Reka supplies only behaviour — the focus trap, the return of focus to whatever opened it,
 * Escape, and the aria wiring — so none of Vuetify's chrome comes with it and every visible
 * rule here is the island's own. The portal puts it at the end of the body, which is why the
 * island's variables are restated on the content rather than inherited.
 */
defineOptions({name: "ModalDialog"})

const {cancel = true, testid = undefined, accent = undefined, cancelTestid = undefined} = defineProps<{
  open: boolean
  title: string
  testid?: string
  /** The game's colour, so the dialog belongs to the page it was opened from. */
  accent?: string
  wide?: boolean
  danger?: boolean
  cancel?: boolean
  cancelTestid?: string
}>()

const emit = defineEmits<{(event: "update:open", open: boolean): void}>()

const {decorative} = useMotionAllowed()

/** A dialog with a form opens on its first field; one without keeps Reka's focus. */
const body = useTemplateRef<HTMLElement>("body")
const toFirstField = (event: Event) => {
  if (body.value && focusFirstField(body.value)) event.preventDefault()
}
</script>

<template>
  <dialog-root
    :open="open"
    @update:open="emit('update:open', $event)"
  >
    <dialog-portal>
      <dialog-overlay
        class="island-dialog__scrim"
        :class="{'island-dialog__scrim--still': !decorative}"
      />
      <dialog-content
        class="island island-dialog"
        :class="{'island-dialog--still': !decorative, 'island-dialog--wide': wide, 'island-dialog--danger': danger}"
        :data-testid="testid ?? 'island-dialog'"
        :style="accent ? {'--dialog-accent': accent} : undefined"
        @open-auto-focus="toFirstField"
      >
        <div class="island-dialog__head">
          <dialog-title class="island-dialog__title">
            {{ title }}
          </dialog-title>
          <dialog-close
            aria-label="Close"
            class="island-dialog__close"
            data-testid="island-dialog-close"
          >
            <span aria-hidden="true">&times;</span>
          </dialog-close>
        </div>

        <div
          ref="body"
          class="island-dialog__body"
        >
          <slot />
        </div>

        <!--
          Pinned under the form rather than scrolling with it, so the way out of a dialog is
          where it was the last time you looked. The bar draws its own rule and its own
          spacing, because a footer that drew them would draw them around nothing on the
          dialogs that have no buttons to put here yet.
        -->
        <div class="island-dialog__foot">
          <div
            v-if="$slots.footer"
            class="island-dialog__acts"
          >
            <cut-button
              v-if="cancel"
              :testid="cancelTestid ?? `${testid ?? 'island-dialog'}-cancel`"
              tone="quiet"
              @click="emit('update:open', false)"
            >
              Cancel
            </cut-button>
            <slot name="footer" />
          </div>
        </div>
      </dialog-content>
    </dialog-portal>
  </dialog-root>
</template>

<style>
/*
 * Unscoped on purpose: the portal moves this out of the component's subtree, and the form
 * inside it is slotted from a parent, so a scoped rule would reach neither.
 */
.island-dialog__scrim {
  position: fixed;
  inset: 0;
  z-index: 2400;
  background: rgb(0 0 0 / 62%);
  animation: island-dialog-in 160ms cubic-bezier(0.22, 1, 0.36, 1);
}

.island-dialog {
  position: fixed;
  top: 50%;
  left: 50%;
  z-index: 2401;
  width: min(30rem, calc(100vw - 2rem));
  max-height: calc(100vh - 2rem);
  /* The island's root fills its container; a fixed box's container is the window, so without
     this the dialog stands the full height of the screen. */
  min-height: 0;
  height: fit-content;
  /* The head and the foot hold their ground and the form between them takes the scrolling, so
     the root itself never scrolls. */
  display: flex;
  flex-direction: column;
  overflow: hidden;
  transform: translate(-50%, -50%);
  padding: 1.25rem;
  background: var(--color-surface);
  border-top: 3px solid var(--dialog-accent, var(--color-brand));
  box-shadow: 0 24px 60px rgb(0 0 0 / 55%);
  color: var(--color-chalk);
  font-family: "Barlow Semi Condensed", system-ui, sans-serif;
  animation: island-dialog-rise 200ms cubic-bezier(0.22, 1, 0.36, 1);
}

.island-dialog--wide {
  width: min(56rem, calc(100vw - 2rem));
}

.island-dialog__scrim--still,
.island-dialog--still {
  animation: none;
}

.island-dialog__body {
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
}

.island-dialog__foot {
  flex: 0 0 auto;
}

.island-dialog__acts {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  align-items: center;
  gap: 0.5rem;
  margin-top: 1.25rem;
}

.island-dialog--danger {
  --dialog-accent: var(--color-danger);
}

.island-dialog--danger .island-dialog__title {
  color: var(--color-danger);
}

.island-dialog__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 1rem;
  margin-bottom: 1rem;
}

.island-dialog__title {
  margin: 0;
  font-family: "Shellhouse One", system-ui, sans-serif;
  font-size: 1.15rem;
  font-style: italic;
  letter-spacing: 0.02em;
  text-transform: uppercase;
}

.island-dialog__close {
  background: none;
  border: 0;
  padding: 0 0.25rem;
  color: var(--color-ash);
  font-size: 1.5rem;
  line-height: 1;
  cursor: pointer;
}

.island-dialog__close:hover,
.island-dialog__close:focus-visible {
  color: var(--color-chalk);
}

@keyframes island-dialog-in {
  from { opacity: 0; }
  to { opacity: 1; }
}

@keyframes island-dialog-rise {
  from { opacity: 0; transform: translate(-50%, calc(-50% + 8px)); }
  to { opacity: 1; transform: translate(-50%, -50%); }
}
</style>
