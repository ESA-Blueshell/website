<script setup lang="ts">
import {onMounted, ref} from "vue"
import MarkdownView from "@/components/island/MarkdownView.vue"
import type {StarboardEntryResponse} from "@/services/api"
import {readStarboard} from "../adapters/starboard"

/**
 * What the server starred lately, in Discord's own chrome like the voice widget above it. The api
 * decides what may show; where it cannot say, or has nothing, the feed is not drawn at all.
 */
const entries = ref<StarboardEntryResponse[]>([])
onMounted(async () => {
  entries.value = (await readStarboard()) ?? []
})

const nameOf = (entry: StarboardEntryResponse): string => entry.authorNickname ?? entry.authorName
</script>

<template>
  <section
    v-if="entries.length > 0"
    aria-labelledby="starboard-title"
    class="starboard"
    data-testid="home-starboard"
  >
    <h3
      id="starboard-title"
      class="starboard__title"
    >
      From the starboard
    </h3>
    <ul class="starboard__list">
      <li
        v-for="entry in entries"
        :key="entry.id"
        class="starboard__entry"
        :data-testid="`home-starboard-${entry.id}`"
      >
        <div class="starboard__head">
          <img
            v-if="entry.avatar"
            alt=""
            class="starboard__avatar"
            height="28"
            loading="lazy"
            :src="entry.avatar"
            width="28"
          >
          <span class="starboard__name">{{ nameOf(entry) }}</span>
          <span class="starboard__channel">#{{ entry.channel }}</span>
        </div>
        <markdown-view
          v-if="entry.text"
          class="starboard__text"
          :source="entry.text"
        />
        <img
          v-if="entry.image"
          alt=""
          class="starboard__image"
          loading="lazy"
          :src="entry.image"
        >
        <div class="starboard__foot">
          <span class="starboard__stars">⭐ {{ entry.stars }}<span class="sr-only"> stars</span></span>
          <a
            class="starboard__open"
            :href="entry.href"
            rel="noopener"
            target="_blank"
          >Open in Discord</a>
        </div>
      </li>
    </ul>
  </section>
</template>

<style scoped>
.starboard {
  width: 100%;
  max-width: 820px;
  margin: 1.25rem auto 0;
  font-family: var(--font-body);
}

.starboard__title {
  margin: 0 0 0.6rem;
  font-size: 0.8rem;
  font-weight: 600;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  color: var(--color-chalk);
}

/* One row that scrolls sideways, so a busy month keeps the band its height. */
.starboard__list {
  display: flex;
  gap: 0.75rem;
  padding-bottom: 0.4rem;
  overflow-x: auto;
  scroll-snap-type: x mandatory;
}

/* Discord's own chrome, the same in both themes, as the widget above it. */
.starboard__entry {
  display: flex;
  flex: 0 0 17rem;
  flex-direction: column;
  gap: 0.5rem;
  padding: 0.7rem 0.8rem;
  border-left: 3px solid #f0b232;
  border-radius: 8px;
  background: #2b2d31;
  color: #dbdee1;
  scroll-snap-align: start;
}

.starboard__head {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  min-width: 0;
}

.starboard__avatar {
  flex: none;
  border-radius: 9999px;
}

.starboard__name {
  overflow: hidden;
  font-size: 0.85rem;
  font-weight: 600;
  white-space: nowrap;
  text-overflow: ellipsis;
  color: #f2f3f5;
}

.starboard__channel {
  margin-left: auto;
  font-size: 0.72rem;
  white-space: nowrap;
  color: #949ba4;
}

/* A long announcement fades out rather than making one card the band's height. */
.starboard__text {
  max-height: 7.5rem;
  overflow: hidden;
  font-size: 0.85rem;
  line-height: 1.4;
  color: #dbdee1;
  mask-image: linear-gradient(to bottom, #000 70%, transparent);
}

.starboard__text :deep(:is(h1, h2, h3, h4, h5, h6)) {
  margin: 0 0 0.3rem;
  font-size: 0.95rem;
  color: #f2f3f5;
}

.starboard__text :deep(:is(p, ul, ol, blockquote)) {
  margin: 0 0 0.4rem;
}

.starboard__image {
  width: 100%;
  max-height: 10rem;
  border-radius: 6px;
  object-fit: cover;
}

.starboard__foot {
  display: flex;
  align-items: center;
  margin-top: auto;
  font-size: 0.75rem;
  color: #949ba4;
}

.starboard__open {
  margin-left: auto;
  color: #00a8fc;
}

.starboard__open:hover,
.starboard__open:focus-visible {
  text-decoration: underline;
}
</style>
