<script lang="ts" setup>
defineOptions({name: "JoinBand"})

/** The invite to where the board answers questions. */
const DISCORD = "https://discord.gg/cauRtRaqh"
const EMAIL = "esports-affairs@blueshell.utwente.nl"
</script>

<template>
  <section
    class="join-band"
    data-testid="esports-join"
  >
    <span
      aria-hidden="true"
      class="join-band__wash"
    />

    <div class="join-band__inner">
      <div class="join-band__say">
        <h2 class="join-band__title">
          Want in?
        </h2>
        <p class="join-band__line">
          Membership is what puts you on a roster. If you would rather ask first, the board
          answers on Discord and esports affairs answers by mail.
        </p>
      </div>

      <div class="join-band__actions">
        <router-link
          class="join-cut join-cut--solid"
          data-testid="esports-join-member"
          to="/membership"
        >
          <span>Become a member</span>
        </router-link>

        <a
          class="join-cut"
          data-testid="esports-join-discord"
          :href="DISCORD"
          rel="noopener"
          target="_blank"
        >
          <span>Ask on Discord</span>
        </a>

        <a
          class="join-cut join-cut--quiet"
          data-testid="esports-join-mail"
          :href="`mailto:${EMAIL}`"
        >
          <span>Ask over email</span>
        </a>
      </div>
    </div>
  </section>
</template>

<style scoped>
/*
 * Full width and shallow, like the strip above it: this is the last band of the page rather
 * than a card sitting on it.
 */
.join-band {
  position: relative;
  isolation: isolate;
  width: 100%;
  overflow: hidden;
  background-color: var(--color-pit);
}

.join-band__wash {
  position: absolute;
  inset: 0;
  background:
    radial-gradient(60rem 18rem at 12% 120%, color-mix(in oklab, var(--color-brand) 26%, transparent), transparent 70%),
    radial-gradient(40rem 14rem at 88% -30%, color-mix(in oklab, var(--color-acid) 10%, transparent), transparent 70%);
  pointer-events: none;
}

.join-band__inner {
  position: relative;
  display: flex;
  width: 100%;
  max-width: 88rem;
  margin: 0 auto;
  align-items: center;
  justify-content: space-between;
  gap: 1.5rem 2.5rem;
  padding: 1.35rem 1.5rem;
  flex-wrap: wrap;
}

.join-band__title {
  font-family: var(--font-display);
  font-size: 1.15rem;
  text-transform: uppercase;
  color: var(--color-chalk);
}

.join-band__line {
  margin-top: 0.3rem;
  max-width: 34rem;
  font-size: 0.8rem;
  line-height: 1.45;
  color: var(--color-ash);
}

.join-band__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.6rem;
}

/*
 * The buttons are cut on the same diagonal as the bands and the slices, so the whole page is
 * put together the same way. The fill arrives from the left on hover rather than switching,
 * which is the one flourish they get.
 */
.join-cut {
  position: relative;
  display: inline-flex;
  align-items: center;
  overflow: hidden;
  padding: 0.62rem 1.35rem;
  clip-path: polygon(0.7rem 0, 100% 0, calc(100% - 0.7rem) 100%, 0 100%);
  background-color: color-mix(in oklab, var(--color-chalk) 8%, transparent);
  font-family: var(--font-display);
  font-size: 0.72rem;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--color-chalk);
  white-space: nowrap;
}

.join-cut::before {
  content: "";
  position: absolute;
  inset: 0;
  background-color: var(--color-brand);
  transform-origin: left center;
  scale: 0 1;
  transition: scale 320ms cubic-bezier(0.22, 1, 0.36, 1);
}

.join-cut > span {
  position: relative;
}

.join-cut:hover::before,
.join-cut:focus-visible::before {
  scale: 1 1;
}

.join-cut--solid {
  background-color: var(--color-brand);
  color: var(--color-void);
}

.join-cut--solid::before {
  background-color: var(--color-acid);
}

/* Tinted rather than outlined: an inset border is cut by the clip-path and what survives it
   reads as a line struck through the address. */
.join-cut--quiet {
  background-color: color-mix(in oklab, var(--color-chalk) 4%, transparent);
  color: var(--color-ash);
}

.join-cut--quiet:hover,
.join-cut--quiet:focus-visible {
  color: var(--color-chalk);
}

@media (max-width: 767px) {
  .join-band__inner {
    padding: 1.15rem 1.15rem 1.35rem;
  }

  .join-cut {
    font-size: 0.68rem;
    padding: 0.55rem 1rem;
  }
}

@media (prefers-reduced-motion: reduce) {
  .join-cut::before {
    transition: none;
  }
}
</style>
