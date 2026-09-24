<script lang="ts" setup>
/* Markdown as it reads once written: the same looks the editor gives it while it is typed. */
import {computed} from "vue"
import $markdownToHtml from "@/plugins/markdownToHtml"

defineOptions({name: "MarkdownView"})

const {source} = defineProps<{
  source: string
}>()

const html = computed(() => $markdownToHtml(source))
</script>

<template>
  <!-- Sanitised in markdownToHtml, because it is written by members and read in public. -->
  <!-- eslint-disable-next-line vue/no-v-html -->
  <div
    class="markdown-view"
    v-html="html"
  />
</template>

<style scoped>
/* The island resets headings and lists to plain text, so each is given its look back here. */
.markdown-view {
  overflow-wrap: break-word;
}

.markdown-view :deep(:is(p, ul, ol, blockquote, pre, table)) {
  margin: 0 0 1.05rem;
}

.markdown-view :deep(:is(h1, h2, h3, h4, h5, h6)) {
  margin: 1.4rem 0 0.6rem;
  font-family: var(--font-display);
  font-weight: 400;
  line-height: 1.25;
  color: var(--color-chalk);
}

.markdown-view :deep(> :first-child) {
  margin-top: 0;
}

.markdown-view :deep(> :last-child) {
  margin-bottom: 0;
}

.markdown-view :deep(h1) {
  font-size: 1.55rem;
}

.markdown-view :deep(h2) {
  font-size: 1.3rem;
}

.markdown-view :deep(:is(h3, h4, h5, h6)) {
  font-size: 1.1rem;
}

.markdown-view :deep(:is(ul, ol)) {
  padding-left: 1.4rem;
}

.markdown-view :deep(ul) {
  list-style: disc;
}

.markdown-view :deep(ol) {
  list-style: decimal;
}

.markdown-view :deep(li::marker) {
  color: var(--color-ash);
}

.markdown-view :deep(li > :is(ul, ol)) {
  margin-bottom: 0;
}

.markdown-view :deep(strong) {
  font-weight: 700;
  color: var(--color-chalk);
}

.markdown-view :deep(del) {
  color: var(--color-ash);
}

.markdown-view :deep(a) {
  color: var(--color-brand);
  text-decoration: underline;
  text-underline-offset: 3px;
}

.markdown-view :deep(blockquote) {
  padding-left: 1rem;
  border-left: 3px solid var(--color-hairline);
  font-style: italic;
  color: var(--color-ash);
}

.markdown-view :deep(code) {
  font-family: var(--font-bitmap);
  font-size: 0.85em;
  color: var(--color-eyebrow);
}

.markdown-view :deep(pre) {
  padding: 0.8rem 1rem;
  overflow-x: auto;
  background: color-mix(in oklab, var(--color-chalk) 7%, transparent);
}

.markdown-view :deep(hr) {
  margin: 1.4rem 0;
  border: 0;
  border-top: 1px solid var(--color-hairline);
}

.markdown-view :deep(table) {
  display: block;
  max-width: 100%;
  overflow-x: auto;
  border-collapse: collapse;
}

.markdown-view :deep(:is(th, td)) {
  padding: 0.35rem 0.7rem;
  border: 1px solid var(--color-hairline);
  text-align: left;
}

.markdown-view :deep(img) {
  max-width: 100%;
  height: auto;
}
</style>
