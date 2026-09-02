<script lang="ts" setup>
/**
 * A strip of the page between two bands, in the accent of whatever it divides.
 *
 * Two photographic bands meeting at a fade read as one picture bleeding into another, however
 * far the fade is drawn back. A strip between them, with a mark on it, is a join a reader can
 * see: the bands stop being one thing without either of them having to be dimmed for it.
 *
 * Filled with the board's own colour rather than with a tone the page already uses. Given a
 * ground near the bands' own it read as more of the same dark with marks floating in it: what
 * divides two bands has to be a thing of its own. The mark is knocked out of the colour in the
 * near-black both themes keep for ink on a bright fill.
 *
 * The fill also breaks the page's pattern instead of showing it. A repeat that lines up on one
 * side of a join and not the other is the thing the strip is there to hide, and it sits
 * directly on the band below it, with nothing between.
 *
 * The mark is the page's own: leaning ticks, which is the shape the buttons and the chips are
 * cut to and the shape the accent dash above a name already draws, and a rule trailing off to
 * the right. Decorative, so it is hidden from a reader who is being told the structure some
 * other way.
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
  display: flex;
  align-items: center;
  gap: 0.8rem;
  width: 100%;
  padding: 0.95rem clamp(1.25rem, 4vw, 3rem);
  background-color: var(--accent, var(--color-brand));
}

/*
 * Three leaning ticks, drawn as one element.
 *
 * The lean is the buttons' and the chips', which are all cut on the same slant, so the mark
 * belongs to the page rather than to this strip.
 */
.band-rule__ticks {
  flex: 0 0 auto;
  width: 3.9rem;
  height: 0.9rem;
  background-image: repeating-linear-gradient(
    112deg,
    var(--color-void) 0 11px,
    transparent 11px 22px
  );
}

/* Off to the right and gone before the edge, so the strip is a mark on the page and not a
   line ruled across it. */
.band-rule__line {
  flex: 1 1 auto;
  height: 2px;
  background-image: linear-gradient(
    to right,
    var(--color-void) 0,
    var(--color-void) 6%,
    color-mix(in oklab, var(--color-void) 52%, transparent) 38%,
    color-mix(in oklab, var(--color-void) 18%, transparent) 70%,
    transparent 94%
  );
}
</style>
