<script lang="ts" setup>
import {computed, onBeforeUnmount, watch, ref} from "vue"
import {DateTime} from "luxon"
import PosterArt from "@/components/island/PosterArt.vue"
import {plateOf} from "./eventFacts"

/**
 * The event as its poster will show, built from the form while it is filled in: the poster
 * picked, or the date plate with the day, the name, the time and the place.
 */
defineOptions({name: "EventPreview"})

const {title, location = "", startTime = "", poster = null} = defineProps<{
  title: string
  location?: string
  startTime?: string
  /** The poster picked in the form, where there is one. */
  poster?: File | null
}>()

const url = ref<string | undefined>(undefined)
watch(() => poster, (file) => {
  if (url.value) URL.revokeObjectURL(url.value)
  url.value = file ? URL.createObjectURL(file) : undefined
}, {immediate: true})
onBeforeUnmount(() => {
  if (url.value) URL.revokeObjectURL(url.value)
})

/* Nothing on the plate for a start that is not a date yet; the name and place still show. */
const plate = computed(() => (DateTime.fromISO(startTime).isValid ? plateOf({startTime}) : {}))
const when = computed<string>(() => {
  const at = DateTime.fromISO(startTime)
  return at.isValid ? at.toFormat("ccc d LLLL - HH:mm") : ""
})
const name = computed<string>(() => title.trim() || "Your event")
</script>

<template>
  <aside
    aria-label="Preview"
    class="preview"
    data-testid="event-form-preview"
  >
    <p class="preview__eyebrow">
      Preview
    </p>
    <div class="preview__poster island-dark">
      <poster-art
        :banner="url"
        sizes="24rem"
        :title="name"
        v-bind="plate"
        :where="location || undefined"
      />
      <div class="preview__foot">
        <p class="preview__when">
          {{ when }}
        </p>
        <p class="preview__title">
          {{ name }}
        </p>
        <p class="preview__where">
          {{ location }}
        </p>
      </div>
    </div>
  </aside>
</template>

<style scoped>
.preview {
  position: sticky;
  top: 5rem;
  display: flex;
  flex-direction: column;
  gap: 0.9rem;
}

.preview__eyebrow {
  font-size: 11px;
  font-weight: 500;
  letter-spacing: 0.3em;
  text-transform: uppercase;
  color: var(--color-eyebrow);
}

.preview__foot {
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
  padding: 0.9rem 1.1rem 1.1rem;
  background: var(--band-ground);
}

.preview__when {
  font-family: var(--font-bitmap);
  font-size: 0.68rem;
  color: var(--color-ash);
}

.preview__title {
  font-family: var(--font-display);
  font-size: 1.1rem;
  line-height: 1.15;
  text-transform: uppercase;
  color: var(--color-chalk);
  overflow-wrap: break-word;
}

.preview__where {
  font-size: 0.82rem;
  color: var(--color-ash);
}
</style>
