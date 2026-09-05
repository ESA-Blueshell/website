<script setup lang="ts">
import Island from "@/components/island/Island.vue"
import HeaderBand from "@/components/island/HeaderBand.vue"
import BandRule from "@/components/island/BandRule.vue"
import CallBand from "@/components/island/CallBand.vue"
import EventsBand from "@/domains/association/island/EventsBand.vue"
import HeroBand from "@/domains/association/island/HeroBand.vue"
import HistoryBand from "@/domains/association/island/HistoryBand.vue"
import NumberBand from "@/domains/association/island/NumberBand.vue"
import {MILESTONES} from "@/domains/association/historyAxis"
import {useAssociationNumbers} from "@/domains/association/island/useAssociationNumbers"
import {associationYearOrdinal} from "@/utils/association"
import heroPhoto from "@/assets/association/lan-party.webp"
import inclusivityPhoto from "@/assets/association/karaoke-inclusive.webp"

// Four, because the band draws four across: the two claims that are largest, and the two
// counts that say most about what goes on here.
const {figures} = useAssociationNumbers(["members", "discord", "committees", "boards"])

const yearOrdinal = associationYearOrdinal()


const JOIN = {
  eyebrow: "Everyone is welcome, and we mean it",
  headline: "Come and find your people",
  body:
    "Whether you play ranked until three in the morning or have never finished a game in your "
    + "life, there is room here. Come to an event, or say hello on the Discord first.",
  actions: [
    {label: "Become a member", href: "/membership", tone: "solid" as const, testid: "aboutus-join"},
    {label: "Meet us on Discord", href: "https://discord.gg/23YMFQy", tone: "plain" as const, away: true},
  ],
}
</script>

