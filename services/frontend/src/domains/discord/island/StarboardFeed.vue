<script setup lang="ts">
import {onMounted, ref} from "vue"
import MarkdownView from "@/components/island/MarkdownView.vue"
import type {StarboardEntryResponse} from "@/services/api"
import {safeFormatISO} from "@/utils/datetime"
import {initialsOf} from "@/utils/initials"
import {readStarboard} from "../adapters/starboard"

/**
 * What the server starred lately, drawn as a Discord channel as wide as the voice widget above it:
 * every message whole, in the api's order, the list scrolling inside. The api decides what may
 * show; where it cannot say, or has nothing, the feed is not drawn at all.
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
    <div class="starboard__head">
      <svg
        aria-hidden="true"
        class="starboard__hash"
        fill="none"
        height="20"
        stroke="currentColor"
        stroke-linecap="round"
        stroke-width="2"
        viewBox="0 0 24 24"
        width="20"
      >
        <path d="M10 3 7 21M17 3l-3 18M4 8.5h17M3 15.5h17" />
      </svg>
      <h3
        id="starboard-title"
        class="starboard__title"
      >
        starboard
      </h3>
      <span class="starboard__topic">The most starred messages of the last 30 days</span>
    </div>

    <ol class="starboard__list">
      <li
        v-for="entry in entries"
        :key="entry.id"
        class="starboard__entry"
        :data-testid="`home-starboard-${entry.id}`"
      >
        <img
          v-if="entry.avatar"
          alt=""
          class="starboard__avatar"
          height="40"
          loading="lazy"
          :src="entry.avatar"
          width="40"
        >
        <span
          v-else
          aria-hidden="true"
          class="starboard__avatar starboard__avatar--initials"
        >{{ initialsOf(nameOf(entry)).charAt(0) }}</span>

        <div class="starboard__body">
          <div class="starboard__byline">
            <span class="starboard__name">{{ nameOf(entry) }}</span>
            <span class="starboard__channel">in #{{ entry.channel }}</span>
            <time
              class="starboard__when"
              :datetime="entry.postedAt"
            >{{ safeFormatISO(entry.postedAt, "d LLL") }}</time>
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
            <span class="starboard__stars">
              <svg
                aria-hidden="true"
                height="14"
                viewBox="0 0 24 24"
                width="14"
              >
                <path
                  d="m12 2.8 2.8 5.8 6.3.9-4.6 4.4 1.1 6.3L12 17.2l-5.6 3 1.1-6.3L2.9 9.5l6.3-.9L12 2.8Z"
                  fill="currentColor"
                />
              </svg>
              {{ entry.stars }}<span class="sr-only"> stars</span>
            </span>
            <a
              class="starboard__open"
              :href="entry.href"
              rel="noopener"
              target="_blank"
            >Open in Discord</a>
          </div>
        </div>
      </li>
    </ol>
  </section>
</template>

<style scoped>
/*
 * Discord's own chrome, the same in both themes, and as wide as the voice widget above it. The
 * site's tokens are set to Discord's so the markdown inside reads on its ground in light too.
 */
.starboard {
  --color-chalk: #dbdee1;
  --color-ash: #949ba4;
  --color-hairline: #3f4147;
  --color-void: #1e1f22;
  --color-eyebrow: #949ba4;
  --color-brand-ink: #00a8fc;

  display: flex;
  flex-direction: column;
  width: 100%;
  max-width: 820px;
  /* The messages scroll inside it past this, so a busy month keeps the band its height. */
  max-height: 36rem;
  margin: 1rem auto 0;
  overflow: hidden;
  border-radius: 14px;
  background: #313338;
  color: #dbdee1;
  font-family: var(--font-body);
}

.starboard__head {
  display: flex;
  flex: none;
  align-items: center;
  gap: 0.45rem;
  min-width: 0;
  padding: 0.7rem 1rem;
  border-bottom: 1px solid #26282c;
  background: #2b2d31;
}

