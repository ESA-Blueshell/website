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
  /* No gap at the end of the run: the last slice meets the line, so what a reader sees is a
     mark gathering into a rule rather than a mark and then a rule. */
  gap: 0;
  width: 100%;
  height: var(--rule);
  margin-block: calc(var(--rule) / -2);
  /* Flush at both ends: the leftmost slice is cut on the lean, so run off the left edge it
     reads as a mark carrying on past the page rather than one that starts a gap in. */
  padding-inline: 0;
}

/*
 * The run of leaning slices the line grows out of, drawn as one element.
 *
 * The lean is the buttons' and the chips', which are all cut on the same slant, so the mark
 * belongs to the page rather than to this rule. Six slices rather than a repeat, each wider
 * than the last and each closer to the next: a repeat is a pattern and reads as a texture,
 * where a run that builds reads as the line gathering itself. It ends against the line rather
 * than a gap away from it, so what a reader sees is one mark becoming a rule.
 */
.band-rule__ticks {
  flex: 0 0 auto;
  width: 4.5rem;
  height: 0.62rem;
  background-image: linear-gradient(
    112deg,
    var(--accent, var(--color-brand)) 0 7px,
    transparent 7px 12px,
    var(--accent, var(--color-brand)) 12px 17px,
    transparent 17px 21px,
    var(--accent, var(--color-brand)) 21px 28px,
    transparent 28px 32px,
    var(--accent, var(--color-brand)) 32px 42px,
    transparent 42px 45px,
    var(--accent, var(--color-brand)) 45px 57px,
    transparent 57px 59px,
    /* The last one runs to the edge rather than stopping at a width of its own: the lean means
       a stop short of it leaves a notch at the line's height, and this one hands over. */
    var(--accent, var(--color-brand)) 59px 100%
  );
}

/* It runs the width of the seam and thins the whole way as it goes, so it reads as a line
   drawn from the mark and let go of rather than one that stops. */
.band-rule__line {
  flex: 1 1 auto;
  height: 3px;
  background-image: linear-gradient(
    to right,
    var(--accent, var(--color-brand)) 0,
    var(--accent, var(--color-brand)) 14%,
    color-mix(in oklab, var(--accent, var(--color-brand)) 74%, transparent) 42%,
    color-mix(in oklab, var(--accent, var(--color-brand)) 44%, transparent) 66%,
    color-mix(in oklab, var(--accent, var(--color-brand)) 18%, transparent) 87%,
    transparent 100%
  );
}
</style>
