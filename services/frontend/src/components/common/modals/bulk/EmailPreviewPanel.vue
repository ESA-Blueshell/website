<script lang="ts" setup>
import {computed} from "vue"

/**
 * Nested email-preview affordance for the reminder / incasso bulk dialogs. Lives in the
 * fixed header area: a user SELECT (the currently-included users) plus a "Preview email"
 * button that renders the actual email the server would send, isolated inside a sandboxed
 * <iframe :srcdoc>. Rendering server-produced HTML in a sandboxed iframe keeps it out of
 * the app DOM (no v-html of untrusted-looking markup). The parent owns the fetch; this
 * component is presentational and emits a "preview" request.
 */

defineOptions({name: "EmailPreviewPanel"})

interface UserOption {
  value: number
  title: string
}

interface Props {
  /** Selectable users (currently-included set), value = userId. */
  users: UserOption[]
  /** The currently-selected user id. */
  modelValue: number | null
  /** Whether required inputs (e.g. the date) are present so a preview can be requested. */
  inputsReady: boolean
  loading?: boolean
  error?: string | null
  /** Rendered email, once fetched. */
  subject?: string | null
  html?: string | null
  /** Controls the nested preview dialog visibility. */
  dialogOpen: boolean
}

const props = withDefaults(defineProps<Props>(), {
  loading: false,
  error: null,
  subject: null,
  html: null,
})

const emit = defineEmits<{
  (e: "update:modelValue", value: number | null): void
  (e: "update:dialogOpen", value: boolean): void
  (e: "preview"): void
}>()

const selectedUser = computed({
  get: () => props.modelValue,
  set: (v) => emit("update:modelValue", v),
})

const dialog = computed({
  get: () => props.dialogOpen,
  set: (v) => emit("update:dialogOpen", v),
})

// The Preview button stays clickable even when the form/dates are invalid: clicking it
// then triggers the normal validation display (offending fields go red with inline
// messages) via the dialog's validate-on-click wiring, and the request is aborted when
// invalid. It is only disabled while a preview request is in flight, or when there are no
// possible recipients to preview at all.
const previewDisabled = computed(() => props.loading || props.users.length === 0)
</script>

<template>
  <div class="bulk-email-preview-container mb-4">
    <div class="text-overline bulk-email-preview-container__label">Preview email</div>
    <div class="d-flex align-center gap-3 bulk-email-preview">
      <v-select
        v-model="selectedUser"
        class="bulk-email-preview__user"
        data-testid="bulk-email-preview-user-select"
        density="comfortable"
        :disabled="users.length === 0"
        hide-details
        item-title="title"
        item-value="value"
        :items="users"
        label="Preview recipient"
        prepend-inner-icon="mdi-account"
      />
      <v-btn
        data-testid="bulk-email-preview-btn"
        :disabled="previewDisabled"
        :loading="loading"
        prepend-icon="mdi-email-search-outline"
        variant="tonal"
        @click="emit('preview')"
      >
        Preview email
      </v-btn>
    </div>
  </div>

  <v-dialog
    v-model="dialog"
    data-testid="bulk-email-preview-dialog"
    max-width="820"
    scrollable
  >
    <v-card>
      <v-card-title class="d-flex align-center">
        <span>Email preview</span>
        <v-spacer />
        <v-btn
          aria-label="Close preview"
          icon="mdi-close"
          size="small"
          variant="text"
          @click="dialog = false"
        />
      </v-card-title>
      <v-card-text>
        <v-alert
          v-if="error"
          data-testid="bulk-email-preview-error"
          density="comfortable"
          type="error"
          variant="tonal"
        >
          {{ error }}
        </v-alert>
        <div
          v-else-if="loading"
          class="d-flex justify-center py-8"
          data-testid="bulk-email-preview-loading"
        >
          <v-progress-circular indeterminate />
        </div>
        <template v-else>
          <div class="mb-2">
            <span class="text-caption text-medium-emphasis">Subject</span>
            <div
              class="font-weight-medium"
              data-testid="bulk-email-preview-subject"
            >{{ subject }}</div>
          </div>
          <!--
            Render the server-produced email HTML inside a sandboxed iframe via srcdoc so
            it is fully isolated from the app DOM: no script execution, no same-origin
            access, and no styles/markup bleeding into (or reading from) the surrounding
            page. This is the safe alternative to v-html for server-rendered email bodies.
          -->
          <iframe
            v-if="html"
            class="bulk-email-preview__frame"
            data-testid="bulk-email-preview-frame"
            sandbox=""
            :srcdoc="html"
            title="Email preview"
          />
        </template>
      </v-card-text>
    </v-card>
  </v-dialog>
</template>

<style lang="scss" scoped>
// Container for the preview email section: a subtle boxed area with a tonal
// background and a label, separate from the date filter row above it. Matches the
// app's existing subtle surface-tint pattern (rgba border + minimal background)
// rather than heavy dividers that were removed for being over-styled.
.bulk-email-preview-container {
  border-radius: 4px;
  padding: 12px;
  background: rgba(var(--v-theme-on-surface), 0.04);
  border: 1px solid rgba(var(--v-theme-on-surface), 0.12);

  &__label {
    display: block;
    margin-bottom: 8px;
    color: rgba(var(--v-theme-on-surface), 0.7);
  }
}

.bulk-email-preview {
  // Keep the recipient select and the Preview button centred on the same horizontal
  // axis. The comfortable-density select is taller than the button, so without an
  // explicit centre the button drifts off-axis; align-items:center on the flex row plus
  // resetting any stray vertical margins keeps them aligned regardless of the select's
  // hide-details height.
  align-items: center;

  &__user {
    max-width: 320px;
    flex-grow: 1;
  }

  .v-btn {
    align-self: center;
    margin-top: 0;
    margin-bottom: 0;
  }
}

.bulk-email-preview__frame {
  width: 100%;
  min-height: 60vh;
  border: 1px solid rgba(var(--v-theme-on-surface), 0.12);
  border-radius: 4px;
  background: #fff;
}
</style>
