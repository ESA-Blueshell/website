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
          #title-append slot — render extra affordances (e.g. a help button)
          inline in the header band, to the right of the title.
        -->
        <slot name="title-append" />
      </v-card-title>

      <v-card-text :class="{'base-modal__text--split': $slots['body-header']}">
        <!--
          #body-header slot — a fixed (non-scrolling) region at the top of the body. When
          present, the body becomes a flex column: this header stays put while only the
          default slot below it scrolls, so form inputs, the counts summary and the table
          column headers stay visible while the member rows scroll.
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
            #actions-prepend slot — secondary action(s) rendered between Cancel and the
            primary Save action (e.g. a "Preview email" button next to Send).
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
// surface tint plus a bottom divider separate the title (and any #title-append
// affordance) from the body. Uses theme surface variables, no hardcoded colours.
.base-modal__title {
  gap: 8px;
  padding: 16px 24px;
  background-color: rgba(var(--v-theme-on-surface), 0.04);
  border-bottom: thin solid rgba(var(--v-border-color), var(--v-border-opacity));
}

.base-modal__title-text {
  flex: 1 1 auto;
  min-width: 0;
}

// Footer/actions band mirrors the header treatment: a subtle surface tint, a top divider
// and comfortable padding visually separate the primary actions from the body. Uses theme
// surface/border variables, no hardcoded colours.
.base-modal__actions {
  padding: 12px 24px;
  background-color: rgba(var(--v-theme-on-surface), 0.04);
  border-top: thin solid rgba(var(--v-border-color), var(--v-border-opacity));
}

// When a #body-header is present, make the body a flex column with a FIXED header region
// and a single scrollable region below it. The card-text itself does not scroll; only
// .base-modal__scroll-body does. This keeps the form/counts AND the table's (sticky) column
// headers pinned while the rows scroll, and clips the rows so they never bleed past the
// header or the modal edge.
.base-modal__text--split {
  display: flex;
  flex-direction: column;
  // Vuetify 4.1's VDialog.css sets `.v-dialog--scrollable > .v-overlay__content > .v-card >
  // .v-card-text { overflow-y: auto }` as an UNLAYERED rule, which otherwise wins over this
  // scoped rule and turns v-card-text back into the scroller — collapsing the split-body
  // chain (fixed header + single .base-modal__scroll-body) and unpinning the sticky thead.
  // !important is required to beat that unlayered Vuetify rule so the split layout engages.
  overflow: hidden !important;
}

// Fixed (non-scrolling) top region of the split body: form inputs + counts summary.
.base-modal__body-header {
  flex: 0 0 auto;
  z-index: 2;
  background-color: rgb(var(--v-theme-surface));
  // Cancel the v-card-text top padding so the region sits flush against the header band,
  // then restore breathing room beneath it.
  margin: -16px -24px 0;
  padding: 16px 24px 8px;
}

// The only scrolling region: the preview table (its thead is position: sticky, so the
// column headers stay pinned at the top of this region beneath the fixed form/counts).
.base-modal__scroll-body {
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
  // Use the full body width; cancel the v-card-text side padding for the rows region.
  margin: 0 -24px -16px;
  padding: 0 24px;
}
</style>
