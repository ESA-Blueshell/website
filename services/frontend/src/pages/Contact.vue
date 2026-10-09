<script lang="ts" setup>
import {computed, ref} from "vue"
import {useMediaQuery} from "@vueuse/core"
import Island from "@/components/island/Island.vue"
import HeaderBand from "@/components/island/HeaderBand.vue"
import BandRule from "@/components/island/BandRule.vue"
import CallBand from "@/components/island/CallBand.vue"
import CutButton from "@/components/island/CutButton.vue"
import CampusMap from "@/domains/association/island/CampusMap.vue"
import {discordInvite} from "@/domains/discord"
import {LOUNGE_IN_MAPS_APP, LOUNGE_ON_MAP} from "@/domains/association"

const BOARD_MAIL = "board@blueshell.utwente.nl"
const POST_ADDRESS = ["Blueshell Esports", "Postbus 217 (Bastille 49)", "7500 AE Enschede", "Netherlands"]

const JOIN = {
  headline: "Want to join?",
  body: "Everything about becoming a member is on the membership page.",
  actions: [{label: "Become a member", href: "/membership", tone: "solid" as const, testid: "contact-join"}],
}

/** A touch screen is most likely a phone, with a maps app to hand; anything else gets the site. */
const touch = useMediaQuery("(pointer: coarse)")
const lounge = computed(() => (touch.value ? LOUNGE_IN_MAPS_APP : LOUNGE_ON_MAP))

const copied = ref<boolean | null>(null)

const copyAddress = async () => {
  try {
    await navigator.clipboard.writeText(POST_ADDRESS.join("\n"))
    copied.value = true
  } catch {
    copied.value = false
  }
}
</script>

<template>
  <main class="contact-page">
    <island testid="contact-island">
      <header-band
        body="A question about joining, an event or working with us? Mail the board, ask in #board-questions on Discord or find them at the Esports Lounge."
        eyebrow="Contact"
        heading="Ask the board"
      />

      <section
        aria-label="Ways to reach the board"
        class="contact-ways"
      >
        <div class="contact-ways__row mx-auto w-full max-w-6xl px-5 sm:px-8">
          <div
            class="contact-way"
            data-testid="contact-way-mail"
          >
            <p class="contact-way__kind">
              <svg
                aria-hidden="true"
                fill="none"
                stroke="currentColor"
                stroke-linejoin="round"
                stroke-width="1.8"
                viewBox="0 0 24 24"
              ><rect
                height="14"
                width="18"
                x="3"
                y="5"
              /><path d="m3 6 9 7 9-7" /></svg>
              Mail
            </p>
            <!-- Breaks after its @ where the column is too narrow, never inside a name. -->
            <p class="contact-way__what">
              board@<wbr>blueshell.utwente.nl
            </p>
            <p class="contact-way__line">
              Reaches the whole board.
            </p>
            <cut-button
              class="contact-way__act"
              :href="`mailto:${BOARD_MAIL}`"
            >
              Send a mail
            </cut-button>
          </div>

          <div
            class="contact-way"
            data-testid="contact-way-discord"
          >
            <p class="contact-way__kind">
              <svg
                aria-hidden="true"
                fill="none"
                stroke="currentColor"
                stroke-linejoin="round"
                stroke-width="1.8"
                viewBox="0 0 24 24"
              ><path d="M4 5h16v11H9l-5 4Z" /></svg>
              Discord
            </p>
            <a
              class="contact-channel"
              data-testid="contact-board-questions"
              :href="discordInvite('board')"
              rel="noopener"
              target="_blank"
            >#board-questions</a>
            <p class="contact-way__line">
              Ask your questions on Discord.
            </p>
            <cut-button
              away
              class="contact-way__act"
              :href="discordInvite('board')"
            >
              Ask on Discord
            </cut-button>
          </div>

          <div
            class="contact-way"
            data-testid="contact-way-lounge"
          >
            <p class="contact-way__kind">
              <svg
                aria-hidden="true"
                fill="none"
                stroke="currentColor"
                stroke-linejoin="round"
                stroke-width="1.8"
                viewBox="0 0 24 24"
              ><path d="M12 21s-6.5-6.2-6.5-11a6.5 6.5 0 0 1 13 0c0 4.8-6.5 11-6.5 11Z" /><circle
                cx="12"
                cy="10"
                r="2.3"
              /></svg>
              In person
            </p>
            <p class="contact-way__what">
              Esports Lounge Twente
            </p>
            <p class="contact-way__line">
              You can usually find a board member there during the break.
            </p>
            <cut-button
              :away="!touch"
              class="contact-way__act"
              :href="lounge"
              testid="contact-lounge-map"
              tone="quiet"
            >
              Show it on the map
            </cut-button>
          </div>
        </div>
      </section>

      <band-rule />

      <section
        aria-label="Where to find us"
        class="contact-where"
      >
        <div class="contact-where__grid mx-auto w-full max-w-6xl px-5 py-12 sm:px-8 md:py-14">
          <div class="contact-where__visit">
            <p class="contact-where__eyebrow">
              Visit
            </p>
            <p class="contact-where__name">
              Esports Lounge Twente
            </p>
            <p class="contact-where__text">
              In the <b>Bastille</b>, the student union building on the University of Twente
              campus. Bus stop <b>UT/Bastille</b> is right outside.
            </p>
          </div>

          <campus-map class="contact-where__map" />

          <div class="contact-where__post">
            <p class="contact-where__eyebrow">
              Post
            </p>
            <address class="contact-where__text">
              <template
                v-for="line in POST_ADDRESS"
                :key="line"
              >
                {{ line }}<br>
              </template>
            </address>
            <cut-button
              class="contact-where__copy"
              small
              testid="contact-copy-address"
              tone="quiet"
              @click="copyAddress"
            >
              {{ copied === true ? "Copied" : copied === false ? "Select it to copy" : "Copy the address" }}
            </cut-button>
          </div>
        </div>
      </section>

      <call-band
        :actions="JOIN.actions"
        :body="JOIN.body"
        :headline="JOIN.headline"
        testid="contact-call"
      />
    </island>
  </main>
