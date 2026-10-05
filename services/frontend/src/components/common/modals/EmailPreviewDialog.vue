<script lang="ts" setup>
import {computed} from "vue"
import CutButton from "@/components/island/CutButton.vue"
import ModalDialog from "@/components/island/ModalDialog.vue"
import NoticeBox from "@/components/island/NoticeBox.vue"
import type {RenderedEmailPreview} from "@/composables/useEmailPreview"

defineOptions({name: "EmailPreviewDialog"})

/**
 * Shows a prepared email, and optionally lets the reader send it.
 *
 * Presentational only: the caller owns the fetch, the loading and error state, and the
 * sending. Give it a `confirmLabel` and it becomes the confirmation step for that email —
 * what is read is exactly what goes out, so there is nothing left to confirm afterwards.
 * Leave it off and the dialog is read-only.
 */
const props = withDefaults(defineProps<{
  modelValue: boolean
  title?: string
  preview?: RenderedEmailPreview | null
  loading?: boolean
  error?: string | null
  /** Set to offer sending this email from the dialog. Omitted, the dialog only shows it. */
  confirmLabel?: string | null
  confirmLoading?: boolean
}>(), {
  title: "Email preview",
  preview: null,
  loading: false,
  error: null,
  confirmLabel: null,
  confirmLoading: false,
})

const emit = defineEmits<{
  (e: "update:modelValue", value: boolean): void
  (e: "confirm"): void
}>()

// Nothing to send until there is a rendered email to have read.
const canConfirm = computed(() => props.confirmLabel != null && props.preview != null && !props.error)

defineSlots<{
  /** Controls that change what is previewed, e.g. which recipient to render for. */
  recipient?: () => unknown
}>()

const open = computed({
  get: () => props.modelValue,
  set: (v) => emit("update:modelValue", v),
})

const recipientLabel = computed(() => {
  const name = props.preview?.recipientName
  const email = props.preview?.recipientEmail
  if (!name && !email) return null
  return name ? `${name} <${email}>` : email
})
</script>

<template>
  <modal-dialog
    :cancel="false"
    :open="open"
    testid="email-preview-dialog"
    :title="title"
    wide
    @update:open="open = $event"
  >
    <div class="email-preview">
      <p
        v-if="preview?.subject"
        class="email-preview__subject"
        data-testid="email-preview-subject"
      >
        {{ preview.subject }}
      </p>

      <!-- Changing the recipient re-renders, so the control belongs beside the email. -->
      <div v-if="$slots.recipient">
        <slot name="recipient" />
      </div>

      <notice-box
        v-if="error"
        testid="email-preview-error"
        tone="danger"
      >
        {{ error }}
      </notice-box>

      <p
        v-else-if="loading"
        class="email-preview__note"
        data-testid="email-preview-loading"
        role="status"
      >
        Loading the email.
      </p>

      <template v-else-if="preview">
        <p
          v-if="recipientLabel"
          class="email-preview__note"
          data-testid="email-preview-recipient"
        >
          To: {{ recipientLabel }}
        </p>

        <notice-box
          v-if="preview.linkPlaceholder"
          testid="email-preview-placeholder-notice"
        >
          The links in this preview do not work. A real one is created only when the email is
          actually sent.
        </notice-box>

        <!-- A sent email's links are live credentials, so they never reach this dialog. -->
        <notice-box
          v-else-if="preview.linksRedacted"
          testid="email-preview-redacted-notice"
        >
          The links are removed from this preview. They are one-time credentials belonging to
          the recipient, so following one from here would spend it.
        </notice-box>

        <!--
          Sandboxed srcdoc rather than v-html: the email's own styles cannot reach the app,
          nothing in it executes, and it gets no same-origin access.
        -->
        <iframe
          class="email-preview__frame"
          data-testid="email-preview-frame"
          sandbox=""
          :srcdoc="preview.html"
          title="Email preview"
        />
      </template>
    </div>

    <template #footer>
      <cut-button
        testid="email-preview-close"
        tone="quiet"
        @click="open = false"
      >
        Close
      </cut-button>
      <cut-button
        v-if="canConfirm"
        :disabled="confirmLoading"
        testid="email-preview-send-btn"
        tone="solid"
        @click="emit('confirm')"
      >
        {{ confirmLoading ? "Sending" : confirmLabel }}
      </cut-button>
    </template>
  </modal-dialog>
</template>

<style scoped>
.email-preview {
  display: flex;
  flex-direction: column;
  gap: 0.8rem;
}

.email-preview__subject {
  font-weight: 600;
  overflow-wrap: anywhere;
}

.email-preview__note {
  font-size: 0.9rem;
  color: var(--color-ash);
}

/* Borderless and dark, the email's own canvas, so the dialog reads as one pane. */
.email-preview__frame {
  display: block;
  width: 100%;
  min-height: 60vh;
  border: 0;
  background-color: var(--color-pit);
}
</style>
