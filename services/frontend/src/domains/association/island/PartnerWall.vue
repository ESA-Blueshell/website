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
 * The partners as bare logos centred as one group, each one a way to its page where it has one.
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

/* One group in the middle rather than a grid spread to the edges: three logos read as the
   partners together, and each pair is parted by the lean the buttons are cut on. */
/* One row that shrinks rather than wraps: a wrapped row would start on a rule. */
.wall__grid {
  display: flex;
  align-items: center;
  justify-content: center;
  row-gap: 2rem;
  list-style: none;
}

.wall__partner {
  position: relative;
  flex: 0 1 17rem;
  min-width: 0;
  padding: 0 2.25rem;
  text-align: center;
}

.wall__partner > * {
  display: inline-block;
  max-width: 100%;
}

.wall__partner + .wall__partner::before {
  position: absolute;
  top: 0.4rem;
  bottom: 0.4rem;
  left: 0;
  width: 1px;
  content: "";
  background-color: var(--color-hairline);
  transform: skewX(-12deg);
}

.wall__logo {
  height: 4.75rem;
  width: auto;
  max-width: 100%;
  object-fit: contain;
}

/* A phone stacks them, and a rule between stacked logos parts nothing. */
@media (max-width: 639px) {
  .wall__grid {
    flex-direction: column;
  }

  .wall__partner {
    flex-basis: auto;
    padding: 0;
  }

  .wall__partner + .wall__partner::before {
    display: none;
  }

  .wall__logo {
    height: 3.75rem;
  }
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
