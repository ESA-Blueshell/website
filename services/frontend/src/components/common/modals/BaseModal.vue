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
      <v-card-title class="text-h5">
        {{ title }}
      </v-card-title>

      <v-card-text>
        <slot />
      </v-card-text>

      <v-card-actions>
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
