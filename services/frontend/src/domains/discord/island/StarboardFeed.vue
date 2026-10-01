<script setup lang="ts">
import {onMounted, ref} from "vue"
import MarkdownView from "@/components/island/MarkdownView.vue"
import type {StarboardEntryResponse} from "@/services/api"
import {safeFormatISO} from "@/utils/datetime"
import {initialsOf} from "@/utils/initials"
import {discordInvite} from "../adapters/doors"
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
      <h3
        id="starboard-title"
        class="starboard__title"
      >
        <!-- An invite into the channel, which opens it for somebody already on the server. -->
        <a
          class="starboard__door"
          data-testid="home-starboard-channel"
          :href="discordInvite('starboard')"
          rel="noopener"
          target="_blank"
        >
          <svg
            aria-hidden="true"
            class="starboard__hash"
            fill="none"
            height="24"
            stroke="currentColor"
            stroke-linecap="round"
            stroke-width="2"
            viewBox="0 0 24 24"
            width="24"
          >
            <path d="M10 3 7 21M17 3l-3 18M4 8.5h17M3 15.5h17" />
          </svg>
          starboard
        </a>
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
            <span
              v-if="entry.channel"
              class="starboard__channel"
            >#{{ entry.channel }}</span>
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
 * Discord's own chrome and metrics, the same in both themes, and as wide as the voice widget above
 * it. The site's tokens are set to Discord's so the markdown inside reads on its ground in light too.
 */
.starboard {
  --color-chalk: #dbdee1;
  --color-ash: #949ba4;
  --color-hairline: #4e5058;
  --color-void: #1e1f22;
  --color-eyebrow: #949ba4;
  --color-brand: #5865f2;
  --color-brand-lit: #c9cdfb;
  --color-brand-ink: #00a8fc;
  --font-body: var(--discord-font);
  --font-prose: var(--discord-font);

  display: flex;
  flex-direction: column;
  width: 100%;
  max-width: 820px;
  /* The messages scroll inside it past this, so a busy month keeps the band its height. */
  max-height: 36rem;
  margin: 1rem auto 0;
  overflow: hidden;
  border-radius: 14px;
  background: #2b2d31;
  color: #dbdee1;
  font-family: var(--discord-font);
}

.starboard__head {
  display: flex;
  flex: none;
  align-items: center;
  min-width: 0;
  height: 48px;
  padding: 0 1rem;
  border-bottom: 1px solid #3f4147;
}

.starboard__hash {
  flex: none;
  width: 24px;
  height: 24px;
  margin-right: 0.5rem;
  color: #80848e;
}

/* The site's heading face would make it read as a band heading rather than a channel name. */
.starboard__title {
  margin: 0;
  font-family: var(--discord-font);
  font-size: 1rem;
  font-style: normal;
  font-weight: 600;
  line-height: 1.25rem;
  letter-spacing: 0;
  text-transform: none;
  color: #f2f3f5;
}

.starboard__door {
  display: flex;
  align-items: center;
  color: inherit;
}

.starboard__door:hover,
.starboard__door:focus-visible {
  text-decoration: underline;
}

.starboard__topic {
  overflow: hidden;
  margin-left: 0.5rem;
  padding-left: 0.5rem;
  border-left: 1px solid #3f4147;
  font-size: 0.875rem;
  font-weight: 400;
  line-height: 1.5rem;
  white-space: nowrap;
  text-overflow: ellipsis;
  color: #b5bac1;
}

.starboard__list {
  flex: 1 1 auto;
  min-height: 0;
  margin: 0;
  padding: 1rem 0;
  overflow-y: auto;
  list-style: none;
  overscroll-behavior: contain;
}

.starboard__list::-webkit-scrollbar {
  width: 8px;
}

.starboard__list::-webkit-scrollbar-track {
  background: #232428;
}

.starboard__list::-webkit-scrollbar-thumb {
  border-radius: 4px;
  background: #1a1b1e;
}

/* A message group as Discord spaces one: the avatar 16px in, the words from 72px. */
.starboard__entry {
  display: grid;
  grid-template-columns: 40px minmax(0, 1fr);
  column-gap: 1rem;
  padding: 0.125rem 1rem 0.125rem;
}

.starboard__entry + .starboard__entry {
  margin-top: 1.0625rem;
}

.starboard__entry:hover {
  background: #313338;
}

.starboard__avatar {
  width: 40px;
  height: 40px;
  margin-top: 0.125rem;
  border-radius: 9999px;
}

/* Where the author has no picture, Discord's blurple plate with their first letter. */
.starboard__avatar--initials {
  display: grid;
  place-items: center;
  background: #5865f2;
  font-size: 1rem;
  font-weight: 500;
  color: #fff;
}

.starboard__body {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.starboard__byline {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  column-gap: 0.25rem;
  line-height: 1.375rem;
}

.starboard__name {
  margin-right: 0.25rem;
  font-size: 1rem;
  font-weight: 500;
  color: #f2f3f5;
}

/* The source channel as Discord writes a channel mention. */
.starboard__channel {
  padding: 0 2px;
  border-radius: 3px;
  background: rgb(88 101 242 / 30%);
  font-size: 0.8125rem;
  font-weight: 500;
  color: #c9cdfb;
}

.starboard__when {
  margin-left: 0.25rem;
  font-size: 0.75rem;
  color: #949ba4;
}

.starboard__text {
  overflow-wrap: anywhere;
  font-size: 1rem;
  line-height: 1.375rem;
  color: #dbdee1;
}

.starboard__text :deep(:is(h1, h2, h3, h4, h5, h6)) {
  margin: 0.5rem 0 0.25rem;
  font-family: var(--discord-font);
  font-size: 1.25rem;
  font-style: normal;
  line-height: 1.375;
  text-transform: none;
  color: #f2f3f5;
}

.starboard__text :deep(:is(p, ul, ol, blockquote)) {
  margin: 0;
}

/* A blank line between paragraphs, as Discord keeps the one somebody typed. */
.starboard__text :deep(p + p) {
  margin-top: 1.375rem;
}

.starboard__text :deep(:is(ul, ol)) {
  margin: 0.25rem 0 0.25rem 1rem;
}

.starboard__text :deep(:first-child) {
  margin-top: 0;
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
  margin-top: 0.25rem;
  border-radius: 8px;
}

.starboard__foot {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-top: 0.25rem;
}

/* The star reaction, as Discord draws one. */
.starboard__stars {
  display: inline-flex;
  align-items: center;
  gap: 0.375rem;
  height: 1.5rem;
  padding: 0 0.375rem;
  border: 1px solid transparent;
  border-radius: 0.5rem;
  background: #383a40;
  font-size: 0.875rem;
  font-weight: 500;
  color: #b5bac1;
}

.starboard__stars svg {
  width: 16px;
  height: 16px;
  color: #ffac33;
}

.starboard__open {
  font-size: 0.75rem;
  color: #949ba4;
}

.starboard__open:hover,
.starboard__open:focus-visible {
  text-decoration: underline;
  color: #dbdee1;
}

@media (--phone) {
  .starboard {
    max-height: 32rem;
  }

  .starboard__door {
  display: flex;
  align-items: center;
  color: inherit;
}

.starboard__door:hover,
.starboard__door:focus-visible {
  text-decoration: underline;
}

.starboard__topic {
    display: none;
  }

  .starboard__entry {
    grid-template-columns: 32px minmax(0, 1fr);
    column-gap: 0.75rem;
    padding: 0.125rem 0.75rem;
  }

  .starboard__avatar {
    width: 32px;
    height: 32px;
  }

  .starboard__text {
    font-size: 0.9375rem;
  }
}
</style>