<template>
  <v-main>
    <island testid="aboutus-island">
      <hero-band
        alt="A Blueshell LAN party, rows of screens and people playing"
        eyebrow="About us"
        headline=""
        :photo="heroPhoto"
        testid="aboutus-hero"
      >
        <template #headline>
          The largest gaming association<br>
          <span class="text-brand">in the Netherlands</span>
        </template>
      </hero-band>

      <!-- The band draws the figures and nothing else, so the line that dates them is the
           page's to write. -->
      <div class="mx-auto w-full max-w-6xl px-5 pt-10 sm:px-8">
        <p class="font-body text-[11px] font-medium tracking-[0.3em] text-eyebrow uppercase">
          Now in our {{ yearOrdinal }} year
        </p>
      </div>
      <number-band
        :figures="figures"
        testid="aboutus-numbers"
      />

      <header-band>
        <template #head>
          <p class="font-body text-[11px] font-medium tracking-[0.3em] text-eyebrow uppercase">
            Where we came from
          </p>
          <h2 class="mt-2.5 max-w-2xl font-display text-2xl leading-[1.1] uppercase sm:text-4xl">
            It started with two students<br>
            <span class="text-brand">and a bad idea</span>
          </h2>
        </template>
      </header-band>

      <!-- No room of its own above or below: the line is a slice of the page, and a slice
           meets the one before it. -->
      <history-band
        :milestones="MILESTONES"
        testid="aboutus-history"
      />

      <band-rule />

      <section class="w-full">
        <div class="mx-auto grid w-full max-w-6xl gap-8 px-5 py-12 sm:px-8 md:grid-cols-2">
          <div>
            <p class="font-body text-[11px] font-medium tracking-[0.3em] text-eyebrow uppercase">
              What we are for
            </p>
            <h2 class="mt-2.5 font-display text-2xl uppercase sm:text-4xl">
              Gaming is for everyone
            </h2>
            <p class="mt-4 font-body text-sm leading-relaxed text-ash sm:text-base">
              Our members come from psychology and from mechanical engineering, from Twente and
              from Saxion, and they are here for the same reason. We fielded the first all-female
              team in the Dutch College Esports Series because a community that says it is for
              everyone has to keep proving it.
            </p>
            <p class="mt-3 font-body text-sm leading-relaxed text-ash sm:text-base">
              Nobody here will ask how good you are.
            </p>
          </div>
          <img
            alt="Blueshell members at a karaoke night"
            class="inclusivity-photo"
            :src="inclusivityPhoto"
          >
        </div>
      </section>

      <section
        class="pillars w-full"
        data-testid="aboutus-pillars"
      >
        <div class="mx-auto w-full max-w-6xl px-5 py-12 sm:px-8">
          <div class="grid gap-8 md:grid-cols-3">
            <div>
              <h3 class="font-display text-lg uppercase">
                A committee for it
              </h3>
              <p class="mt-2 font-body text-sm leading-relaxed text-ash">
                Our committees are the association: Nintendo nights, Minecraft, arts and
                crafts, the trips away. Members run them, members start them, and members put
                on their own nights without one.
              </p>
              <router-link
                class="pillars__link"
                to="/committees"
              >
                See the committees →
              </router-link>
            </div>
            <div>
              <h3 class="font-display text-lg uppercase">
                A Discord that never sleeps
              </h3>
              <p class="mt-2 font-body text-sm leading-relaxed text-ash">
                Over eleven hundred people, with channels for Counter-Strike 2, Valorant, League
                of Legends and most things besides. It is where the games get organised and
                where people stay talking long after the game is over.
              </p>
              <a
                class="pillars__link"
                href="https://discord.gg/23YMFQy"
                rel="noopener"
                target="_blank"
              >Join the Discord →</a>
            </div>
            <div>
              <h3 class="font-display text-lg uppercase">
                A place in the Bastille
              </h3>
              <p class="mt-2 font-body text-sm leading-relaxed text-ash">
                We hold events in the Predator Esports Lounge: ten gaming PCs across two booths,
                a sim-rig, and a bar. Pokémon tournaments, gaming pub quizzes, escape rooms, and
                whatever a committee thought of last week.
              </p>
              <router-link
                class="pillars__link"
                to="/events"
              >
                See what is on →
              </router-link>
            </div>
          </div>
        </div>
      </section>

      <band-rule mirrored />

      <header-band>
        <template #head>
          <p class="font-body text-[11px] font-medium tracking-[0.3em] text-eyebrow uppercase">
            And when we do compete
          </p>
          <h2 class="mt-2.5 max-w-2xl font-display text-2xl leading-[1.1] uppercase sm:text-4xl">
            Three-time champions<br>
            <span class="text-brand">of a league we joined for fun</span>
          </h2>
          <p class="mt-3 max-w-2xl font-body text-sm leading-relaxed text-ash">
            We field teams in the Dutch College Esports Series across Valorant, League of Legends,
            Rocket League and Counter-Strike 2, and we are the second-largest association in it.
            We also won the Dutch Student League three times in a row. Our teams take every level,
            from a casual weeknight match to people who very much intend to win, and we keep a
            friendly relationship with Esports Team Twente, who compete at the top of it.
          </p>
          <p class="mt-4">
            <router-link
              class="pillars__link"
              to="/esports/competitive-scene"
            >
              See the teams →
            </router-link>
          </p>
        </template>
      </header-band>

      <events-band
        eyebrow="Lately"
        heading="What we have been up to"
        testid="aboutus-events"
      />

      <call-band
        :actions="JOIN.actions"
        :body="JOIN.body"
        :eyebrow="JOIN.eyebrow"
        :headline="JOIN.headline"
        testid="aboutus-call"
      />
    </island>
  </v-main>
</template>

<style scoped>
.pillars {
  background: var(--band-ground);
}

.inclusivity-photo {
  width: 100%;
  height: 100%;
  max-height: 22rem;
  object-fit: cover;
  /* The photograph dissolves into the band beside it rather than sitting in a frame, the way
     the board band's does. */
  mask-image: linear-gradient(to left, #000 82%, transparent 100%);
}

.pillars__link {
  display: inline-block;
  margin-top: 0.75rem;
  font-family: var(--font-body);
  font-size: 0.875rem;
  color: var(--color-brand);
}

.pillars__link:hover {
  text-decoration: underline;
}

@media (max-width: 767px) {
  .inclusivity-photo {
    max-height: 14rem;
    mask-image: none;
  }
}
</style>
