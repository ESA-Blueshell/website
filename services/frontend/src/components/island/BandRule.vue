<script lang="ts" setup>
/**
 * A rule laid across the seam between two bands, in the accent of whatever it divides.
 *
 * Two photographic bands meeting at a fade read as one picture bleeding into another, however
 * far the fade is drawn back. A rule drawn on the seam, with a mark at one end, is a join a
 * reader can see: the bands stop being one thing without either of them having to be dimmed
 * for it.
 *
 * It takes no room of its own. Half of it lies over the band above and half over the band
 * below, which is what a rule drawn on a seam does. Given room it was a third band, and the
 * page had three things in it where it has two and a join; filled, it was a bar of colour
 * doing the same work worse.
 *
 * The mark is the page's own: leaning ticks, which is the shape the buttons and the chips are
 * cut to and the shape the accent dash above a name already draws, and the rule running off to
 * the right of them. Decorative, so it is hidden from a reader who is being told the structure
 * some other way.
 */
defineOptions({name: "BandRule"})

withDefaults(defineProps<{testid?: string}>(), {testid: "band-rule"})
</script>

<template>
  <div
    aria-hidden="true"
    class="band-rule"
    :data-testid="testid"
  >
    <span class="band-rule__ticks" />
    <span class="band-rule__line" />
  </div>
</template>

<style scoped>
.band-rule {
  /* Its whole height, which is the mark's: half above the seam and half below it. */
  --rule: 0.85rem;

  position: relative;
  z-index: 4;
  display: flex;
  align-items: center;
  gap: 0.55rem;
  width: 100%;
  height: var(--rule);
  margin-block: calc(var(--rule) / -2);
  /* Flush with the page: a rule on a seam runs the width of the seam, and an inset at either
     end reads as a bar that fell short rather than as a line drawn across. */
  padding-inline: 0;
}

/*
 * Three leaning ticks, drawn as one element.
 *
 * The lean is the buttons' and the chips', which are all cut on the same slant, so the mark
 * belongs to the page rather than to this rule.
 */
.band-rule__ticks {
  flex: 0 0 auto;
  width: 4.4rem;
  height: 100%;
  background-image: repeating-linear-gradient(
    112deg,
    var(--accent, var(--color-brand)) 0 11px,
    transparent 11px 22px
  );
}

/* It runs nearly the whole way before it goes, so it reads as a line drawn along the seam
   rather than as a dash beside the mark. */
.band-rule__line {
  flex: 1 1 auto;
  height: 3px;
  background-image: linear-gradient(
    to right,
    var(--accent, var(--color-brand)) 0,
    var(--accent, var(--color-brand)) 62%,
    color-mix(in oklab, var(--accent, var(--color-brand)) 58%, transparent) 84%,
    color-mix(in oklab, var(--accent, var(--color-brand)) 22%, transparent) 95%,
    transparent 100%
  );
}
</style>
