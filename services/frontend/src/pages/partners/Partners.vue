<script setup lang="ts">
import Island from "@/components/island/Island.vue"
import HeaderBand from "@/components/island/HeaderBand.vue"
import BandRule from "@/components/island/BandRule.vue"
import CallBand from "@/components/island/CallBand.vue"
import SliceBand from "@/components/island/SliceBand.vue"
import HeroBand from "@/domains/association/island/HeroBand.vue"
import NumberBand from "@/domains/association/island/NumberBand.vue"
import ReachChart, {type Field} from "@/domains/association/island/ReachChart.vue"
import {useAssociationNumbers} from "@/domains/association/island/useAssociationNumbers"
import {useEventsOnShow} from "@/domains/association/island/useEventsOnShow"
import heroPhoto from "@/assets/association/busy-gamenight.webp"
import dslLogo from "@/assets/association/dsl-logo.webp"
import elnino from "@/assets/elnino.png"
import maatwerk from "@/assets/marketing_maatwerk_logo_big.png"
import talentitLight from "@/assets/talentit.png"
import talentitDark from "@/assets/talentitdark.png"
import connectworksLight from "@/assets/connectworks.png"
import connectworksDark from "@/assets/connectworksdark.png"

const EXTERNAL_AFFAIRS = "external-affairs@blueshell.utwente.nl"

const {figures} = useAssociationNumbers(["members", "discord", "committees", "teams"])
const {slices: eventSlices} = useEventsOnShow()

/**
 * What our members study, from the association's 2025 partnership overview.
 *
 * The colours are the printed ones, held here rather than taken from the theme: a reader
 * comparing this page to the flyer should see the same chart.
 */
const FIELDS: Field[] = [
  {label: "Computer Science & IT", percent: 28.8, colour: "#3387fa"},
  {label: "Engineering & Technology", percent: 27.4, colour: "#1b3faa"},
  {label: "Business & Management", percent: 19.2, colour: "#e8483c"},
  {label: "Social Sciences", percent: 12.3, colour: "#f5893c"},
  {label: "Biomedical & Health Sciences", percent: 9.6, colour: "#f2d04b"},
  {label: "Communication & Media", percent: 2.7, colour: "#2fa84f"},
]

/** What a partnership offers, in the order the association prints it. */
const OFFERS = [
  {title: "Access to our members", body: "Several hundred students who are hard to reach any other way."},
  {title: "Representatives at our events", body: "Come and stand in the room, not in an inbox."},
  {title: "Social media promotion", body: "To the channels our members actually read."},
  {title: "Promotional material at our events", body: "Your flyers in the hands of people at the table."},
  {title: "Logos on our merch and posters", body: "Worn and pinned up all year, on and off campus."},
  {title: "Pages on our website", body: "A page of your own here, like the partners below have."},
  {title: "Direct referrals of students", body: "For an internship, a final assignment or a first job."},
]

/**
 * Where a partner's own artwork ends up.
 *
 * Drawn as frames rather than shipped as the printed mock-ups: a mock-up is a picture of a
 * logo that is not yours, and a frame with your name on it says the same thing without asking
 * anybody to imagine the swap.
 */
const PLACES = [
  {label: "On our flyers", note: "Handed out at every event we run"},
  {label: "On our posters", note: "Up around the campus and in the lounge"},
  {label: "In our newsletter", note: "Sent to every member, every month"},
  {label: "On our jerseys", note: "Worn by the teams that play under our name"},
]

const PARTNERS = [
  {name: "El Niño", href: "/partners/el-nino", light: elnino, dark: elnino},
  {name: "Marketing Maatwerk", href: "/partners/marketing-maatwerk", light: maatwerk, dark: maatwerk},
  {name: "Talent IT", href: null, light: talentitLight, dark: talentitDark},
  {name: "Connectworks", href: null, light: connectworksLight, dark: connectworksDark},
]

const TALK = {
  eyebrow: "Contact us today",
  headline: "Let's talk about what you need",
  body:
    "Tell our commissioner of External Affairs what you are trying to reach, and we will put "
    + "together something that works for both of us.",
  actions: [
    {
      label: EXTERNAL_AFFAIRS,
      href: `mailto:${EXTERNAL_AFFAIRS}`,
      tone: "solid" as const,
      away: true,
      testid: "partners-contact",
    },
  ],
}
</script>

