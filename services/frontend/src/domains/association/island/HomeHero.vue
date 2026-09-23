<script lang="ts" setup>
/**
 * The top of the home page: the association's banner photograph, the wordmark, one line on
 * what Blueshell is, the two ways in and the social row.
 *
 * Pinned dark like every band that carries a photograph: a light theme does not make the
 * photograph light, so the buttons and the marks keep the ink that reads over it.
 */
import {computed} from "vue"
import CutButton from "@/components/island/CutButton.vue"
import SocialMark from "@/components/island/SocialMark.vue"
import {DISCORD_INVITE, opensTab, SOCIAL_GLYPHS} from "@/components/island/socialGlyphs"
import HeroBand from "@/domains/association/island/HeroBand.vue"

const {online = undefined} = defineProps<{
  /** How many are on the Discord right now, once something reads it; absent until then. */
  online?: number
}>()

const socials = [SOCIAL_GLYPHS.discord, SOCIAL_GLYPHS.instagram, SOCIAL_GLYPHS.twitch,
  SOCIAL_GLYPHS.linkedin, SOCIAL_GLYPHS.email]

const discordLabel = computed(() => online === undefined ? "Discord" : `Discord, ${online} online`)
</script>

<template>
  <hero-band
    body="Student esports and gaming association of the Twente region"
    class="island island-dark home-hero"
    headline="Blueshell"
    photo="/banner.webp"
    testid="home-hero"
  >
    <template #headline>
      <span
        id="blueshell"
        class="home-hero__word"
      >Blueshell</span>
    </template>

    <div class="home-hero__ways">
      <div class="home-hero__buttons">
        <cut-button
          href="/membership/signup"
          testid="home-become-member"
          tone="solid"
        >
          Become a member
        </cut-button>
        <cut-button
          away
          :href="DISCORD_INVITE"
          testid="home-join-discord"
        >
          Join our Discord
        </cut-button>
      </div>

      <div class="home-hero__socials">
        <a
          v-for="social in socials"
          :key="social.href"
          :aria-label="social === SOCIAL_GLYPHS.discord ? discordLabel : social.label"
          class="home-hero__social"
          :href="social.href"
          :rel="opensTab(social.href) ? 'noopener' : undefined"
          :target="opensTab(social.href) ? '_blank' : undefined"
        >
          <social-mark
            :glyph="social"
            :size="social === SOCIAL_GLYPHS.discord ? 22 : 20"
          />
          <!-- The slot the live count lights: shown only once there is a count to stand for. -->
          <span
            v-if="social === SOCIAL_GLYPHS.discord && online !== undefined"
            class="home-hero__live"
            data-testid="home-discord-live"
          />
        </a>
      </div>
    </div>
  </hero-band>
</template>

<style scoped>
/* The island root sets min-height: 100% for a page; the band keeps the height HeroBand gives it. */
.home-hero {
  min-height: 0;
}

.home-hero :deep(.hero-band__headline) {
  margin-top: 0;
}

.home-hero__word {
  display: block;
  font-size: 5.5rem;
  line-height: 0.95;
}

.home-hero :deep(.hero-band__body) {
  margin-top: 0.2rem;
  max-width: 40rem;
  font-size: 1.35rem;
  line-height: 1.35;
}

.home-hero__ways {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 1.5rem;
  margin-top: -0.1rem;
}

.home-hero__buttons {
  display: flex;
  flex-wrap: wrap;
  gap: 0.6rem;
}

.home-hero__socials {
  display: flex;
  align-items: center;
  gap: 1.15rem;
  color: #e6eaee;
}

.home-hero__social {
  position: relative;
  display: block;
  transition: color 220ms var(--ease-out-quint);
}

.home-hero__social:hover,
.home-hero__social:focus-visible {
  color: #fff;
}

.home-hero__live {
  position: absolute;
  top: -2px;
  right: -3px;
  width: 8px;
  height: 8px;
  border-radius: 9999px;
  background: #3ba55d;
  box-shadow: 0 0 0 2px rgb(13 19 25 / 75%);
}

@media (max-width: 767px) {
  .home-hero__word {
    font-size: 2.9rem;
  }

  .home-hero :deep(.hero-band__body) {
    font-size: 1.05rem;
  }
}
</style>
