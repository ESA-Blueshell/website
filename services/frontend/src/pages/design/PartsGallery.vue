<script lang="ts" setup>
import {DISCORD_INVITE} from "@/components/island/socialGlyphs"
/* The parts the island's bands are built from, on one page and in both halves of the theme.
   Dev only: the route that reaches it is registered only when `import.meta.env.DEV`. */
import {ref} from "vue"
import busy from "@/assets/association/busy-gamenight.webp"
import karaoke from "@/assets/association/karaoke-inclusive.webp"
import lan from "@/assets/association/lan-party.webp"
import BandRule from "@/components/island/BandRule.vue"
import CallBand, {type CallAction} from "@/components/island/CallBand.vue"
import CountBadge from "@/components/island/CountBadge.vue"
import CountryFlag from "@/components/island/CountryFlag.vue"
import CutButton from "@/components/island/CutButton.vue"
import HeaderBand from "@/components/island/HeaderBand.vue"
import LeadBand from "@/components/island/LeadBand.vue"
import ModalDialog from "@/components/island/ModalDialog.vue"
import PageTabs from "@/components/island/PageTabs.vue"
import PanChevron from "@/components/island/PanChevron.vue"
import SegmentedChoice from "@/components/island/SegmentedChoice.vue"
import SliceBand, {type SliceItem} from "@/components/island/SliceBand.vue"

const dark = ref(true)

const choice = ref("upcoming")
const choices = [
  {key: "upcoming", label: "Upcoming"},
  {key: "past", label: "Past events"},
]

const tabs = [
  {label: "Account", to: "/account"},
  {label: "Parts", to: "/design/parts"},
  {label: "Fields", to: "/design/fields"},
]

const panned = ref(0)
const opened = ref(false)

const actions: CallAction[] = [
  {label: "Become a member", href: "/membership", tone: "solid"},
  {label: "Ask us on Discord", href: DISCORD_INVITE, away: true},
]

const slices: SliceItem[] = [
  {id: 1, title: "Game nights", meta: "Every Tuesday", banner: busy},
  {id: 2, title: "Karaoke", meta: "Once a block", banner: karaoke},
  {id: 3, title: "LAN parties", meta: "Twice a year", banner: lan},
]

const flags = ["NL", "DE", "BE", "GB", "FR", "TR"]
</script>