<template>
  <v-main>
    <island testid="partners-island">
      <hero-band
        alt="A Blueshell event, every seat at the table taken"
        eyebrow="Become a partner"
        headline=""
        :photo="heroPhoto"
        testid="partners-hero"
      >
        <template #headline>
          Blueshell wants you<br>
          <span class="text-brand">to be our partner</span>
        </template>
      </hero-band>

      <div class="mx-auto w-full max-w-6xl px-5 pt-10 sm:px-8">
        <p class="font-body text-[11px] font-medium tracking-[0.3em] text-eyebrow uppercase">
          Who you would be reaching
        </p>
      </div>
      <number-band
        :figures="figures"
        testid="partners-numbers"
      />

      <header-band>
        <template #head>
          <p class="font-body text-[11px] font-medium tracking-[0.3em] text-eyebrow uppercase">
            Broad reach
          </p>
          <h2 class="mt-2.5 max-w-2xl font-display text-2xl leading-[1.1] uppercase sm:text-4xl">
            Gaming is one interest<br>
            <span class="text-brand">shared across every faculty</span>
          </h2>
          <div class="mt-6 grid gap-8 md:grid-cols-2">
            <p class="font-body text-sm leading-relaxed text-ash sm:text-base">
              Because gaming cuts across everything students study, our membership does too. A
              large part of it sits exactly where demand is hardest to meet — software development
              and engineering — and you can reach those students here directly.
              <br><br>
              We also run events together with study associations, which carries your name past
              our own membership and into the University of Twente and Saxion as a whole.
            </p>
            <reach-chart
              :fields="FIELDS"
              testid="partners-reach"
            />
          </div>
        </template>
      </header-band>

      <band-rule />

      <section
        class="offers w-full"
        data-testid="partners-offers"
      >
        <div class="mx-auto w-full max-w-6xl px-5 py-12 sm:px-8">
          <p class="font-body text-[11px] font-medium tracking-[0.3em] text-eyebrow uppercase">
            What we offer
          </p>
          <h2 class="mt-2.5 font-display text-2xl uppercase sm:text-4xl">
            Everything on the table
          </h2>
          <ol class="offers__list mt-7">
            <li
              v-for="(offer, index) in OFFERS"
              :key="offer.title"
              class="offers__offer"
            >
              <span
                aria-hidden="true"
                class="offers__number"
              >{{ index + 1 }}</span>
              <h3 class="font-display text-base uppercase">
                {{ offer.title }}
              </h3>
              <p class="mt-1 font-body text-sm leading-relaxed text-ash">
                {{ offer.body }}
              </p>
            </li>
          </ol>
        </div>
      </section>

      <section
        class="places w-full"
        data-testid="partners-places"
      >
        <div class="mx-auto w-full max-w-6xl px-5 py-12 sm:px-8">
          <p class="font-body text-[11px] font-medium tracking-[0.3em] text-eyebrow uppercase">
            Where your name goes
          </p>
          <h2 class="mt-2.5 font-display text-2xl uppercase sm:text-4xl">
            Your logo here
          </h2>
          <ul class="places__grid mt-7">
            <li
              v-for="place in PLACES"
              :key="place.label"
              class="places__place"
            >
              <div class="places__frame">
                <span class="places__slot">Your logo</span>
              </div>
              <h3 class="mt-3 font-display text-sm uppercase">
                {{ place.label }}
              </h3>
              <p class="mt-1 font-body text-xs leading-snug text-ash">
                {{ place.note }}
              </p>
            </li>
          </ul>
        </div>
      </section>

      <band-rule mirrored />

      <header-band>
        <template #head>
          <div class="flex flex-wrap items-center gap-6">
            <img
              alt="Dutch Student League"
              class="dsl-logo"
              :src="dslLogo"
            >
            <div>
              <p class="font-body text-[11px] font-medium tracking-[0.3em] text-eyebrow uppercase">
                And we win things
              </p>
              <h2 class="mt-2.5 max-w-2xl font-display text-2xl leading-[1.1] uppercase sm:text-4xl">
                Three-time champions<br>
                <span class="text-brand">of the Dutch Student League</span>
              </h2>
            </div>
          </div>
          <p class="mt-4 max-w-2xl font-body text-sm leading-relaxed text-ash">
            We are also the second-largest association in the Dutch College Esports Series, where
            our teams play Valorant, League of Legends, Rocket League and Counter-Strike 2 under
            our name — and, if you would like, under yours.
          </p>
        </template>
      </header-band>

      <section
        v-if="eventSlices.length > 0"
        class="w-full"
        data-testid="partners-events"
      >
        <div class="mx-auto w-full max-w-6xl px-5 pt-10 pb-6 sm:px-8">
          <p class="font-body text-[11px] font-medium tracking-[0.3em] text-eyebrow uppercase">
            Where you would appear
          </p>
          <h2 class="mt-2.5 font-display text-2xl uppercase sm:text-4xl">
            Events we ran lately
          </h2>
        </div>
        <slice-band
          accent="var(--color-brand)"
          :items="eventSlices"
          layout="aside"
          testid-prefix="partners-event"
        />
      </section>

      <section
        class="wall w-full"
        data-testid="partners-wall"
      >
        <div class="mx-auto w-full max-w-6xl px-5 py-12 sm:px-8">
          <p class="font-body text-[11px] font-medium tracking-[0.3em] text-eyebrow uppercase">
            In good company
          </p>
          <h2 class="mt-2.5 font-display text-2xl uppercase sm:text-4xl">
            Who we already work with
          </h2>
          <ul class="wall__grid mt-7">
            <li
              v-for="partner in PARTNERS"
              :key="partner.name"
              class="wall__partner"
            >
              <component
                :is="partner.href ? 'router-link' : 'div'"
                :to="partner.href ?? undefined"
              >
                <img
                  :alt="partner.name"
                  class="wall__logo wall__logo--light"
                  :src="partner.light"
                >
                <img
                  :alt="partner.name"
                  class="wall__logo wall__logo--dark"
                  :src="partner.dark"
                >
              </component>
            </li>
          </ul>
        </div>
      </section>

      <call-band
        :actions="TALK.actions"
        :body="TALK.body"
        :eyebrow="TALK.eyebrow"
        :headline="TALK.headline"
        testid="partners-call"
      />
    </island>
  </v-main>
