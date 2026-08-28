<script lang="ts" setup>
import {computed, ref} from "vue"

/**
 * One uploaded image, and the two things that can be done to it.
 *
 * The picker shows what is set rather than describing it: an image nobody can see is one
 * nobody can tell is wrong. Choosing a file uploads it there and then — there is no separate
 * save, because there is nothing else on the control to save with it.
 */
defineOptions({name: "ImagePicker"})

const props = withDefaults(defineProps<{
  /** Where the image now set is served, or nothing where none is. */
  url?: string | null
  label: string
  testid: string
  /** Whether the control offers to take the image away, which a required image does not. */
  mayClear?: boolean
  busy?: boolean
}>(), {url: null, mayClear: true, busy: false})

const emit = defineEmits<{
  (event: "pick", file: File): void
  (event: "clear"): void
}>()

const input = ref<HTMLInputElement | null>(null)
const tooLarge = ref(false)

/** What the api admits, so a refusal happens here rather than after the upload. */
const ACCEPT = "image/png,image/jpeg,image/webp"
const MAX_BYTES = 15 * 1024 * 1024

const has = computed(() => Boolean(props.url))

const choose = (event: Event) => {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file) return
  tooLarge.value = file.size > MAX_BYTES
  if (!tooLarge.value) emit("pick", file)
  // Cleared so choosing the same file again is still a change the input reports.
  if (input.value) input.value.value = ""
}
</script>

<template>
  <div
    class="picker"
    :data-testid="testid"
  >
    <span class="picker__label">{{ label }}</span>

    <div class="picker__body">
      <img
        v-if="has"
        alt=""
        class="picker__preview"
        :data-testid="`${testid}-preview`"
        :src="url ?? undefined"
      >
      <span
        v-else
        class="picker__empty"
        :data-testid="`${testid}-empty`"
      >Nothing uploaded</span>

      <div class="picker__actions">
        <label
          class="picker__button"
          :class="{'picker__button--busy': busy}"
        >
          {{ has ? "Replace" : "Upload" }}
          <input
            ref="input"
            :accept="ACCEPT"
            class="picker__file"
            :data-testid="`${testid}-file`"
            :disabled="busy"
            type="file"
            @change="choose"
          >
        </label>

        <button
          v-if="has && mayClear"
          class="picker__button picker__button--quiet"
          :data-testid="`${testid}-clear`"
          :disabled="busy"
          type="button"
          @click="emit('clear')"
        >
          Remove
        </button>
      </div>
    </div>

    <p
      v-if="tooLarge"
      class="picker__failure"
      :data-testid="`${testid}-too-large`"
    >
      That file is larger than 15 MB.
    </p>
  </div>
</template>

<style scoped>
.picker {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
}

.picker__label {
  font-family: var(--font-body);
  font-size: 0.66rem;
  letter-spacing: 0.18em;
  text-transform: uppercase;
  color: var(--color-ash);
}

.picker__body {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.picker__preview {
  width: 6rem;
  height: 3.375rem;
  object-fit: cover;
  border-radius: 0.25rem;
  background-color: var(--color-surface);
}

.picker__empty {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 6rem;
  height: 3.375rem;
  border: 1px dashed color-mix(in srgb, var(--color-ash) 40%, transparent);
  border-radius: 0.25rem;
  font-family: var(--font-body);
  font-size: 0.68rem;
  text-align: center;
  color: var(--color-ash);
}

.picker__actions {
  display: flex;
  gap: 0.4rem;
}

.picker__button {
  cursor: pointer;
  padding: 0.3rem 0.7rem;
  border: 1px solid color-mix(in srgb, var(--color-ash) 40%, transparent);
  border-radius: 0.25rem;
  background: none;
  font-family: var(--font-body);
  font-size: 0.72rem;
  color: inherit;
  transition: border-color 160ms ease, color 160ms ease;
}

.picker__button:hover,
.picker__button:focus-visible {
  border-color: currentcolor;
}

.picker__button--busy {
  opacity: 0.6;
  cursor: progress;
}

.picker__button--quiet {
  color: var(--color-ash);
}

/* The label is the control; the input itself is only the file dialog behind it. */
.picker__file {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  overflow: hidden;
  clip-path: inset(50%);
  white-space: nowrap;
}

.picker__failure {
  font-family: var(--font-body);
  font-size: 0.72rem;
  color: var(--color-warning, #ff8a80);
}

@media (prefers-reduced-motion: reduce) {
  .picker__button {
    transition: none;
  }
}
</style>