<template>
  <!-- The switch holds both ways whatever the site's theme: dark is pinned on the island, and
       light comes from the theme attribute island.css reads off an ancestor. -->
  <div :data-theme="dark ? 'dark' : 'light'">
    <div
      class="island gallery"
      :class="{'island-dark': dark}"
    >
      <header class="gallery__head">
        <h1 class="gallery__title">
          The island's parts
        </h1>
        <button
          class="gallery__theme"
          type="button"
          @click="dark = !dark"
        >
          {{ dark ? "Read it light" : "Read it dark" }}
        </button>
      </header>

      <section class="gallery__set">
        <h2 class="gallery__what">
          Buttons
        </h2>
        <div class="gallery__row">
          <cut-button tone="solid">
            Solid
          </cut-button>
          <cut-button>Plain</cut-button>
          <cut-button tone="quiet">
            Quiet
          </cut-button>
          <cut-button
            href="/events"
            tone="solid"
          >
            A page
          </cut-button>
          <cut-button
            away
            :href="DISCORD_INVITE"
          >
            Somewhere else
          </cut-button>
        </div>
        <segmented-choice
          v-model="choice"
          :options="choices"
          testid-prefix="gallery-choice"
        />
      </section>

      <section class="gallery__set">
        <h2 class="gallery__what">
          Tabs
        </h2>
        <page-tabs
          :entries="tabs"
          label="The design pages"
        />
      </section>

      <section class="gallery__set">
        <h2 class="gallery__what">
          Counts
        </h2>
        <p class="gallery__heading">
          Also coming up<count-badge
            :count="6"
            said="upcoming events"
          />
        </p>
        <!-- A phone's width: the count follows the heading's last word onto its second line. -->
        <p class="gallery__heading gallery__heading--narrow">
          All upcoming events<count-badge
            :count="14"
            said="upcoming events"
          />
        </p>
      </section>

      <section class="gallery__set">
        <h2 class="gallery__what">
          Chevrons
        </h2>
        <div class="gallery__pan">
          <div
            class="gallery__plates"
            :style="{translate: `${panned * -8}rem 0`}"
          >
            <img
              v-for="(plate, index) in [busy, karaoke, lan, busy, karaoke, lan]"
              :key="index"
              alt=""
              class="gallery__plate"
              :src="plate"
            >
          </div>
          <pan-chevron
            label="Back"
            way="back"
            @pan="panned = Math.max(0, panned - 1)"
          />
          <pan-chevron
            label="On"
            way="on"
            @pan="panned = Math.min(3, panned + 1)"
          />
        </div>
      </section>

      <section class="gallery__set">
        <h2 class="gallery__what">
          Flags
        </h2>
        <div class="gallery__row">
          <country-flag
            v-for="code in flags"
            :key="code"
            :code="code"
            :size="25"
          />
        </div>
      </section>

      <section class="gallery__set">
        <h2 class="gallery__what">
          Dialog
        </h2>
        <cut-button @click="opened = true">
          Open the dialog
        </cut-button>
        <modal-dialog
          :open="opened"
          title="A dialog"
          @update:open="opened = $event"
        >
          <p class="gallery__said">
            What a dialog says, over the page it was opened from.
          </p>
        </modal-dialog>
      </section>

      <section class="gallery__bands">
        <h2 class="gallery__what gallery__what--inset">
          Bands
        </h2>
        <header-band
          body="A heading band, with its blob and its eyebrow."
          eyebrow="Blueshell"
          heading="The association"
          heading-tail="in Enschede"
        />
        <band-rule />
        <lead-band>
          <p class="gallery__said">
            A lead band: the band ground, washed from the top left in its own accent.
          </p>
        </lead-band>
        <band-rule mirrored />
        <slice-band
          accent="var(--color-brand)"
          :items="slices"
          testid-prefix="gallery-slice"
        />
        <call-band
          :actions="actions"
          body="A call band says what somebody gets, and where to get it."
          eyebrow="Join us"
          headline="Play with us"
        />
      </section>
    </div>
  </div>
</template>

<style scoped>
.gallery {
  min-height: 100vh;
  padding: 2rem clamp(1rem, 4vw, 4rem) 6rem;
  background-color: var(--color-ground);
}

.gallery__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 1rem;
  margin-bottom: 2rem;
}

.gallery__title {
  font-family: var(--font-display);
  font-size: clamp(1.6rem, 4vw, 2.6rem);
  color: var(--color-chalk);
  text-transform: uppercase;
}

.gallery__theme {
  padding: 0.4rem 0.9rem;
  font-family: var(--font-bitmap);
  font-size: 0.72rem;
  color: var(--color-chalk);
  text-transform: uppercase;
  cursor: pointer;
  background: none;
  border: 1px solid var(--color-hairline);
}

.gallery__set {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 1rem;
  margin-bottom: 2.5rem;
}

.gallery__what {
  font-family: var(--font-bitmap);
  font-size: 0.75rem;
  color: var(--color-ash);
  text-transform: uppercase;
}

.gallery__what--inset {
  margin-bottom: 0.8rem;
}

.gallery__row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.8rem;
}

.gallery__heading {
  font-family: var(--font-display);
  font-size: clamp(1.4rem, 3vw, 2rem);
  line-height: 1.15;
  color: var(--color-chalk);
  text-transform: uppercase;
}

.gallery__heading--narrow {
  max-width: 12rem;
}

.gallery__said {
  color: var(--color-ash);
}

.gallery__pan {
  position: relative;
  width: 100%;
  max-width: 40rem;
  overflow: hidden;
}

.gallery__plates {
  display: flex;
  gap: 0.6rem;
  transition: translate 0.45s var(--ease-out-quint);
}

.gallery__plate {
  flex: none;
  width: 7.4rem;
  aspect-ratio: 1 / 1;
  object-fit: cover;
}

.gallery__bands {
  margin: 0 calc(clamp(1rem, 4vw, 4rem) * -1);
}

.gallery__bands > .gallery__what {
  padding: 0 clamp(1rem, 4vw, 4rem);
}

@media (prefers-reduced-motion: reduce) {
  .gallery__plates {
    transition: none;
  }
}
</style>
