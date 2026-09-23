<script lang="ts" setup>
import template from "@/assets/association/event-template.webp"

/**
 * An event's poster, square and never cut, or the association's own template with the event's
 * name, time and place written where a poster puts them. The words scale with the square, so
 * the same part serves a strip, a band and a thumbnail.
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
  when = undefined,
  where = undefined,
} = defineProps<{
  /** The event's own poster, where somebody made one. Without it the template is drawn. */
  banner?: string
  srcset?: string
  width?: number
  height?: number
  sizes?: string
  /** Empty where the poster sits beside its own title, which says the same. */
  alt?: string
  title: string
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
      <img
        alt=""
        class="poster-art__img"
        :src="template"
      >
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

/*
 * The template's own words, laid where the posters put theirs: in the dark band under the blue
 * rule, which is the bottom quarter of the square.
 */
.poster-art__plate {
  position: absolute;
  inset: 0;
  display: block;
}

.poster-art__plate .poster-art__img {
  object-fit: contain;
}

.poster-art__words {
  position: absolute;
  inset-inline: 12%;
  bottom: 2%;
  display: flex;
  /* The dark band under the blue rule is the bottom quarter of the template, and the words
     stay inside it however long the title runs. */
  max-height: 22%;
  flex-direction: column;
  justify-content: flex-end;
  gap: 0.4cqw;
  overflow: hidden;
  text-align: center;
  color: #ffffff;
}

.poster-art__title {
  display: -webkit-box;
  overflow: hidden;
  font-family: var(--font-body);
  font-size: 6cqw;
  font-weight: 700;
  line-height: 1.1;
  text-overflow: ellipsis;
  overflow-wrap: anywhere;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.poster-art__line {
  overflow: hidden;
  font-family: var(--font-body);
  font-size: 4cqw;
  font-weight: 400;
  line-height: 1.25;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (prefers-reduced-motion: reduce) {
  .poster-art__img {
    transition: none;
  }
}
</style>
