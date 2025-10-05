<script lang="ts" setup>
import {computed, ref} from "vue"
import $markdownToHtml from "@/plugins/markdownToHtml" // marked + node-emoji + DOMPurify

defineOptions({inheritAttrs: false})

const props = defineProps({
  modelValue: {type: String, default: ""},
  label: {type: String, default: "Description (Markdown)"},
  placeholder: {
    type: String,
    default: "Write using **Markdown**. Use :sparkles: for emoji.",
  },
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

const emit = defineEmits<{
  (e: "update:modelValue", v: string): void
  (e: "blur"): void
  (e: "focus"): void
}>()

const value = computed({
  get: () => props.modelValue,
  set: (v: string) => emit("update:modelValue", v),
})

const tab = ref<"write" | "preview">(props.initialTab as "write" | "preview")

const previewHtml = computed(() =>
  props.modelValue?.trim() ? $markdownToHtml(props.modelValue) : "",
)
</script>

<template>
  <div class="md-editor">
    <div class="mb-3">
      <v-tabs
        v-model="tab"
        density="compact"
        selected-class="tab--active"
        class="d-flex ga-4"
        hide-slider
      >
        <v-tab
          value="write"
          prepend-icon="mdi-pencil-outline"
          class="rounded"
        >
          Write
        </v-tab>
        <v-tab
          value="preview"
          class="rounded"
          prepend-icon="mdi-eye-outline"
        >
          Preview
        </v-tab>
      </v-tabs>
    </div>

    <v-window v-model="tab">
      <v-window-item value="write">
        <v-textarea
          v-model="value"
          :label="label"
          :placeholder="placeholder"
          :variant="variant"
          :density="density"
          :rows="rows"
          :auto-grow="autoGrow"
          :disabled="disabled"
          :readonly="readonly"
          :error-messages="errorMessages as any"
          :hide-details="hideDetails"
          v-bind="$attrs"
          @blur="emit('blur')"
          @focus="emit('focus')"
        />
      </v-window-item>

      <v-window-item value="preview">
        <v-card variant="outlined">
          <v-card-text>
            <div
              v-if="!modelValue || !modelValue.trim().length"
              class="text-medium-emphasis"
            >
              Nothing to preview.
            </div>

            <div
              v-else
              class="markdown-body"
              :aria-live="previewAriaLive"
              v-html="previewHtml"
            />
          </v-card-text>
        </v-card>
      </v-window-item>
    </v-window>
    <div
      v-if="(errorMessages as any)?.length"
      class="text-error text-caption mt-1"
    >
      {{ (errorMessages as any).join ? (errorMessages as any).join(", ") : (errorMessages as any) }}
    </div>
  </div>
</template>
<style scoped lang="scss">
.md-editor {
  :deep(.v-tabs),
  :deep(.v-tab),
  :deep(.v-window),
  :deep(.v-card),
  :deep(.v-textarea),
  :deep(.v-field) {
    overflow: visible;
  }

  .tab--active {
    background: rgb(var(--v-theme-primary));
    border-radius: 8px;
    box-shadow: 0 1px 2px rgba(0, 0, 0, .12);
  }

  :deep(.v-field--variant-outlined .v-field__field) {
    padding-top: 4px;
  }
}

</style>
