<script lang="ts" setup>
import {computed} from "vue"
import {type Picture, srcsetOf} from "./pictures"

/**
 * The banner band heading one record's own page, such as a game's or a committee's: the way back
 * to its index, what kind of record it is, its name and what it says about itself, a row of facts
 * and the buttons the viewer may press. The banner is square as every banner is and never cut;
 * without one the record's letters are drawn on a plate.
 */
defineOptions({name: "RecordHead"})

const {banner = null, icon = null, archived = false} = defineProps<{
  testid: string
  back: {to: string; label: string}
  eyebrow: string
  title: string
  accent: string
  initials: string
  banner?: Picture | null
  icon?: string | null
  archived?: boolean
}>()

const bannerSrcset = computed(() => srcsetOf(banner))
</script>

<template>
  <div class="record-head__crumb-row">
    <router-link
      class="record-head__crumb"
      :data-testid="`${testid}-back`"
      :to="back.to"
    >
      <svg
        aria-hidden="true"
        fill="none"
        viewBox="0 0 20 12"
      ><path
        d="M20 6H3M7 1.5 1.5 6 7 10.5"
        stroke="currentColor"
        stroke-width="1.4"
      /></svg>
      {{ back.label }}
    </router-link>
  </div>

  <section
    class="record-head"
    :data-testid="`${testid}-head`"
    :style="{'--accent': accent}"
  >
    <div class="record-head__body">
      <div class="record-head__label">
        <p class="record-head__eyebrow">
          {{ eyebrow }}
        </p>
        <span
          v-if="archived"
          class="record-head__tag"
          :data-testid="`${testid}-archived`"
        >Archived</span>
      </div>
      <h1 class="record-head__title">
        <img
          v-if="icon"
          alt=""
          :src="icon"
        >{{ title }}
      </h1>
      <div
        v-if="$slots.default"
        class="record-head__intro"
      >
        <slot />
      </div>
      <div
        v-if="$slots.facts"
        class="record-head__facts"
      >
        <slot name="facts" />
      </div>
      <div
        v-if="$slots.acts"
        class="record-head__acts"
      >
        <slot name="acts" />
      </div>
    </div>
    <div class="record-head__art">
      <img
        v-if="banner"
        alt=""
        sizes="(min-width: 768px) 36rem, 100vw"
        :src="banner.url"
        :srcset="bannerSrcset"
      >
      <span
        v-else
        aria-hidden="true"
        class="record-head__plate"
      ><span>{{ initials }}</span></span>
    </div>
  </section>
</template>

<style scoped>
.record-head__crumb-row {
  width: 100%;
  max-width: 72rem;
  margin: 0 auto;
  padding: 0 2rem;
}

.record-head__crumb {
  display: inline-flex;
  gap: 0.6rem;
  align-items: center;
  padding: 1.1rem 0;
  font-size: 0.85rem;
  letter-spacing: 0.04em;
  color: var(--color-ash);
  text-decoration: none;
}

.record-head__crumb:hover {
  color: var(--color-chalk);
}

.record-head__crumb svg {
  width: 18px;
  height: 11px;
}

.record-head {
  position: relative;
  isolation: isolate;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 36rem;
  min-height: 26rem;
  background-color: var(--band-ground);
}

.record-head::before {
  content: "";
  position: absolute;
  inset: 0;
  z-index: -1;
  pointer-events: none;
  background: radial-gradient(70% 120% at 0 0, color-mix(in oklab, var(--accent) 10%, transparent) 0%, transparent 62%);
}

.record-head__body {
  display: flex;
  flex-direction: column;
  gap: 1.2rem;
  justify-content: center;
  min-width: 0;
  padding: 2.5rem 3rem 2.75rem max(2rem, calc((100cqw - 72rem) / 2 + 2rem));
}

.record-head__label {
  display: flex;
  flex-wrap: wrap;
  gap: 0.8rem;
  align-items: center;
}

.record-head__eyebrow {
  font-size: 11px;
  font-weight: 500;
  letter-spacing: 0.3em;
  text-transform: uppercase;
  color: var(--color-eyebrow);
}

.record-head__tag {
  padding: 0.26rem 0.55rem;
  font-size: 0.68rem;
  font-weight: 600;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: var(--color-ash);
  border: 1px solid currentcolor;
}

.record-head__title {
  display: flex;
  gap: 1rem;
  align-items: center;
  font-family: var(--font-display);
  font-size: 4rem;
  line-height: 0.95;
  text-transform: uppercase;
  color: var(--color-chalk);
}

.record-head__title img {
  width: 3.6rem;
  height: 3.6rem;
  object-fit: contain;
}

.record-head__intro {
  max-width: 38rem;
  font-size: 1.05rem;
  line-height: 1.6;
  color: color-mix(in oklab, var(--color-chalk) 86%, transparent);
}

.record-head__facts {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  row-gap: 1.2rem;
}

.record-head__acts {
  display: flex;
  flex-wrap: wrap;
  gap: 0.6rem;
  align-items: center;
}

.record-head__art {
  position: relative;
  overflow: hidden;
  background-color: var(--color-surface);
}

.record-head__art img {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.record-head__plate {
  position: absolute;
  inset: 0;
  overflow: hidden;
  background:
    radial-gradient(110% 90% at 0 0, color-mix(in oklab, var(--accent) 30%, transparent), transparent 70%),
    repeating-linear-gradient(-62deg, transparent 0 22px, color-mix(in oklab, var(--accent) 7%, transparent) 22px 24px),
    var(--color-pit);
}

.record-head__plate > span {
  position: absolute;
  top: -3rem;
  right: -0.4rem;
  font-family: var(--font-display);
  font-size: 16rem;
  line-height: 1;
  color: color-mix(in oklab, var(--accent) 22%, transparent);
}

@media (max-width: 639px) {
  .record-head__crumb-row {
    padding: 0 1.25rem;
  }

  .record-head {
    grid-template-columns: 1fr;
    min-height: 0;
  }

  .record-head__art {
    order: -1;
    aspect-ratio: 16 / 9;
  }

  .record-head__plate > span {
    top: -1.5rem;
    font-size: 9rem;
  }

  .record-head__body {
    padding: 1.4rem 1.25rem 1.8rem;
  }

  .record-head__title {
    gap: 0.7rem;
    font-size: 2.3rem;
  }

  .record-head__title img {
    width: 2.4rem;
    height: 2.4rem;
  }

  .record-head__facts {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
