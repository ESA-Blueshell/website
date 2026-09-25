<script lang="ts" setup>
import HeaderBand from "@/components/island/HeaderBand.vue"
import $markdownToHtml from "@/plugins/markdownToHtml.ts"

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
      <div
        v-if="intro"
        class="mt-5 max-w-2xl font-body text-sm leading-relaxed text-ash"
        data-testid="esports-game-intro"
        v-html="$markdownToHtml(intro)"
      />
    </template>
    <slot />
  </header-band>
</template>
