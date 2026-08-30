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

/**
 * The shape the picture is drawn in, taken from what it is for.
 *
 * A banner is the wide art behind a slice and an icon is a logo, so a square frame for one and
 * a letterbox for the other is not decoration: it is the only way the preview tells the truth
 * about what was uploaded before anybody sees it on a page.
 */
const icony = computed(() => String(props.kind).includes("ICON"))
const ratio = computed(() => (icony.value ? "1 / 1" : "16 / 9"))

/** Small enough to sit beside the fields it belongs to rather than dominate the form. */
const width = computed(() => (icony.value ? "4.5rem" : "9rem"))
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

    <!--
      The frame is the control. Pressing it is how a picture arrives and how it is replaced,
      because the picture is the thing being decided and a button beside it is one more place
      to look. Taking it away is the cross in the corner, away from the press that replaces it,
      so the two are not the same gesture a pixel apart.
    -->
    <div
      class="picker__frame"
      :class="{'picker__frame--busy': working}"
      :style="{aspectRatio: ratio, width}"
    >
      <img
        v-if="picture"
        alt=""
        class="picker__preview"
        :data-testid="`${testid}-preview`"
        sizes="(max-width: 40rem) 90vw, 18rem"
        :src="picture.url"
        :srcset="srcset"
      >

      <label
        class="picker__press"
        :data-testid="`${testid}-press`"
      >
        <!-- Named for what it says: an empty frame says so, and a full one offers the
             replacement. The words are the state, so they carry the name of it. -->
        <span
          class="picker__say"
          :data-testid="has ? `${testid}-replace` : `${testid}-empty`"
        >
          {{ working ? "Uploading…" : has ? "Replace" : "Add a picture" }}
        </span>
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
        :aria-label="`Remove the ${label.toLowerCase()}`"
        class="picker__cross"
        :data-testid="`${testid}-clear`"
        :disabled="working"
        type="button"
        @click="clear"
      >
        &times;
      </button>
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
  flex: 0 0 auto;
  flex-direction: column;
  gap: 0.3rem;
  min-width: 0;
}

.picker__label {
  font-family: var(--font-display);
  font-size: 0.62rem;
  color: var(--color-ash);
  letter-spacing: 0.06em;
  text-transform: uppercase;
}

/*
 * Cut on the island's diagonal, and sized by what the picture is for rather than by a fixed
 * box: a banner previewed square would look nothing like the banner it becomes.
 */
/* Square-cornered, unlike the buttons: the diagonal is for things that are pressed along a
   band, and a picture cut on it would be a picture with a corner missing. */
.picker__frame {
  position: relative;
  overflow: hidden;
  background-color: color-mix(in oklab, var(--color-chalk) 5%, transparent);
  border: 1px solid color-mix(in oklab, var(--color-chalk) 12%, transparent);
}

.picker__frame--busy {
  opacity: 0.7;
}

.picker__preview {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

/* The whole frame, so the picture is what is pressed. */
.picker__press {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  cursor: pointer;
}

.picker__say {
  padding: 0.25rem 0.5rem;
  font-family: var(--font-display);
  font-size: 0.58rem;
  color: var(--color-chalk);
  letter-spacing: 0.06em;
  text-transform: uppercase;
  background-color: color-mix(in oklab, var(--color-void) 62%, transparent);
  text-align: center;
  opacity: 0;
  transition: opacity 200ms ease;
}

/* Said all the time while the frame is empty; only on approach once it holds a picture, so
   the picture is what is looked at rather than the words over it. */
.picker__frame:not(:has(.picker__preview)) .picker__say,
.picker__press:hover .picker__say,
.picker__press:focus-within .picker__say {
  opacity: 1;
}

.picker__file {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip-path: inset(50%);
}

/* In the corner and away from the press that replaces: two different acts, two places. */
.picker__cross {
  position: absolute;
  top: 0.2rem;
  right: 0.2rem;
  display: grid;
  width: 1.1rem;
  height: 1.1rem;
  font-size: 0.8rem;
  line-height: 1;
  color: var(--color-chalk);
  cursor: pointer;
  background-color: color-mix(in oklab, var(--color-void) 68%, transparent);
  border: 0;
  border-radius: 50%;
  place-items: center;
}

.picker__cross:hover:not(:disabled) {
  color: var(--color-void);
  background-color: var(--color-brand);
}

.picker__failure {
  margin: 0;
  font-family: var(--font-body);
  font-size: 0.78rem;
  color: var(--color-danger, #ff6b6b);
}

@media (prefers-reduced-motion: reduce) {
  .picker__say {
    transition: none;
  }
}
</style>
