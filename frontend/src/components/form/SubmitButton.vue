<script lang="ts" setup>
import {computed, onBeforeUnmount, ref, watch} from "vue"
import type {SubmitState} from "@/composables/formUtils"

const props = withDefaults(
  defineProps<{
    text?: string
    icon?: string | null
    loading?: boolean
    disabled?: boolean
    block?: boolean
    color?: string
    variant?: "elevated" | "flat" | "tonal" | "outlined" | "text" | "plain"
    size?: "x-small" | "small" | "default" | "large" | "x-large"
    type?: "button" | "submit" | "reset"
    submitState?: SubmitState
    showSubmitStatus?: boolean
    useStatusIcon?: boolean
  }>(),
  {
    text: "Submit",
    icon: null,
    loading: false,
    disabled: false,
    block: false,
    color: "primary",
    variant: "elevated",
    size: "large",
    type: "button",
    submitState: "idle",
    showSubmitStatus: false,
    useStatusIcon: true,
  },
)

const emit = defineEmits<{
  (e: "click"): void
}>()

const showContentIcon = computed(() => !!props.icon)

const statusIcon = computed(() => {
  if (props.submitState === "success") return "mdi-check-circle"
  if (props.submitState === "error") return "mdi-close-circle"
  return null
})

const statusColorClass = computed(() => {
  if (props.submitState === "success") return "submit-btn__status-overlay--success"
  if (props.submitState === "error") return "submit-btn__status-overlay--error"
  return null
})

const isStatusVisible = ref(false)
let hideTimeout: number | null = null

const hasStatus = computed(
  () => !!statusIcon.value && isStatusVisible.value && props.useStatusIcon,
)

function scheduleHide() {
  if (hideTimeout != null) window.clearTimeout(hideTimeout)

  hideTimeout = window.setTimeout(() => {
    isStatusVisible.value = false
  }, 3200)
}

watch(
  () => ({
    state: props.submitState,
    visible: props.showSubmitStatus,
  }),
  ({state, visible}) => {
    if (!props.useStatusIcon) return
    if (!visible) return
    if (state !== "success" && state !== "error") return
    if (!statusIcon.value) return

    isStatusVisible.value = true
    scheduleHide()
  },
  {immediate: true},
)

onBeforeUnmount(() => {
  if (hideTimeout != null) window.clearTimeout(hideTimeout)
})
</script>

<template>
  <v-btn
    :block="block"
    :color="color"
    :variant="variant"
    :size="size"
    :loading="loading"
    :disabled="disabled || loading"
    :type="type"
    class="submit-btn"
    @click="emit('click')"
  >
    <div
      class="submit-btn__inner"
      :class="{'submit-btn__inner--status-active': hasStatus}"
    >
      <div class="submit-btn__content">
        <v-icon
          v-if="showContentIcon"
          :icon="icon"
          class="submit-btn__icon"
        />

        <span class="submit-btn__label">
          <slot>
            {{ text }}
          </slot>
        </span>
      </div>
    </div>
    <transition
      name="submit-feedback-overlay"
      mode="out-in"
    >
      <div
        v-if="hasStatus"
        class="submit-btn__status-overlay"
        :class="statusColorClass"
      >
        <v-icon
          :icon="statusIcon"
          class="submit-btn__status-overlay-icon"
        />
      </div>
    </transition>
  </v-btn>
</template>

<style lang="scss" scoped>
.submit-btn {
  min-width: 140px;
  position: relative;
  overflow: hidden;
}

.submit-btn__inner {
  position: relative;
  width: 100%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.submit-btn__content {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  position: relative;
  z-index: 1;
  transition: opacity 0.15s ease-out;
}

.submit-btn__icon {
  font-size: 20px;
}

.submit-btn__label {
  white-space: nowrap;
}

.submit-btn__status-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: inherit;
  pointer-events: none;
  z-index: 2;
  opacity: 1;
}

.submit-btn__status-overlay-icon {
  font-size: 28px;
  color: rgba(0, 0, 0, 0.6);
}

.submit-btn__status-overlay--success {
  background-color: rgba(var(--v-theme-success), 1);
}

.submit-btn__status-overlay--error {
  background-color: rgba(var(--v-theme-error), 1);
}

.submit-btn__inner--status-active .submit-btn__content {
  opacity: 0;
}

.submit-feedback-overlay-enter-active,
.submit-feedback-overlay-leave-active {
  transition: opacity 0.8s ease-out,
  transform 0.8s ease-out;
}

.submit-feedback-overlay-enter-from,
.submit-feedback-overlay-leave-to {
  opacity: 0;
}
</style>
