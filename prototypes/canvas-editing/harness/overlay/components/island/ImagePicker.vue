<script lang="ts" setup>
import {computed, ref, useId} from "vue"
import {srcsetOf, type Picture, type PictureStore} from "./pictures"

/**
 * The one picture input: the event form's file field, with the picture that is held shown in a
 * frame of the shape it is used in.
 *
 * Every use differs only by its parameters: the shape the frame is cut to (a 1:1 icon, a wide
 * banner, a 2:3 portrait, a poster), how the picture sits in it, whether a vector may be chosen
 * and what it is called. Choosing a file stores it at once and the input holds what came back;
 * nothing is on a record until the page around it is saved. How the bytes are stored is the
 * caller's to say, because the pictures belong to several domains (frontend ADR-001).
 */
defineOptions({name: "ImagePicker"})

type Shape = "banner" | "icon" | "square" | "portrait" | "poster"

const props = withDefaults(defineProps<{
  /** The picture now held, or nothing where none is. */
  picture?: Picture | null
  label: string
  testid: string
  /** How the chosen bytes are put into storage, which is the caller's to decide. */
  store: PictureStore
  /** The shape the picture is used in, which the frame is cut to so the preview tells the truth. */
  shape?: Shape
  /** How the picture sits in its frame: filling it, cropped, or fitted whole. */
  fit?: "cover" | "contain"
  /** What an empty input says; "Choose a banner" and the like when left out. */
  say?: string
  /** A row with its words, as on the event form, or the frame alone where there is no room. */
  layout?: "row" | "tile"
  /** Whether the input offers to take the picture away, which a required picture does not. */
  mayClear?: boolean
  /** Whether this picture may be a vector, which only a logo may be. */
  mayBeVector?: boolean
  /** Whether something outside is busy, which is not the same as this input uploading. */
  busy?: boolean
}>(), {picture: null, shape: "banner", fit: undefined, say: undefined, layout: "row", mayClear: true, mayBeVector: false, busy: false})

const emit = defineEmits<{
  (event: "update:picture", picture: Picture | null): void
}>()

const input = ref<HTMLInputElement | null>(null)
const failure = ref<string | null>(null)
const uploading = ref(false)
const controlId = `${useId()}-picture`

/** What the api admits, so a refusal happens here rather than after the upload. */
const RASTER = "image/png,image/jpeg,image/webp"
const accept = computed(() => (props.mayBeVector ? `${RASTER},image/svg+xml` : RASTER))
const MAX_BYTES = 15 * 1024 * 1024

const RATIOS: Record<Shape, string> = {banner: "16 / 9", icon: "1 / 1", square: "1 / 1", portrait: "2 / 3", poster: "1 / 1.414"}
const ratio = computed(() => RATIOS[props.shape])
/* A logo is fitted whole, so a transparent mark is never cropped; anything else fills its frame. */
const fitted = computed(() => props.fit ?? (props.shape === "icon" ? "contain" : "cover"))

const has = computed(() => Boolean(props.picture))
const working = computed(() => props.busy || uploading.value)
const srcset = computed(() => srcsetOf(props.picture))
const noun = computed(() => props.label.toLowerCase())
const empty = computed(() => props.say ?? `Choose ${/^[aeiou]/.test(noun.value) ? "an" : "a"} ${noun.value}`)
const under = computed(() => {
  if (working.value) return "Uploading"
  return has.value ? "Pick another from this machine" : "Pick one from this machine"
})

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
    const stored = await props.store(chosen)
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
    class="picture"
    :class="[`picture--${layout}`, {'picture--wrong': failure, 'picture--held': has, 'picture--busy': working}]"
    :data-testid="testid"
  >
    <input
      :id="controlId"
      ref="input"
      :accept="accept"
      :aria-label="`${has ? 'Replace' : 'Add'} the ${noun}`"
      class="picture__input"
      :data-testid="`${testid}-file`"
      :disabled="working"
      type="file"
      @change="choose"
    >

    <label
      class="picture__plate"
      :data-testid="`${testid}-press`"
      :for="controlId"
      :style="{aspectRatio: ratio}"
    >
      <img
        v-if="picture"
        alt=""
        class="picture__shot"
        :data-testid="`${testid}-preview`"
        sizes="12rem"
        :src="picture.url"
        :srcset="srcset"
        :style="{objectFit: fitted}"
      >
      <span
        v-if="working"
        class="picture__spin"
      />
      <svg
        v-else-if="!picture"
        aria-hidden="true"
        class="picture__mark"
        fill="none"
        stroke="currentColor"
        stroke-linecap="round"
        stroke-linejoin="round"
        stroke-width="1.5"
        viewBox="0 0 24 24"
      >
        <path d="M12 16V4" />
        <path d="m7.5 8.5 4.5-4.5 4.5 4.5" />
        <path d="M4 16v3.5h16V16" />
      </svg>
    </label>

    <div class="picture__said">
      <label
        class="picture__name"
        :data-testid="has ? `${testid}-replace` : `${testid}-empty`"
        :for="controlId"
      >{{ has ? label : empty }}</label>
      <span class="picture__under">{{ under }}</span>
    </div>

    <button
      v-if="has && mayClear"
      :aria-label="`Take the ${noun} off`"
      class="picture__off"
      :data-testid="`${testid}-clear`"
      :disabled="working"
      type="button"
      @click="clear"
    >
      <svg
        aria-hidden="true"
        fill="none"
        stroke="currentColor"
        stroke-linecap="round"
        stroke-width="1.7"
        viewBox="0 0 24 24"
      >
        <path d="M6 6 18 18M18 6 6 18" />
      </svg>
    </button>

    <p
      v-if="failure"
      class="picture__failure"
      :data-testid="`${testid}-failure`"
      role="alert"
    >
      {{ failure }}
    </p>
  </div>
