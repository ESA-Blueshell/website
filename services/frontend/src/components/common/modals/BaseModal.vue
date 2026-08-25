<script lang="ts" setup>
import {computed} from "vue"
import {useDisplay} from "vuetify"
import SubmitButton from "@/components/form/SubmitButton.vue"
import type {SubmitState} from "@/composables/formUtils"

defineOptions({name: "BaseModal"})

interface Props {
  modelValue: boolean
  title: string
  maxWidth?: string
  scrollable?: boolean
  /** Use the full screen on mobile (md and down) so the dialog isn't cramped. */
  fullscreenMobile?: boolean
  testid?: string
  showSave?: boolean
  saveLabel?: string
  saveColor?: string
  saveLoading?: boolean
  saveDisabled?: boolean
  saveTestid?: string
  // SubmitButton-specific passthrough — when saveIcon or saveShowStatus is
  // provided, the save action renders as a SubmitButton with progress feedback.
  saveIcon?: string | null
  saveSubmitState?: SubmitState
  saveShowStatus?: boolean
  saveVariant?: "elevated" | "flat" | "tonal" | "outlined" | "text" | "plain"
  showDelete?: boolean
  deleteLabel?: string
  deleteTestid?: string
  showCancel?: boolean
  cancelLabel?: string
  cancelTestid?: string
}

const props = withDefaults(defineProps<Props>(), {
  maxWidth: "760",
  scrollable: true,
  fullscreenMobile: false,
  testid: "base-modal",
  showSave: false,
  saveLabel: "Save",
  saveColor: "primary",
  saveLoading: false,
  saveDisabled: false,
  saveTestid: undefined,
  saveIcon: undefined,
  saveSubmitState: "idle",
  saveShowStatus: false,
  saveVariant: "elevated",
  showDelete: false,
  deleteLabel: "Delete",
  deleteTestid: undefined,
  showCancel: true,
  cancelLabel: "Cancel",
  cancelTestid: undefined,
})

defineSlots<{
  /** The modal body. Scrolls on its own unless `body-header` splits it. */
  default?: () => unknown
  /**
   * Rendered inline in the header band, beside the title. The title does not grow, so
   * appended content sits next to it; lead with a `<v-spacer />` to push it to the edge.
   */
  "title-append"?: () => unknown
  /** A region at the top of the body that stays put while only the body below it scrolls. */
  "body-header"?: () => unknown
  /** Replaces the entire footer, buttons and all. */
  actions?: () => unknown
  /** Secondary action(s) between Cancel and the primary action. */
  "actions-prepend"?: () => unknown
  /** Replaces just the primary action button. */
  save?: () => unknown
  /** Action(s) after the primary action. */
  "actions-append"?: () => unknown
}>()

const emit = defineEmits<{
  (e: "update:modelValue", value: boolean): void
  (e: "save"): void
  (e: "delete"): void
  (e: "cancel"): void
}>()

// useDisplay needs Vuetify's display injection, which plain unit mounts don't
// provide; guard so BaseModal stays mountable in tests without a Vuetify context.
const mdAndDown = (() => {
  try {
    return useDisplay().mdAndDown
  } catch {
    return null
  }
})()
const isFullscreen = computed(() => props.fullscreenMobile && Boolean(mdAndDown?.value))

const open = computed({
  get: () => props.modelValue,
  set: (val: boolean) => emit("update:modelValue", val),
})

function onCancel() {
  open.value = false
  emit("cancel")
}

/** When a caller supplies saveIcon or saveShowStatus, render SubmitButton for rich feedback. */
const useSaveAsSubmitButton = computed(
  () => props.saveIcon != null || props.saveShowStatus,
)
</script>