.starboard__hash {
  flex: none;
  color: #80848e;
}

/* The site's heading face would make it read as a band heading rather than a channel name. */
.starboard__title {
  margin: 0;
  font-family: var(--font-body);
  font-size: 0.95rem;
  font-style: normal;
  font-weight: 600;
  line-height: 1.3;
  letter-spacing: 0;
  text-transform: none;
  color: #f2f3f5;
}

.starboard__topic {
  overflow: hidden;
  margin-left: 0.35rem;
  padding-left: 0.8rem;
  border-left: 1px solid #3f4147;
  font-size: 0.8rem;
  white-space: nowrap;
  text-overflow: ellipsis;
  color: #949ba4;
}

.starboard__list {
  flex: 1 1 auto;
  min-height: 0;
  margin: 0;
  padding: 0.5rem 0;
  overflow-y: auto;
  list-style: none;
  overscroll-behavior: contain;
}

.starboard__list::-webkit-scrollbar {
  width: 8px;
}

.starboard__list::-webkit-scrollbar-thumb {
  border-radius: 4px;
  background: #1a1b1e;
}

.starboard__entry {
  display: grid;
  grid-template-columns: 40px minmax(0, 1fr);
  column-gap: 0.9rem;
  padding: 0.55rem 1rem 0.65rem;
}

.starboard__entry:hover {
  background: #2e3035;
}

.starboard__avatar {
  width: 40px;
  height: 40px;
  border-radius: 9999px;
}

/* Where the author has no picture, Discord's blurple plate with their first letter. */
.starboard__avatar--initials {
  display: grid;
  place-items: center;
  background: #5865f2;
  font-size: 1rem;
  font-weight: 600;
  color: #fff;
}

.starboard__body {
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
  min-width: 0;
}

.starboard__byline {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  column-gap: 0.5rem;
  line-height: 1.3;
}

.starboard__name {
  font-size: 0.95rem;
  font-weight: 600;
  color: #f2f3f5;
}

.starboard__channel,
.starboard__when {
  font-size: 0.75rem;
  color: #949ba4;
}

.starboard__text {
  overflow-wrap: anywhere;
  font-size: 0.93rem;
  line-height: 1.4;
  color: #dbdee1;
}

.starboard__text :deep(:is(h1, h2, h3, h4, h5, h6)) {
  margin: 0.2rem 0 0.3rem;
  font-size: 1rem;
  color: #f2f3f5;
}

.starboard__text :deep(:is(p, ul, ol, blockquote)) {
  margin: 0 0 0.35rem;
}

.starboard__text :deep(:last-child) {
  margin-bottom: 0;
}

.starboard__text :deep(a) {
  color: #00a8fc;
}

/* A picture keeps its own shape, as Discord shows an attachment. */
.starboard__image {
  display: block;
  width: auto;
  max-width: min(100%, 26rem);
  height: auto;
  max-height: 22rem;
  margin-top: 0.15rem;
  border-radius: 8px;
}

.starboard__foot {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  margin-top: 0.15rem;
}

/* The star reaction, as Discord draws one. */
.starboard__stars {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  padding: 0.15rem 0.5rem;
  border: 1px solid #4e5058;
  border-radius: 8px;
  background: #2b2d31;
  font-size: 0.8rem;
  font-weight: 600;
  color: #dbdee1;
}

.starboard__stars svg {
  color: #f0b232;
}

.starboard__open {
  font-size: 0.75rem;
  color: #00a8fc;
}

.starboard__open:hover,
.starboard__open:focus-visible {
  text-decoration: underline;
}

@media (--phone) {
  .starboard {
    max-height: 32rem;
  }

  .starboard__topic {
    display: none;
  }

  .starboard__entry {
    grid-template-columns: 32px minmax(0, 1fr);
    column-gap: 0.7rem;
    padding: 0.55rem 0.75rem 0.65rem;
  }

  .starboard__avatar {
    width: 32px;
    height: 32px;
  }
}
</style>