</template>

<style scoped>
/* The page's own main, in place of a layout's: it fills what the bar and the footer leave. */
.contact-page {
  flex: 1 0 auto;
}

/* A band, on the shared band ground: see island.css. */
.contact-ways {
  background-color: var(--band-ground);
}

.contact-ways__row {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  padding-block: 2.75rem 3rem;
}

/* Parted by the slant everything on the island is cut on, as the number band's figures are. */
.contact-way {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 0.7rem;
  min-width: 0;
  padding-inline: 2rem;
}

.contact-way:first-child {
  padding-inline-start: 0;
}

.contact-way::before {
  content: "";
  position: absolute;
  top: 0.2rem;
  bottom: 0.2rem;
  left: 0;
  width: 1px;
  background-color: var(--color-hairline);
  transform: skewX(-12deg);
}

.contact-way:first-child::before {
  display: none;
}

.contact-way__kind,
.contact-where__eyebrow {
  display: flex;
  align-items: center;
  gap: 0.55rem;
  font-family: var(--font-body);
  font-size: 11px;
  font-weight: 500;
  letter-spacing: 0.3em;
  text-transform: uppercase;
  color: var(--color-eyebrow);
}

.contact-way__kind svg {
  width: 18px;
  height: 18px;
}

.contact-way__what {
  font-family: var(--font-display);
  font-size: 1.35rem;
  line-height: 1.15;
  text-transform: uppercase;
}

/* A channel as Discord draws a mention of one: a pill in the brand's blue, filled under the pointer. */
.contact-channel {
  align-self: flex-start;
  padding: 0.05em 0.35em;
  border-radius: 4px;
  background-color: color-mix(in oklab, var(--color-brand) 22%, transparent);
  color: color-mix(in oklab, var(--color-brand-lit) 70%, var(--color-chalk));
  font-family: var(--font-body);
  font-size: 1.35rem;
  font-weight: 600;
  line-height: 1.3;
  text-decoration: none;
  transition: background-color 160ms ease, color 160ms ease;
}

.contact-channel:hover,
.contact-channel:focus-visible {
  background-color: var(--color-brand);
  color: #ffffff;
}

.contact-way__line {
  flex: 1;
  font-size: 0.95rem;
  line-height: 1.55;
  color: var(--color-ash);
}

.contact-way__act {
  align-self: flex-start;
  margin-top: 0.4rem;
}

.contact-where__grid {
  display: grid;
  grid-template-areas:
    "visit map"
    "post map";
  grid-template-rows: auto 1fr;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1.75fr);
  gap: 2.25rem 3.5rem;
}

.contact-where__visit {
  grid-area: visit;
}

.contact-where__map {
  grid-area: map;
}

.contact-where__post {
  grid-area: post;
}

.contact-where__visit,
.contact-where__post {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 0.55rem;
}

.contact-where__name {
  font-family: var(--font-display);
  font-size: 1.25rem;
  text-transform: uppercase;
}

.contact-where__text {
  font-size: 1rem;
  font-style: normal;
  line-height: 1.6;
  color: var(--color-ash);
}

.contact-where__text b {
  font-weight: 600;
  color: var(--color-chalk);
}

.contact-where__copy {
  margin-top: 0.35rem;
}

/*
 * On a phone each way stands on a tinted cut of its own with room around it, centred, and the
 * map comes between where the Lounge is and where post goes, centred the same.
 */
@media (--phone) {
  .contact-ways__row {
    grid-template-columns: minmax(0, 1fr);
    gap: 0.9rem;
    padding-block: 1.75rem 2rem;
  }

  .contact-way,
  .contact-way:first-child {
    align-items: center;
    padding: 1.6rem 1.25rem 1.5rem;
    text-align: center;
    clip-path: polygon(0.9rem 0, 100% 0, calc(100% - 0.9rem) 100%, 0 100%);
    background-color: color-mix(in oklab, var(--color-chalk) 5%, transparent);
  }

  .contact-way::before {
    display: none;
  }

  .contact-way__kind {
    justify-content: center;
  }

  .contact-way__act,
  .contact-channel {
    align-self: center;
  }

  .contact-where__grid {
    grid-template-areas:
      "visit"
      "map"
      "post";
    grid-template-rows: auto;
    grid-template-columns: minmax(0, 1fr);
    gap: 2rem;
  }

  .contact-where__visit,
  .contact-where__post {
    align-items: center;
    text-align: center;
  }
}
</style>
