<script lang="ts" setup>
import {computed, ref} from "vue"
import {storePicture, type EsportsImage} from "../adapters/esports"
import {srcsetOf} from "../pictures"
import type {FileType} from "@/services/api"

/**
 * One picture, and the two things that can be done to it.
 *
 * The picker shows what is set rather than describing it: a picture nobody can see is one
 * nobody can tell is wrong. Choosing a file puts it into storage there and then, and the
 * picker holds what came back — but nothing is on a record until the dialog around it is
 * saved. Cancelling that dialog therefore leaves the team, the person or the game exactly as
 * it was, rather than keeping a picture and throwing the rest of the form away.
 *
 * The bytes a cancelled dialog leaves in storage stay. Storage is addressed by content, the
 * pictures are small, and counting who points at a file is a larger mechanism than the
 * problem deserves.
 */
defineOptions({name: "ImagePicker"})

const props = withDefaults(defineProps<{
  /** The picture now held, or nothing where none is. */
  picture?: EsportsImage | null
  label: string
  testid: string
  /** What kind of picture this is, which decides how it is scaled and where it is stored. */
  kind: FileType
  /** Whether the control offers to take the picture away, which a required picture does not. */
  mayClear?: boolean
  /** Whether something outside is busy, which is not the same as this control uploading. */
  busy?: boolean
}>(), {picture: null, mayClear: true, busy: false})

const emit = defineEmits<{
  (event: "update:picture", picture: EsportsImage | null): void
}>()

const input = ref<HTMLInputElement | null>(null)
const failure = ref<string | null>(null)
const uploading = ref(false)

/** What the api admits, so a refusal happens here rather than after the upload. */
const ACCEPT = "image/png,image/jpeg,image/webp"
const MAX_BYTES = 15 * 1024 * 1024

const has = computed(() => Boolean(props.picture))
const working = computed(() => props.busy || uploading.value)
const srcset = computed(() => srcsetOf(props.picture))

const choose = async (event: Event) => {
  const chosen = (event.target as HTMLInputElement).files?.[0]
  // Cleared so choosing the same file again is still a change the input reports.
  if (input.value) input.value.value = ""
  if (!chosen) return

  failure.value = null
  if (chosen.size > MAX_BYTES) {
    failure.value = "That file is larger than 15 MB."
    return
  }

  uploading.value = true
  try {
    const stored = await storePicture(chosen, props.kind)
    if (!stored.ok) {
      failure.value = stored.reason
      return
    }
    emit("update:picture", stored.picture)
  } finally {
    uploading.value = false
  }
}

const clear = () => {
  failure.value = null
  emit("update:picture", null)
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
        v-if="picture"
        alt=""
        class="picker__preview"
        :data-testid="`${testid}-preview`"
        :height="picture.height ?? undefined"
        sizes="6rem"
        :src="picture.url"
        :srcset="srcset"
        :width="picture.width ?? undefined"
      >
      <span
        v-else
        class="picker__empty"
        :data-testid="`${testid}-empty`"
      >Nothing uploaded</span>

      <div class="picker__actions">
        <label
          class="picker__button"
          :class="{'picker__button--busy': working}"
        >
          {{ has ? "Replace" : "Upload" }}
          <input
            ref="input"
            :accept="ACCEPT"
            class="picker__file"
            :data-testid="`${testid}-file`"
            :disabled="working"
            type="file"
            @change="choose"
          >
        </label>

        <button
          v-if="has && mayClear"
          class="picker__button picker__button--quiet"
          :data-testid="`${testid}-clear`"
          :disabled="working"
          type="button"
          @click="clear"
        >
          Remove
        </button>
      </div>
    </div>

    <p
      v-if="failure"
      class="picker__failure"
      :data-testid="`${testid}-failure`"
    >
      {{ failure }}
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
