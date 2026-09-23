<script setup lang="ts">
import BandHead from "@/components/island/BandHead.vue"
import LeadBand from "@/components/island/LeadBand.vue"
import SliceBand, {type SliceItem} from "@/components/island/SliceBand.vue"
import {DISCORD_INVITE} from "@/components/island/socialGlyphs"
import {CASUAL_GAMES} from "./casualGames"

/**
 * What members play together outside any team, each game leading into the Discord, where its
 * channel is.
 *
 * The slices are pinned dark: their glow, names and counts are tuned against dark art.
 */
const slices: (SliceItem & {channel: string})[] = CASUAL_GAMES.map(game => ({
  id: game.name,
  href: DISCORD_INVITE,
  title: game.name,
  meta: game.meta,
  banner: game.banner,
  icon: game.icon,
  accent: game.accent,
  channel: game.channel,
}))

// The Discord is somewhere else, so it opens beside the site rather than instead of it.
const openDiscord = () => window.open(DISCORD_INVITE, "_blank", "noopener")
</script>

<template>
  <lead-band
    accent="var(--color-acid)"
    testid="home-casual"
  >
    <band-head
      eyebrow="Casual gaming"
      heading="The games we play together"
    />

    <template #bleed>
      <slice-band
        accent="var(--color-acid)"
        class="island-dark"
        :items="slices"
        short
        testid-prefix="home-casual"
        @go="openDiscord"
      >
        <template #details="{item}">
          <a
            class="slice__link"
            :data-testid="`home-casual-link-${item.id}`"
            :href="DISCORD_INVITE"
            rel="noopener"
            target="_blank"
            @click.stop
          >
            Open {{ slices.find(one => one.id === item.id)?.channel }} on Discord →
          </a>
        </template>
      </slice-band>
    </template>
  </lead-band>
</template>