</template>

<style scoped>
/* The event form's file field, line for line, with the plate cut to the picture's own shape. */
.picture {
  position: relative;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.85rem;
  width: 100%;
  min-width: 0;
  padding: 0.55rem 0.7rem;
  background-color: color-mix(in oklab, var(--color-chalk) 7%, transparent);
  border-bottom: 1px solid var(--color-ok);
}

.picture--wrong {
  border-bottom-color: var(--color-wrong);
}

.picture__input {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip-path: inset(50%);
}

.picture__plate {
  position: relative;
  flex: none;
  display: grid;
  place-items: center;
  height: 3.4rem;
  overflow: hidden;
  background-color: color-mix(in oklab, var(--color-chalk) 6%, transparent);
  color: var(--color-ash);
  cursor: pointer;
}

.picture__plate:hover {
  color: var(--color-chalk);
  background-color: color-mix(in oklab, var(--color-chalk) 11%, transparent);
}

.picture__input:focus-visible + .picture__plate {
  outline: 2px solid var(--color-brand);
  outline-offset: 2px;
}

.picture--busy .picture__plate {
  opacity: 0.7;
}

.picture__shot {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
}

.picture__mark {
  width: 22px;
  height: 22px;
}

.picture__spin {
  position: relative;
  width: 18px;
  height: 18px;
  border: 2px solid color-mix(in oklab, var(--color-chalk) 25%, transparent);
  border-top-color: var(--color-chalk);
  border-radius: 50%;
  animation: picture-spin 0.8s linear infinite;
}

@keyframes picture-spin {
  to { transform: rotate(360deg); }
}

.picture__said {
  display: flex;
  flex: 1 1 8rem;
  flex-direction: column;
  gap: 0.1rem;
  min-width: 0;
}

.picture__name {
  overflow: hidden;
  font-family: var(--font-body);
  font-size: 0.85rem;
  color: var(--color-chalk);
  text-overflow: ellipsis;
  white-space: nowrap;
  cursor: pointer;
}

.picture__under {
  font-family: var(--font-bitmap);
  font-size: 0.68rem;
  color: var(--color-ash);
}

.picture__off {
  margin-left: auto;
  display: grid;
  place-items: center;
  padding: 0.2rem;
  border: 0;
  background: none;
  color: var(--color-ash);
  cursor: pointer;
}

.picture__off:hover {
  color: var(--color-chalk);
}

.picture__off svg {
  width: 15px;
  height: 15px;
}

.picture__failure {
  flex-basis: 100%;
  font-size: 0.78rem;
  color: var(--color-wrong);
}

/* The frame alone, for a picture beside a card's fields: the words stay for a screen reader. */
.picture--tile {
  justify-self: start;
  align-self: start;
  width: auto;
  padding: 0.45rem;
}

.picture--tile .picture__plate {
  height: 4.2rem;
}

.picture--tile .picture__said {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip-path: inset(50%);
}

.picture--tile .picture__off {
  position: absolute;
  top: 0.15rem;
  right: 0.15rem;
  margin: 0;
  background-color: color-mix(in oklab, var(--color-ground) 70%, transparent);
}
</style>
