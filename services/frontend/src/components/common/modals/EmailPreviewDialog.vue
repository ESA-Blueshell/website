<script lang="ts" setup>
import {computed} from "vue"
import BaseModal from "@/components/common/modals/BaseModal.vue"
import type {RenderedEmailPreview} from "@/composables/useEmailPreview"

defineOptions({name: "EmailPreviewDialog"})

/**
 * Shows a prepared email. Presentational only: the caller owns the fetch and the loading
 * and error state, so any flow that can render an email can reuse this shell.
 */
const props = withDefaults(defineProps<{
  modelValue: boolean
  title?: string
  preview?: RenderedEmailPreview | null
  loading?: boolean
  error?: string | null
}>(), {
  title: "Email preview",
  preview: null,
  loading: false,
  error: null,
})

const emit = defineEmits<{
  (e: "update:modelValue", value: boolean): void
}>()

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
    :title="title"
    testid="email-preview-dialog"
  >
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
      <div class="mb-3">
        <div
          v-if="recipientLabel"
          class="text-body-2 text-medium-emphasis"
          data-testid="email-preview-recipient"
        >
          To: {{ recipientLabel }}
        </div>
        <div
          class="text-subtitle-1 font-weight-medium"
          data-testid="email-preview-subject"
        >
          {{ preview.subject }}
        </div>
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
.email-preview__frame {
  width: 100%;
  min-height: 60vh;
  border: 0;
  display: block;
  background: #1e1e1e;
}
</style>
