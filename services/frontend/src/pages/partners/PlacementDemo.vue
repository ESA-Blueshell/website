<script setup lang="ts">
import Island from "@/components/island/Island.vue"
import HeaderBand from "@/components/island/HeaderBand.vue"
import BandRule from "@/components/island/BandRule.vue"
import PlacementBand from "@/domains/association/island/PlacementBand.vue"
import PlacementBandOriginal from "@/domains/association/island/PlacementBandOriginal.vue"

/**
 * The two ways of showing a partner where their name ends up, on one page.
 *
 * Here to be looked at and decided between, not to be linked to. Whichever wins stays on the
 * become-a-partner page and this goes.
 */
</script>

<template>
  <v-main>
    <island testid="placement-demo">
      <header-band
        body="Two ways of saying the same thing. The first crops the association's own printed
              pieces to the corner a sponsor's logo sits in and points at it in markup. The
              second ships the pieces exactly as they were designed."
        eyebrow="Demo, not a real page"
        heading="Where your name goes,"
        heading-tail="drawn two ways"
      />

      <div class="demo-label">
        <p class="font-display">
          A · rebuilt from photographs
        </p>
        <p class="font-body">
          The words are markup, so they can be read out, translated and searched. Each picture is
          a crop of a real flyer, newsletter or poster.
        </p>
      </div>
      <placement-band testid="demo-rebuilt" />

      <band-rule />

      <div class="demo-label">
        <p class="font-display">
          B · the artwork as it was made
        </p>
        <p class="font-body">
          The design already exists and this ships it. The words are inside the picture, so they
          do not scale with the reader's type size and a screen reader gets only the alt text.
        </p>
      </div>
      <placement-band-original testid="demo-original" />
    </island>
  </v-main>
</template>

<style scoped>
.demo-label {
  margin-inline: auto;
  width: 100%;
  max-width: 72rem;
  padding: 2rem 1.25rem 0;
}

.demo-label p:first-child {
  font-size: 1.1rem;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--color-eyebrow);
}

.demo-label p:last-child {
  margin-top: 0.4rem;
  max-width: 44rem;
  font-size: 0.85rem;
  line-height: 1.5;
  color: var(--color-ash);
}

@media (min-width: 640px) {
  .demo-label {
    padding-inline: 2rem;
  }
}
</style>
