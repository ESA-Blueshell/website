<script lang="ts" setup>
import type {FeeQuote} from "../fees"

/**
 * What membership costs, quoted from the contribution period the association is charging for.
 *
 * The amounts are never invented: until the read lands, and where no period has been recorded,
 * the band says the fees are not listed here yet rather than showing a price. The note under
 * them is the association's own rule that fees are set at the General Members Meeting.
 */
defineOptions({name: "FeeBand"})

withDefaults(defineProps<{
  /** The fees for the year, or nothing while the read is out or where none is recorded. */
  quote?: FeeQuote | null
  heading: string
  /** What holds whatever the year costs: the term, and the card nobody needs. */
  terms: string[]
  testid?: string
}>(), {quote: null, testid: "membership-fees"})
</script>

<template>
  <section
    class="fee-band"
    :data-testid="testid"
  >
    <div class="mx-auto w-full max-w-6xl px-5 py-9 sm:px-8 md:py-12">
      <div class="flex flex-wrap items-baseline gap-x-4 gap-y-1">
        <h2 class="fee-band__heading">
          {{ heading }}
        </h2>
        <p
          v-if="quote"
          class="fee-band__year"
          :data-testid="`${testid}-year`"
        >
          {{ quote.year }}
        </p>
      </div>

      <ul
        v-if="quote"
        class="mt-6 grid gap-x-8 gap-y-6 sm:grid-cols-3"
      >
        <li
          v-for="fee in quote.fees"
          :key="fee.id"
          class="fee-band__fee"
          :data-testid="`${testid}-${fee.id}`"
        >
          <p
            class="fee-band__amount"
            :data-testid="`${testid}-${fee.id}-amount`"
          >
            {{ fee.amount }}
          </p>
          <p class="fee-band__label">
            {{ fee.label }}
          </p>
          <p class="fee-band__note">
            {{ fee.note }}
          </p>
        </li>
      </ul>

      <p
        v-else
        class="fee-band__unlisted"
        :data-testid="`${testid}-unlisted`"
      >
        This year's fees are not listed here yet. The board can tell you what they are.
      </p>

      <p
        v-if="quote"
        class="fee-band__change"
        :data-testid="`${testid}-note`"
      >
        {{ quote.note }}
      </p>

      <ul class="fee-band__terms">
        <li
          v-for="term in terms"
          :key="term"
        >
          {{ term }}
        </li>
      </ul>
    </div>
  </section>
</template>

<style scoped>
/* A band, on the shared band ground: see island.css. */
.fee-band {
  width: 100%;
  background-color: var(--band-ground);
}

.fee-band__heading {
  font-family: var(--font-display);
  font-size: clamp(1.35rem, 3.4vw, 2.1rem);
  line-height: 1.1;
  text-transform: uppercase;
  color: var(--color-chalk);
}

.fee-band__year {
  font-family: var(--font-body);
  font-size: 0.95rem;
  letter-spacing: 0.2em;
  color: var(--color-eyebrow);
}

/*
 * A price is cut off the band the way the buttons are, so the three read as stubs torn from
 * one strip rather than as three cards. The lean is the page's, not this band's invention.
 */
.fee-band__fee {
  padding: 1rem 1.25rem 1.15rem;
  background-color: color-mix(in oklab, var(--color-chalk) 6%, transparent);
  clip-path: polygon(0.9rem 0, 100% 0, calc(100% - 0.9rem) 100%, 0 100%);
}

.fee-band__amount {
  font-family: var(--font-display);
  font-size: clamp(1.7rem, 4.4vw, 2.4rem);
  line-height: 1;
  color: var(--color-chalk);
}

.fee-band__label {
  margin-top: 0.5rem;
  font-family: var(--font-display);
  font-size: 0.82rem;
  letter-spacing: 0.05em;
  text-transform: uppercase;
  color: var(--color-chalk);
}

.fee-band__note,
.fee-band__change,
.fee-band__unlisted,
.fee-band__terms {
  font-family: var(--font-body);
  color: var(--color-ash);
}

.fee-band__note {
  margin-top: 0.25rem;
  font-size: 0.78rem;
  line-height: 1.4;
}

.fee-band__unlisted,
.fee-band__change {
  margin-top: 1.25rem;
  max-width: 40rem;
  font-size: 0.85rem;
  line-height: 1.5;
}

.fee-band__terms {
  margin-top: 0.6rem;
  max-width: 40rem;
  font-size: 0.85rem;
  line-height: 1.5;
}
</style>
