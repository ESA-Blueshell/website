<script lang="ts" setup>
import {computed} from "vue"
import BaseModal from "@/components/common/modals/BaseModal.vue"
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
  <base-modal
    v-model="open"
    cancel-label="Close"
    max-width="900"
    save-testid="email-preview-send-btn"
    :save-disabled="!canConfirm"
    :save-label="confirmLabel ?? 'Send'"
    :save-loading="confirmLoading"
    :show-save="canConfirm"
    :title="title"
    testid="email-preview-dialog"
    @save="emit('confirm')"
  >
    <template
      v-if="preview?.subject"
      #title-append
    >
      <!-- Beside the title rather than under it: the two together are the header. -->
      <span class="email-preview__subject text-h6 text-medium-emphasis">
        <span class="email-preview__subject-sep">—</span>
        <span data-testid="email-preview-subject">{{ preview.subject }}</span>
      </span>
    </template>

    <!-- Changing the recipient re-renders, so the control belongs beside the email. -->
    <div
      v-if="$slots.recipient"
      class="mb-3"
    >
      <slot name="recipient" />
    </div>

    <v-alert
      v-if="error"
      class="mb-0"
      data-testid="email-preview-error"
      density="compact"
      type="error"
      variant="tonal"
    >
      {{ error }}
    </v-alert>

    <div
      v-else-if="loading"
      class="d-flex justify-center py-10"
      data-testid="email-preview-loading"
    >
      <v-progress-circular indeterminate />
    </div>

    <template v-else-if="preview">
      <div
        v-if="recipientLabel"
        class="email-preview__recipient text-body-2 text-medium-emphasis mb-3"
        data-testid="email-preview-recipient"
      >
        To: {{ recipientLabel }}
      </div>

      <v-alert
        v-if="preview.linkPlaceholder"
        class="mb-3"
        data-testid="email-preview-placeholder-notice"
        density="compact"
        type="info"
        variant="tonal"
      >
        The links in this preview do not work. A real one is created only when the email is
        actually sent.
      </v-alert>

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
  </base-modal>
</template>

<style lang="scss" scoped>
// Borderless, dark: matches the email's own canvas so the dialog reads as one pane
// rather than a frame inside a frame.
// Subject sits beside the title and may be long, so it truncates rather than pushing the
// send button off the band.
.email-preview__subject {
  display: flex;
  align-items: baseline;
  gap: 8px;
  min-width: 0;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.email-preview__subject-sep {
  opacity: 0.5;
}

.email-preview__recipient {
  font-size: 0.95rem;
}

.email-preview__frame {
  width: 100%;
  min-height: 60vh;
  border: 0;
  display: block;
  background: #1e1e1e;
}
</style>
