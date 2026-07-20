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

// The button is disabled when there is no included user selected or the required inputs
// (the date field) are missing.
const previewDisabled = computed(
  () => props.users.length === 0 || selectedUser.value == null || !props.inputsReady || props.loading,
)
</script>

<template>
  <div class="d-flex align-center bulk-email-preview">
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
      class="ml-3"
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
      <v-divider />
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
.bulk-email-preview {
  &__user {
    max-width: 320px;
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
