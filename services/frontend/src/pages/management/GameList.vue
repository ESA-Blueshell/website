<script lang="ts" setup>
/* Every game: its games and esports channels, whether it is in competition and whether Discord has
   any of its channels otherwise than the site keeps them. Opening one renders the site's own game
   editor inside Management. */
import {computed, onMounted, ref} from "vue"
import FoldOut from "@/components/island/FoldOut.vue"
import SearchBox from "@/components/island/SearchBox.vue"
import StateMark from "@/components/island/StateMark.vue"
import {type CataloguedChannel, listCatalogue} from "@/domains/discord"
import {type CasualGame, useCasualGames} from "@/domains/games"

defineOptions({name: "GameListPage"})

const {games, ready} = useCasualGames()
const catalogue = ref<CataloguedChannel[]>([])
const loaded = ref(false)
const search = ref("")

const differing = computed(() => new Set(catalogue.value.filter((one) => one.access?.differs).map((one) => one.id)))
const channelsOf = (game: CasualGame) => [...game.channels, ...game.esportsChannels]
const differs = (game: CasualGame) => channelsOf(game).some((one) => differing.value.has(one.id))
const named = (game: CasualGame) => {
  const channels = channelsOf(game)
  return channels.length === 0 ? "No channel" : channels.map((one) => `#${one.name}`).join(", ")
}

const shown = computed(() => {
  const needle = search.value.trim().toLowerCase()
  return games.value
    .filter((game) => needle === "" || [game.name, game.code, ...channelsOf(game).map((one) => one.name)].some((value) => value.toLowerCase().includes(needle)))
    .sort((a, b) => a.name.localeCompare(b.name))
})
const live = computed(() => shown.value.filter((one) => !one.archived))
const archived = computed(() => shown.value.filter((one) => one.archived))

onMounted(async () => {
  const [, listed] = await Promise.all([ready, listCatalogue()])
  catalogue.value = listed
  loaded.value = true
})
</script>

<template>
  <div
    class="games"
    data-testid="game-list"
  >
    <header class="games__head">
      <div>
        <p class="games__eyebrow">
          Content
        </p>
        <h1 class="games__title">
          Games
        </h1>
        <p class="games__note">
          Every game, its channels on Discord and where Discord differs from the access set on the site.
        </p>
      </div>
      <router-link
        class="games__action"
        data-testid="game-list-new"
        to="/management/games/new"
      >
        Add a game
      </router-link>
    </header>

    <search-box
      v-model="search"
      label="Search games"
      testid="game-list-search"
    />

    <p
      v-if="loaded && shown.length === 0"
      class="games__note"
      data-testid="game-list-empty"
    >
      No game matches.
    </p>

    <component
      :is="index === 1 ? FoldOut : 'section'"
      v-for="(group, index) in [live, archived]"
      :key="index"
      v-bind="index === 1 ? {label: `Archived · ${group.length}`, testid: 'game-list-archived'} : {}"
    >
      <ul
        v-if="group.length"
        class="games__rows"
      >
        <li
          v-for="game in group"
          :key="game.code"
          class="games__row"
          :data-testid="`game-row-${game.code}`"
        >
          <span class="games__name">
            <router-link :to="`/management/games/${game.slug}`">{{ game.name }}</router-link>
          </span>
          <span class="games__sub">{{ named(game) }}</span>
          <span class="games__sub">{{ game.inCompetition ? "In competition" : "Casual" }}</span>
          <state-mark
            :kind="differs(game) ? 'extra' : 'in-step'"
            :testid="`game-differs-${game.code}`"
          >
            {{ differs(game) ? "Differs on Discord" : "In step" }}
          </state-mark>
        </li>
      </ul>
    </component>
  </div>
</template>

<style scoped>
.games {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  max-width: 76rem;
  padding: 2rem 2.4rem 3rem;
}

.games__head {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: 1rem;
}

.games__eyebrow {
  margin: 0;
  font-size: 11px;
  letter-spacing: 0.3em;
  text-transform: uppercase;
  color: var(--color-eyebrow, var(--color-ash));
}

.games__title {
  margin: 0;
  font-family: var(--font-display);
  font-size: clamp(1.4rem, 3vw, 2rem);
}

.games__note {
  margin: 0;
  max-width: 48rem;
  color: var(--color-ash);
}

.games__action {
  padding: 0.45rem 0.9rem;
  border: 1px solid var(--color-hairline);
  font-size: 0.86rem;
  color: var(--color-chalk);
  text-decoration: none;
}

.games__rows {
  display: flex;
  flex-direction: column;
  margin: 0;
  padding: 0;
  list-style: none;
  border-top: 1px solid var(--color-hairline);
}

.games__row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1.6fr) 8rem 10rem;
  align-items: center;
  gap: 1rem;
  padding: 0.65rem 0.4rem;
  border-bottom: 1px solid var(--color-hairline);
}

.games__name {
  overflow: hidden;
  font-weight: 600;
  text-overflow: ellipsis;
}

.games__name a {
  color: var(--color-chalk);
}

.games__sub {
  overflow: hidden;
  font-size: 0.84rem;
  color: var(--color-ash);
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (max-width: 839px) {
  .games {
    padding: 1.2rem 1.1rem 2rem;
  }

  .games__row {
    grid-template-columns: minmax(0, 1fr) auto;
  }
}
</style>