<template>
  <v-dialog
    v-model="open"
    :data-testid="testid"
    :fullscreen="isFullscreen"
    :max-width="maxWidth"
    :scrollable="scrollable"
  >
    <v-card>
      <v-card-title class="base-modal__title text-h5 d-flex align-center">
        <span class="base-modal__title-text">{{ title }}</span>
        <!--
          #title-append slot — extra affordances rendered inline in the header band,
          to the right of the title.
        -->
        <slot name="title-append" />
      </v-card-title>

      <v-card-text :class="{'base-modal__text--split': $slots['body-header']}">
        <!--
          #body-header — a region that stays put while the body scrolls beneath it. Without
          it the body scrolls whole, which is what most dialogs want.
        -->
        <template v-if="$slots['body-header']">
          <div class="base-modal__body-header">
            <slot name="body-header" />
          </div>
          <div class="base-modal__scroll-body">
            <slot />
          </div>
        </template>
        <slot v-else />
      </v-card-text>

      <v-card-actions class="base-modal__actions">
        <v-btn
          v-if="showDelete"
          :data-testid="deleteTestid"
          color="red"
          variant="tonal"
          @click="emit('delete')"
        >
          {{ deleteLabel }}
        </v-btn>

        <v-spacer />

        <!--
          #actions slot — full override of the entire footer contents.
          Use this when you need a completely custom set of action buttons.
        -->
        <slot name="actions">
          <v-btn
            v-if="showCancel"
            :data-testid="cancelTestid"
            @click="onCancel"
          >
            {{ cancelLabel }}
          </v-btn>

          <!--
            #actions-prepend — secondary action(s) between Cancel and the primary action,
            so a dialog can offer a lesser choice without displacing the main one.
          -->
          <slot name="actions-prepend" />

          <!--
            #save slot — override just the primary save button.
            Falls back to SubmitButton (rich) or plain v-btn based on props.
          -->
          <slot
            v-if="showSave"
            name="save"
          >
            <submit-button
              v-if="useSaveAsSubmitButton"
              :color="saveColor"
              :data-testid="saveTestid"
              :disabled="saveDisabled"
              :icon="saveIcon ?? null"
              :loading="saveLoading"
              :show-submit-status="saveShowStatus"
              :submit-state="saveSubmitState"
              :text="saveLabel"
              :variant="saveVariant"
              size="default"
              @click="emit('save')"
            />
            <v-btn
              v-else
              :color="saveColor"
              :data-testid="saveTestid"
              :disabled="saveDisabled"
              :loading="saveLoading"
              @click="emit('save')"
            >
              {{ saveLabel }}
            </v-btn>
          </slot>

          <slot name="actions-append" />
        </slot>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<style lang="scss" scoped>
// Distinct, comfortably padded header band shared by every BaseModal usage: a subtle
// surface tint plus a bottom divider separate the title, and anything appended beside it,
// from the body. Theme variables only, so it follows light and dark.
.base-modal__title {
  gap: 12px;
  padding: 16px 24px;
  background-color: rgba(var(--v-theme-on-surface), 0.04);
  border-bottom: thin solid rgba(var(--v-border-color), var(--v-border-opacity));
}

// Shrinks but does not grow, so appended content sits beside the title rather than being
// pushed to the far edge. A caller that wants it at the edge leads with a spacer.
.base-modal__title-text {
  flex: 0 1 auto;
  min-width: 0;
}

// The footer mirrors the header, so the primary actions read as their own band rather than
// as content that happens to be last.
.base-modal__actions {
  padding: 12px 24px;
  background-color: rgba(var(--v-theme-on-surface), 0.04);
  border-top: thin solid rgba(var(--v-border-color), var(--v-border-opacity));
}

// With a #body-header the body becomes a flex column: a fixed region on top and exactly one
// scrolling region below, so a form, a summary or a table's sticky column headers stay in
// view while the rows move.
.base-modal__text--split {
  display: flex;
  flex-direction: column;
  // Vuetify's `.v-dialog--scrollable ... > .v-card-text { overflow-y: auto }` is unlayered,
  // so it beats this scoped rule and turns the card text back into the scroller — which
  // collapses the split and unpins anything sticky. !important is what wins that.
  overflow: hidden !important;
}

.base-modal__body-header {
  flex: 0 0 auto;
  z-index: 2;
  background-color: rgb(var(--v-theme-surface));
  // Sit flush against the header band, then restore breathing room beneath.
  margin: -16px -24px 0;
  padding: 16px 24px 8px;
}

.base-modal__scroll-body {
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
  // Full body width; the side padding is restored inside.
  margin: 0 -24px -16px;
  padding: 0 24px;
}
</style>
