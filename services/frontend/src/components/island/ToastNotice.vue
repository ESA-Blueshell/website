<script lang="ts" setup>
import {onBeforeUnmount, watch} from "vue"

/**
 * A message laid over the page, with its actions beside it. It stands in a ToastArea.
 *
 * Its time does not run while it is pointed at or holds the focus, so it cannot close under
 * somebody reading it or about to press one of its buttons.
 */
defineOptions({name: "ToastNotice"})

const {open, timeout = 0, tone = "info", testid = undefined} = defineProps<{
  open: boolean
  /** Milliseconds until it closes itself. Zero keeps it until something closes it. */
  timeout?: number
  tone?: "info" | "warning"
  testid?: string
}>()

const emit = defineEmits<{"update:open": [open: boolean]}>()

let closing: ReturnType<typeof setTimeout> | undefined

const stopClosing = (): void => {
  clearTimeout(closing)
  closing = undefined
}

const startClosing = (): void => {
  stopClosing()
  if (open && timeout > 0) closing = setTimeout(() => emit("update:open", false), timeout)
}

watch(() => open, startClosing, {immediate: true})

onBeforeUnmount(stopClosing)
</script>

<template>
  <transition name="toast">
    <div
      v-if="open"
      class="toast"
      :class="{'toast--warning': tone === 'warning'}"
      :data-testid="testid"
      role="status"
      @focusin="stopClosing"
      @focusout="startClosing"
      @pointerenter="stopClosing"
      @pointerleave="startClosing"
    >
      <div class="toast__said">
        <slot />
      </div>
      <div
        v-if="$slots.actions"
        class="toast__actions"
      >
        <slot name="actions" />
      </div>
    </div>
  </transition>
</template>

<style scoped>
.toast {
  display: flex;
  align-items: center;
  gap: 0.75rem 1rem;
  box-sizing: border-box;
  min-width: min(344px, 100%);
  max-width: min(672px, 100%);
  padding: 0.75rem 1rem;
  border: 1px solid var(--color-hairline);
  background-color: var(--color-raised);
  box-shadow: 0 6px 18px rgb(0 0 0 / 28%);
  font-family: var(--font-body);
  font-size: 0.92rem;
  line-height: 1.45;
  color: var(--color-chalk);
  pointer-events: auto;
}

/* A tint of the tone over the panel, as a notice is: the toast still has to hide what is under it. */
.toast--warning {
  background-color: color-mix(in oklab, var(--color-warning) 16%, var(--color-raised));
}

.toast__said {
  flex: 1 1 auto;
  min-width: 0;
}

.toast__actions {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 0.5rem;
}

.toast-enter-active,
.toast-leave-active {
  transition: opacity 0.2s var(--ease-out-quint), translate 0.2s var(--ease-out-quint);
}

.toast-enter-from,
.toast-leave-to {
  opacity: 0;
  translate: 0 0.5rem;
}

@media (prefers-reduced-motion: reduce) {
  .toast-enter-from,
  .toast-leave-to {
    translate: none;
  }
}

@media (--phone) {
  .toast {
    flex-wrap: wrap;
  }

  .toast__actions {
    margin-left: auto;
  }
}
</style>
