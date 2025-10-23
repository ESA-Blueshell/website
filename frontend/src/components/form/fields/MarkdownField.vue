<template>
  <div class="md-editor">
    <div class="md-field">
      <div
        aria-label="Markdown mode"
        class="md-tabs"
        role="tablist"
      >
        <button
          :aria-selected="tab === 'write'"
          :class="{ active: tab === 'write' }"
          aria-controls="md-write"
          class="md-tab"
          role="tab"
          type="button"
          @click="tab = 'write'"
        >
          Write
        </button>
        <button
          :aria-selected="tab === 'preview'"
          :class="{ active: tab === 'preview' }"
          aria-controls="md-preview"
          class="md-tab"
          role="tab"
          type="button"
          @click="tab = 'preview'"
        >
          Preview
        </button>
      </div>

      <div
        v-if="tab === 'preview'"
        class="md-fake-label"
      >
        {{ label }}
      </div>

      <v-textarea
        v-if="tab === 'write'"
        id="md-write"
        v-model="value"
        :auto-grow="autoGrow"
        :density="density"
        :disabled="disabled"
        :error-messages="errorMessages as any"
        :hide-details="hideDetails"
        :label="label"
        :placeholder="placeholder"
        :readonly="readonly"
        :rows="rows"
        :variant="variant"
        class="md-textarea"
        v-bind="$attrs"
        @blur="emit('blur')"
        @focus="emit('focus')"
      />

      <div
        v-else
        id="md-preview"
        :aria-live="previewAriaLive"
        :style="{ minHeight: `calc(${rows} * 1.5em + 24px)` }"
        class="md-preview"
      >
        <div
          v-if="!modelValue || !modelValue.trim().length"
          class="text-medium-emphasis"
        >
          Nothing to preview.
        </div>
        <div
          v-else
          class="markdown-body"
          v-html="previewHtml"
        />
      </div>
    </div>

    <div
      v-if="(errorMessages as any)?.length"
      class="text-error text-caption mt-1"
    >
      {{ (errorMessages as any).join ? (errorMessages as any).join(", ") : (errorMessages as any) }}
    </div>
  </div>
</template>

<script lang="ts" setup>
import {computed, ref} from "vue"
import $markdownToHtml from "@/plugins/markdownToHtml.ts"

defineOptions({inheritAttrs: false})

const props = defineProps({
  modelValue: {type: String, default: ""},
  label: {type: String, default: "Description (Markdown)"},
  placeholder: {type: String, default: "Write using **Markdown**. Use :sparkles: for emoji."},
  errorMessages: {type: [Array, String], default: () => []},
  rows: {type: Number, default: 6},
  autoGrow: {type: Boolean, default: true},
  variant: {type: String, default: "outlined"},
  density: {type: String, default: undefined},
  hideDetails: {type: [Boolean, String], default: "auto"},
  disabled: {type: Boolean, default: false},
  readonly: {type: Boolean, default: false},
  initialTab: {type: String as () => "write" | "preview", default: "write"},
  previewAriaLive: {type: String, default: "polite"},
})

const emit = defineEmits<{ (e: "update:modelValue", v: string): void; (e: "blur"): void; (e: "focus"): void }>()
const value = computed({get: () => props.modelValue, set: (v: string) => emit("update:modelValue", v)})
const tab = ref<"write" | "preview">(props.initialTab as "write" | "preview")
const previewHtml = computed(() => (props.modelValue?.trim() ? $markdownToHtml(props.modelValue) : ""))
</script>

<style lang="scss" scoped>
.md-editor {
  .md-field {
    position: relative;
  }

  .md-textarea, .md-preview {
    :deep(.v-field), :deep(.v-input), :deep(.v-textarea) {
      border-radius: 8px;
    }
  }

  .md-preview {
    border: 1px solid rgba(var(--v-theme-on-surface), 0.12);
    border-radius: 8px;
    background: rgb(var(--v-theme-surface));
    padding: 12px 14px;
    line-height: 1.5;
    overflow: auto;
  }

  .md-fake-label {
    position: absolute;
    top: -8px;
    left: 12px;
    padding: 0 6px;
    font-size: 0.75rem;
    line-height: 1;
    color: rgba(var(--v-theme-on-surface), 0.6);
    background: rgb(var(--v-theme-surface));
    z-index: 2;
    pointer-events: none;
  }

  .md-tabs {
    position: absolute;
    top: 6px;
    right: 8px;
    display: flex;
    gap: 10px;
    z-index: 3;
  }

  .md-tab {
    appearance: none;
    background: transparent;
    border: 0;
    padding: 4px 2px 6px;
    font-size: 1.2rem;
    line-height: 1;
    color: rgba(var(--v-theme-on-surface), 0.6);
    border-bottom: 2px solid transparent;
    cursor: pointer;
  }

  .md-tab:hover {
    color: rgba(var(--v-theme-on-surface), 0.8);
  }

  .md-tab.active {
    color: rgba(var(--v-theme-on-surface), 0.95);
    border-bottom-color: rgba(var(--v-theme-on-surface), 0.45);
  }

  :deep(.v-field--variant-outlined .v-field__field) {
    padding-top: 4px;
  }

  :deep(.v-field) {
    overflow: visible;
  }

  :deep(.v-field--focused .v-field__outline) {
    box-shadow: 0 0 0 2px rgba(var(--v-theme-on-surface), 0.08) inset;
  }
}
</style>
