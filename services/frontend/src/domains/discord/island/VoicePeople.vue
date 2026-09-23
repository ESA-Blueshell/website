<script lang="ts" setup>
/**
 * Who is in a voice room, on one line: each person's avatar and name, as many as fit, then
 * "and N more". The row is measured rather than capped at a count, so a wide widget names
 * more people than a phone does and neither wraps.
 */
import {computed, nextTick, onBeforeUnmount, onMounted, ref, watch} from "vue"
import {fitting, type VoicePerson} from "../rooms"

defineOptions({name: "VoicePeople"})

const {people} = defineProps<{people: VoicePerson[]}>()

/** The space between two things on the line, which the stylesheet sets as the row's gap. */
const GAP = 8

const row = ref<HTMLElement | null>(null)
const measure = ref<HTMLElement | null>(null)
const shown = ref(people.length)

const initialOf = (name: string): string => name.trim().charAt(0).toUpperCase()

/*
 * Every person and the longest "and N more" are laid out once, out of sight, and read from
 * there; the row draws only what fits.
 */
const refit = () => {
  const box = row.value
  const ruler = measure.value
  if (!box || !ruler) return
  const widths = [...ruler.querySelectorAll<HTMLElement>(".people__person")].map(one => one.offsetWidth)
  // The label with the most digits it can carry, so a label with fewer always fits in its room.
  const more = ruler.querySelector<HTMLElement>(".people__more")?.offsetWidth ?? 0
  shown.value = fitting(widths, GAP, box.clientWidth, left => (left === 0 ? 0 : more))
}

let observer: ResizeObserver | undefined
onMounted(() => {
  refit()
  if (typeof ResizeObserver !== "undefined" && row.value) {
    observer = new ResizeObserver(refit)
    observer.observe(row.value)
  }
})
onBeforeUnmount(() => observer?.disconnect())
watch(() => people, () => void nextTick(refit))

const visible = computed(() => people.slice(0, shown.value))
const left = computed(() => people.length - visible.value.length)
</script>

<template>
  <span
    ref="row"
    class="people"
  >
    <span
      v-for="person in visible"
      :key="person.name"
      class="people__person"
    >
      <img
        v-if="person.avatar"
        alt=""
        class="people__avatar"
        :src="person.avatar"
      >
      <span
        v-else
        aria-hidden="true"
        class="people__avatar people__avatar--initial"
      >{{ initialOf(person.name) }}</span>
      <span class="people__name">{{ person.name }}</span>
    </span>
    <span
      v-if="left > 0"
      class="people__more"
    >and {{ left }} more</span>

    <span
      ref="measure"
      aria-hidden="true"
      class="people__ruler"
    >
      <span
        v-for="person in people"
        :key="person.name"
        class="people__person"
      >
        <span class="people__avatar" />
        <span class="people__name">{{ person.name }}</span>
      </span>
      <span class="people__more">and {{ people.length }} more</span>
    </span>
  </span>
</template>

<style scoped>
.people {
  position: relative;
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  overflow: hidden;
  white-space: nowrap;
}

/* Laid out at its natural width to be read, never seen and never in the way. */
.people__ruler {
  position: absolute;
  display: flex;
  gap: 8px;
  top: 0;
  left: 0;
  visibility: hidden;
  pointer-events: none;
}

.people__person {
  display: inline-flex;
  flex: none;
  align-items: center;
  gap: 5px;
}

.people__avatar {
  flex: none;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  object-fit: cover;
}

.people__avatar--initial {
  display: grid;
  place-items: center;
  font-size: 10px;
  font-weight: 600;
  color: #ffffff;
  background: #5865f2;
}

.people__name,
.people__more {
  font-size: 12.5px;
  color: #b5bac1;
}

.people__more {
  flex: none;
}
</style>
