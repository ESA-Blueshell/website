<script lang="ts">
/** An organisation that backs the association, drawn as its logo for each half of the theme. */
export interface Partner {
  name: string
  /** Its page here, an address elsewhere, or nothing where there is neither. */
  href: string | null
  light: string
  dark: string
  /**
   * Whether the dark half inverts the light logo, for a partner with only dark-ink artwork.
   * The hue is turned back so the brand colours survive the inversion.
   */
  invertInDark?: boolean
}
</script>

<script lang="ts" setup>
import BandHead from "@/components/island/BandHead.vue"

/**
 * The partners as bare logos, each one a way to its page where it has one.
 *
 * Each logo has the variant its ground needs and the theme decides which is drawn, so a logo
 * that is dark ink never lands on a dark page.
 */
defineOptions({name: "PartnerWall"})

withDefaults(defineProps<{
  eyebrow: string
  heading: string
  partners: Partner[]
  testid?: string
}>(), {testid: "partner-wall"})

const inside = (href: string) => href.startsWith("/")
</script>

<template>
  <section
    class="wall w-full"
    :data-testid="testid"
  >
    <div class="mx-auto w-full max-w-6xl px-5 py-12 sm:px-8">
      <band-head
        :eyebrow="eyebrow"
        :heading="heading"
      >
        <slot />
      </band-head>
      <ul class="wall__grid mt-7">
        <li
          v-for="partner in partners"
          :key="partner.name"
          class="wall__partner"
        >
          <component
            :is="partner.href === null ? 'div' : inside(partner.href) ? 'router-link' : 'a'"
            v-bind="partner.href === null ? {} : inside(partner.href) ? {to: partner.href} : {href: partner.href, target: '_blank', rel: 'noopener'}"
            :data-testid="`${testid}-${partner.name}`"
          >
            <img
              :alt="partner.name"
              class="wall__logo wall__logo--light"
              :src="partner.light"
            >
            <img
              :alt="partner.name"
              class="wall__logo wall__logo--dark"
              :class="{'wall__logo--inverted': partner.invertInDark}"
              :src="partner.dark"
            >
          </component>
        </li>
      </ul>
    </div>
  </section>
</template>

<style scoped>
.wall {
  background: var(--band-ground);
}

.wall__grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(10rem, 1fr));
  align-items: center;
  gap: 2rem;
  list-style: none;
}

.wall__logo {
  max-height: 3.5rem;
  width: auto;
  object-fit: contain;
}

.wall__logo--dark {
  display: none;
}

:where([data-theme="dark"]) .wall__logo--light {
  display: none;
}

:where([data-theme="dark"]) .wall__logo--dark {
  display: block;
}

.wall__logo--inverted {
  filter: invert(1) hue-rotate(180deg);
}
</style>
