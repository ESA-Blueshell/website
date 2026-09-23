<script lang="ts" setup>
/**
 * An event's poster, square and never cut, or where nobody made one a date plate: the day in
 * the house blue, the month, then the name, the time and the place. The words scale with the
 * square, so the same part serves a strip, a band and a thumbnail.
 */
defineOptions({name: "PosterArt"})

const {
  banner = undefined,
  srcset = undefined,
  width = undefined,
  height = undefined,
  sizes = undefined,
  alt = "",
  title,
  day = undefined,
  month = undefined,
  when = undefined,
  where = undefined,
} = defineProps<{
  /** The event's own poster, where somebody made one. Without it the date plate is drawn. */
  banner?: string
  srcset?: string
  width?: number
  height?: number
  sizes?: string
  /** Empty where the poster sits beside its own title, which says the same. */
  alt?: string
  title: string
  /** The day of the month and the month, as the plate leads with them. */
  day?: string
  month?: string
  /** The hours, and the place, under the name on the plate. */
  when?: string
  where?: string
}>()
</script>

<template>
  <span class="poster-art">
    <img
      v-if="banner"
      :alt="alt"
      class="poster-art__img"
      :height="height"
      :sizes="sizes"
      :src="banner"
      :srcset="srcset"
      :width="width"
    >
    <span
      v-else
      class="poster-art__plate"
    >
      <span
        v-if="day"
        class="poster-art__date"
      >
        <span class="poster-art__day">{{ day }}</span>
        <span
          v-if="month"
          class="poster-art__month"
        >{{ month }}</span>
      </span>
      <span class="poster-art__words">
        <span class="poster-art__title">{{ title }}</span>
        <span
          v-if="when"
          class="poster-art__line"
        >{{ when }}</span>
        <span
          v-if="where"
          class="poster-art__line"
        >{{ where }}</span>
      </span>
    </span>
  </span>
</template>

<style scoped>
.poster-art {
  container-type: inline-size;
  position: relative;
  display: block;
  flex: none;
  width: 100%;
  aspect-ratio: 1 / 1;
  overflow: hidden;
  background-color: var(--color-pit);
}

.poster-art__img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: opacity 240ms ease;
}

/* The date plate: the day and month at the top, the name and its facts held to the foot. */
.poster-art__plate {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  gap: 4cqw;
  padding: 9cqw 10cqw 10cqw;
  background:
    radial-gradient(120% 90% at 0 0, color-mix(in oklab, var(--color-brand) 18%, transparent), transparent 72%),
    var(--color-pit);
}

.poster-art__day {
  display: block;
  font-family: var(--font-display);
  font-size: 27cqw;
  line-height: 0.85;
  color: var(--color-brand);
}

.poster-art__month {
  display: block;
  margin-top: 3cqw;
  font-size: max(0.55rem, 6cqw);
  letter-spacing: 0.2em;
  text-transform: uppercase;
  color: var(--color-ash);
}

.poster-art__words {
  display: flex;
  flex-direction: column;
  gap: 1.5cqw;
  min-width: 0;
}

.poster-art__title {
  display: -webkit-box;
  overflow: hidden;
  font-family: var(--font-display);
  font-size: max(0.7rem, 9cqw);
  line-height: 1.1;
  text-transform: uppercase;
  overflow-wrap: anywhere;
  color: var(--color-chalk);
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 3;
}

.poster-art__line {
  overflow: hidden;
  font-size: max(0.55rem, 5cqw);
  line-height: 1.3;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--color-ash);
}

@media (prefers-reduced-motion: reduce) {
  .poster-art__img {
    transition: none;
  }
}
</style>
