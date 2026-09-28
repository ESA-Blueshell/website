<script setup lang="ts">
import BandRule from "@/components/island/BandRule.vue"
import CutButton from "@/components/island/CutButton.vue"
import HeaderBand from "@/components/island/HeaderBand.vue"
import Island from "@/components/island/Island.vue"
import type {PartnerContent} from "../partnerPages"

/**
 * One partner's page, drawn from its content: the logo leading to their site, where to find
 * them, what they say about themselves and what they offer, and the ways in.
 */
const {content} = defineProps<{content: PartnerContent}>()
</script>

<template>
  <island testid="partner-island">
    <header-band
      eyebrow="Our partner"
      :heading="content.name"
      :heading-tail="content.tagline ?? ''"
    >
      <template #acts>
        <cut-button
          v-for="(action, index) in content.actions"
          :key="action.href"
          away
          :href="action.href"
          :tone="index === 0 ? 'solid' : 'plain'"
        >
          {{ action.label }}
        </cut-button>
      </template>
    </header-band>

    <div class="partner mx-auto w-full max-w-6xl px-5 pb-12 sm:px-8">
      <div class="partner__head">
        <a
          :aria-label="`Visit ${content.name}`"
          class="partner__plate"
          :href="content.site"
          rel="noopener"
          target="_blank"
        >
          <img
            :alt="content.logo.alt"
            class="partner__logo"
            :class="{'partner__logo--inverted': content.logo.invertInDark}"
            :src="content.logo.src"
          >
        </a>
        <div>
          <h2 class="font-display text-base uppercase">
            {{ content.facts.heading }}
          </h2>
          <dl class="partner__facts mt-3">
            <template
              v-for="fact in content.facts.items"
              :key="fact.label"
            >
              <dt class="font-body text-[11px] font-medium tracking-[0.3em] text-eyebrow uppercase">
                {{ fact.label }}
              </dt>
              <dd class="font-body text-sm">
                <a
                  v-if="fact.href"
                  class="partner__link"
                  :href="fact.href"
                  rel="noopener"
                  :target="fact.href.startsWith('http') ? '_blank' : undefined"
                >{{ fact.value }}</a>
                <template v-else>
                  {{ fact.value }}
                </template>
              </dd>
            </template>
          </dl>
        </div>
      </div>

      <band-rule />

      <section
        v-for="section in content.sections"
        :key="section.heading"
        class="partner__section"
      >
        <h2 class="font-display text-xl uppercase sm:text-2xl">
          {{ section.heading }}
        </h2>
        <p
          v-for="paragraph in section.paragraphs"
          :key="paragraph"
          class="mt-3 max-w-3xl font-body text-sm leading-relaxed text-ash sm:text-base"
        >
          {{ paragraph }}
        </p>
        <ul
          v-if="section.offers"
          class="partner__offers mt-6"
        >
          <li
            v-for="offer in section.offers"
            :key="offer.href"
            class="partner__offer"
          >
            <a
              class="partner__link font-display text-base uppercase"
              :href="offer.href"
              rel="noopener"
              target="_blank"
            >{{ offer.title }}</a>
            <p class="mt-1 font-body text-sm leading-relaxed text-ash">
              {{ offer.body }}
            </p>
          </li>
        </ul>
      </section>
    </div>
  </island>
</template>

<style scoped>
.partner__head {
  display: grid;
  grid-template-columns: minmax(0, 26rem) minmax(0, 1fr);
  gap: 2rem 3rem;
  align-items: center;
  padding-block: 2rem;
}

.partner__plate {
  display: block;
}

.partner__logo {
  display: block;
  width: 100%;
  height: auto;
}

/* The artwork is dark ink on nothing, so dark turns it light, as the partner wall does. */
:where([data-theme="dark"]) .partner__logo--inverted {
  filter: invert(1) hue-rotate(180deg);
}

.partner__facts {
  display: grid;
  grid-template-columns: max-content minmax(0, 1fr);
  gap: 0.6rem 1.25rem;
  align-items: baseline;
}

.partner__link {
  color: var(--color-brand-ink);
  text-decoration: none;
}

.partner__link:hover,
.partner__link:focus-visible {
  text-decoration: underline;
}

.partner__section {
  padding-top: 2.5rem;
}

.partner__offers {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(15rem, 1fr));
  gap: 1.75rem 2rem;
  list-style: none;
}

.partner__offer {
  border-left: 2px solid var(--color-brand);
  padding-left: 0.9rem;
}

@media (max-width: 767px) {
  .partner__head {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