</template>

<style scoped>
.offers,
.wall {
  background: var(--band-ground);
}

.offers__list {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(15rem, 1fr));
  gap: 1.75rem 2rem;
  list-style: none;
  counter-reset: offer;
}

.offers__offer {
  border-left: 2px solid var(--color-brand);
  padding-left: 0.9rem;
}

.offers__number {
  display: block;
  font-family: var(--font-display);
  font-size: 0.8rem;
  letter-spacing: 0.18em;
  color: var(--color-eyebrow);
}

.places__grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(11rem, 1fr));
  gap: 1.5rem;
  list-style: none;
}

/*
 * An empty frame with your name in it, cut on the slant every other band is cut on. Dashed,
 * so it reads as a space held open rather than as a thing that failed to load.
 */
.places__frame {
  display: grid;
  place-items: center;
  aspect-ratio: 4 / 3;
  border: 2px dashed var(--color-hairline);
  background: var(--color-surface);
  clip-path: polygon(14px 0, 100% 0, calc(100% - 14px) 100%, 0 100%);
}

.places__slot {
  font-family: var(--font-display);
  font-size: 0.85rem;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: var(--color-ash);
}

.dsl-logo {
  height: 4.5rem;
  width: auto;
  object-fit: contain;
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

/* Each logo has the variant its ground needs; the theme decides which one is drawn. */
.wall__logo--dark {
  display: none;
}

:where([data-theme="dark"]) .wall__logo--light {
  display: none;
}

:where([data-theme="dark"]) .wall__logo--dark {
  display: block;
}

@media (max-width: 767px) {
  .dsl-logo {
    height: 3rem;
  }
}
</style>
