<script lang="ts" setup>
import HeaderBand from "@/components/island/HeaderBand.vue"
import MarkdownView from "@/components/island/MarkdownView.vue"
import {gameRoomUrl} from "@/domains/discord"

/**
 * The head of a game's competition page: its colour, its logo, its name and what the page says
 * about it. Drawn by the page and by the game's edit page preview alike; the page slots in the
 * way to edit it.
 */
defineOptions({name: "EsportsGameHead"})

defineProps<{
  accent: string
  name: string
  icon?: string | null
  iconSrcset?: string
  intro?: string
  /** The Discord channels its esports players meet in, each linked into the app. */
  channels?: {id: string, guildId: string, name: string}[]
}>()
</script>

<template>
  <!-- The game's own colour and the closer blob: this page is the game's, and the head says so
       before the name does. -->
  <header-band
    :accent="accent"
    blob="tight"
  >
    <template #head>
      <slot name="edit" />
      <!--
        The game's own logo identifies the page rather than decorating it, and it is the only
        logo here: a team carries one only once somebody uploads it, so without this the page a
        slice leads to shows nothing of the game the slice named.
      -->
      <div class="flex items-center gap-4">
        <img
          v-if="icon"
          alt=""
          class="h-10 w-10 object-contain sm:h-12 sm:w-12"
          data-testid="esports-game-icon"
          sizes="48px"
          :src="icon"
          :srcset="iconSrcset"
        >
        <div>
          <p class="font-body text-[11px] tracking-[0.28em] text-ash uppercase">
            Blueshell Esports
          </p>
          <!-- min-h holds the line while the records answer, so the name arriving does not shift
               the header down. -->
          <h1 class="min-h-[1em] font-display text-2xl leading-none uppercase sm:text-4xl">
            {{ name }}
          </h1>
        </div>
      </div>
      <markdown-view
        v-if="intro"
        class="mt-5 max-w-2xl font-body text-sm leading-relaxed text-ash"
        data-testid="esports-game-intro"
        :source="intro"
      />
      <p
        v-if="channels && channels.length > 0"
        class="esports-head__channels"
        data-testid="esports-game-channels"
      >
        <span class="esports-head__channels-label">Esports on Discord</span>
        <template
          v-for="(channel, index) in channels"
          :key="channel.id"
        >
          <template v-if="index > 0">
            ·
          </template>
          <a
            :data-testid="`esports-game-channel-${channel.id}`"
            :href="gameRoomUrl(channel)"
            rel="noopener"
            target="_blank"
          >#{{ channel.name }}</a>
        </template>
      </p>
    </template>
    <slot />
  </header-band>
</template>

<style scoped>
.esports-head__channels {
  margin-top: 1rem;
  font-family: var(--font-body);
  font-size: 0.9rem;
  color: var(--color-chalk);
}

.esports-head__channels a {
  color: inherit;
  text-decoration: none;
}

.esports-head__channels a:hover,
.esports-head__channels a:focus-visible {
  color: var(--color-brand);
}

.esports-head__channels-label {
  margin-right: 0.5rem;
  font-size: 11px;
  letter-spacing: 0.28em;
  text-transform: uppercase;
  color: var(--color-ash);
}
</style>
