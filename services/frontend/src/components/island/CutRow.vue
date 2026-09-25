<script lang="ts" setup>
/**
 * One row of a list: a glyph, a title with a line under it, and what stands at its end. A row with
 * somewhere to go is a link with an arrow and a bar that leans in on hover; one without holds its
 * own action at the end instead.
 *
 * Both edges are cut at the lean of that bar, 12°, so the edge and the bar run parallel: the cut is
 * the row's height times tan 12°.
 */
defineOptions({name: "CutRow"})

const {to = "", meta = "", testid = undefined} = defineProps<{
  title: string
  meta?: string
  to?: string
  testid?: string
}>()
</script>

<template>
  <router-link
    v-if="to"
    class="cut-row cut-row--link"
    :data-testid="testid"
    :to="to"
  >
    <span
      aria-hidden="true"
      class="cut-row__glyph"
    ><slot name="glyph" /></span>
    <span class="cut-row__words">
      <span class="cut-row__title">{{ title }}<slot name="tag" /></span>
      <span
        v-if="meta"
        class="cut-row__meta"
      >{{ meta }}</span>
    </span>
    <span class="cut-row__end"><slot name="end" /></span>
    <svg
      aria-hidden="true"
      class="cut-row__go"
      fill="none"
      viewBox="0 0 20 12"
    ><path
      d="M0 6h17M13 1.5 18.5 6 13 10.5"
      stroke="currentColor"
      stroke-width="1.4"
    /></svg>
  </router-link>

  <div
    v-else
    class="cut-row"
    :data-testid="testid"
  >
    <span
      aria-hidden="true"
      class="cut-row__glyph"
    ><slot name="glyph" /></span>
    <span class="cut-row__words">
      <span class="cut-row__title">{{ title }}<slot name="tag" /></span>
      <span
        v-if="meta"
        class="cut-row__meta"
      >{{ meta }}</span>
    </span>
    <span class="cut-row__end"><slot name="end" /></span>
  </div>
</template>

<style scoped>
.cut-row {
  --row-h: 4.7rem;
  /* tan 12°, so the edge leans with the hover bar. */
  --cut: calc(var(--row-h) * 0.2126);

  position: relative;
  display: grid;
  grid-template-columns: 2.75rem minmax(0, 1fr) auto;
  align-items: center;
  gap: 0 1.25rem;
  height: var(--row-h);
  padding: 0 calc(var(--cut) + 0.8rem) 0 calc(var(--cut) + 1rem);
  clip-path: polygon(var(--cut) 0, 100% 0, calc(100% - var(--cut)) 100%, 0 100%);
  background-color: var(--band-ground);
  color: var(--color-chalk);
}

.cut-row--link {
  --row-h: 5.2rem;

  grid-template-columns: 2.75rem minmax(0, 1fr) auto 1.5rem;
  padding: 0 calc(var(--cut) + 1rem) 0 calc(var(--cut) + 1.1rem);
  transition: background-color 220ms ease;
}

.cut-row--link::before {
  content: "";
  position: absolute;
  top: 0.9rem;
  bottom: 0.9rem;
  left: calc(var(--cut) * 0.5 + 0.35rem);
  width: 3px;
  background: var(--color-brand);
  transform: skewX(-12deg);
  scale: 1 0;
  transition: scale 320ms var(--ease-out-quint);
}

.cut-row--link:hover,
.cut-row--link:focus-visible {
  background-color: color-mix(in oklab, var(--color-surface) 94%, transparent);
}

.cut-row--link:hover::before,
.cut-row--link:focus-visible::before {
  scale: 1 1;
}

.cut-row__glyph {
  display: grid;
  place-items: center;
  width: 2.75rem;
  height: 2.75rem;
  color: var(--color-eyebrow);
  background: color-mix(in oklab, var(--color-chalk) 5%, transparent);
}

.cut-row__glyph :deep(svg) {
  width: 22px;
  height: 22px;
}

.cut-row__words {
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
  min-width: 0;
}

.cut-row__title {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 0.2rem 0.7rem;
  font-family: var(--font-display);
  font-size: 1.05rem;
  line-height: 1.1;
  text-transform: uppercase;
}

.cut-row--link .cut-row__title {
  font-size: 1.2rem;
}

/* One line at every width: the row's height, and so its cut, is fixed. */
.cut-row__meta {
  overflow: hidden;
  font-size: 0.88rem;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--color-ash);
}

.cut-row__end {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.cut-row__go {
  width: 20px;
  height: 12px;
  color: var(--color-ash);
}

.cut-row--link:hover .cut-row__go {
  color: var(--color-chalk);
}

@media (max-width: 767px) {
  .cut-row {
    --row-h: 4.4rem;

    grid-template-columns: 2.4rem minmax(0, 1fr) auto;
    gap: 0 0.8rem;
    padding: 0 calc(var(--cut) + 0.5rem) 0 calc(var(--cut) + 0.7rem);
  }

  .cut-row--link {
    --row-h: 4.6rem;

    grid-template-columns: 2.4rem minmax(0, 1fr) auto;
  }

  .cut-row__glyph {
    width: 2.4rem;
    height: 2.4rem;
  }

  .cut-row__title,
  .cut-row--link .cut-row__title {
    font-size: 0.95rem;
  }

  .cut-row__meta {
    font-size: 0.8rem;
  }

  .cut-row__go {
    display: none;
  }
}
</style>
