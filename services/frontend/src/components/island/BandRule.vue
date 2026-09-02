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

withDefaults(defineProps<{
  /**
   * Whether the rule runs the other way: dashes at the right, the line off to the left.
   *
   * A band closed with the same mark it was opened with reads as the same rule drawn twice.
   * Mirrored, the two hold the band between them. A flip rather than a second set of
   * gradients, since there is nothing in here that reads.
   */
  mirrored?: boolean
  testid?: string
}>(), {mirrored: false, testid: "band-rule"})
</script>

<template>
  <div
    aria-hidden="true"
    class="band-rule"
    :class="{'band-rule--mirrored': mirrored}"
    :data-testid="testid"
  >
    <span class="band-rule__ticks" />
    <span class="band-rule__line" />
  </div>
</template>

<style scoped>
.band-rule {
  /* Its whole height, which is the line's: half above the seam and half below it. The mark is
     the same weight, so what runs along the seam is one line, dashed at its start. */
  --rule: 3px;

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

/* The same rule the other way about, so a band closed with one is held between two marks
   rather than reading as the same rule drawn twice. */
.band-rule--mirrored {
  transform: scaleX(-1);
}

/*
 * The dashes the line grows out of, drawn as one element.
 *
 * The lean is the buttons' and the chips', which are all cut on the same slant, so the mark
 * belongs to the page rather than to this rule. Eight of them rather than a repeat, each a
 * little longer than the last and each closer to the next: a repeat is a pattern and reads as
 * a texture, where a run that builds reads as the line gathering itself. The same weight as
 * the line, so it is one line dashed at its start rather than a mark beside a rule, and the
 * last dash runs to the edge and hands straight over.
 */
.band-rule__ticks {
  flex: 0 0 auto;
  width: 4.9rem;
  height: 100%;
  background-image: linear-gradient(
    112deg,
    var(--accent, var(--color-brand)) 0 3px,
    transparent 3px 8px,
    var(--accent, var(--color-brand)) 8px 12px,
    transparent 12px 17px,
    var(--accent, var(--color-brand)) 17px 22px,
    transparent 22px 26px,
    var(--accent, var(--color-brand)) 26px 32px,
    transparent 32px 36px,
    var(--accent, var(--color-brand)) 36px 43px,
    transparent 43px 46px,
    var(--accent, var(--color-brand)) 46px 54px,
    transparent 54px 57px,
    var(--accent, var(--color-brand)) 57px 66px,
    transparent 66px 68px,
    var(--accent, var(--color-brand)) 68px 100%
  );
}

/* It runs the width of the seam and thins the whole way as it goes, so it reads as a line
   drawn from the mark and let go of rather than one that stops. */
.band-rule__line {
  flex: 1 1 auto;
  height: 100%;
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
