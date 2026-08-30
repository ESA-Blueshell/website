<script lang="ts" setup>
import {computed, ref, watch} from "vue"
import IslandDialog from "./IslandDialog.vue"
import {enterGameInSeason, type Game, type Season} from "../adapters/esports"
import {useGames} from "./useGames"

/**
 * Putting a game the association has played before into the season on show.
 *
 * One click and done, which is why it is a picker rather than a form: the game already exists,
 * and what is being recorded is that it runs again. A game that is new is a different act, with
 * a whole editor behind it, and it has a pane of its own.
 */
defineOptions({name: "EnterGameDialog"})

const props = defineProps<{
  open: boolean
  season: Season | null
  /** The games already in the season on show, which there is nothing to enter. */
  alreadyIn: Game[]
  accent?: string
}>()

const emit = defineEmits<{
  (event: "update:open", open: boolean): void
  (event: "entered", game: Game): void
}>()

const {games} = useGames()

const failure = ref<string | null>(null)
const entering = ref<Game | null>(null)

watch(() => props.open, (open) => {
  if (open) {
    failure.value = null
    entering.value = null
  }
})

/**
 * Every game the association knows that is not already in this season.
 *
 * Not only the ones it currently plays: a game nobody has entered for years is exactly the one
 * somebody is picking here when the association goes back to it.
 */
const offered = computed(() =>
  games.value.filter(one => !props.alreadyIn.includes(one.game)))

const enter = async (game: Game) => {
  const season = props.season
  if (!season || entering.value != null) return
  entering.value = game
  failure.value = null
  try {
    const added = await enterGameInSeason(season.id, game)
    if (!added) {
      failure.value = "That game could not be put into the season."
      return
    }
    emit("entered", game)
    emit("update:open", false)
  } finally {
    entering.value = null
  }
}
</script>

<template>
  <island-dialog
    :accent="accent"
    :open="open"
    testid="enter-game-dialog"
    :title="season ? `A game we played before, in ${season.name}` : 'A game we played before'"
    @update:open="emit('update:open', $event)"
  >
    <div class="enter-game">
      <p
        v-if="offered.length === 0"
        class="enter-game__note"
        data-testid="enter-game-none"
      >
        Every game the association knows is already in this season.
      </p>

      <ul
        v-else
        class="enter-game__list"
        data-testid="enter-game-list"
      >
        <li
          v-for="game in offered"
          :key="game.game"
        >
          <button
            class="enter-game__pick"
            :data-testid="`enter-game-${game.game}`"
            :disabled="entering != null"
            type="button"
            @click="enter(game.game)"
          >
            {{ game.name }}
          </button>
        </li>
      </ul>

      <p
        v-if="failure"
        class="enter-game__failure"
        data-testid="enter-game-failure"
        role="alert"
      >
        {{ failure }}
      </p>
    </div>
  </island-dialog>
</template>

<style scoped>
.enter-game {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.enter-game__list {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  padding: 0;
  margin: 0;
  list-style: none;
}

.enter-game__pick {
  width: 100%;
  padding: 0.6rem 0.75rem;
  font-family: var(--font-body);
  font-size: 0.95rem;
  color: inherit;
  text-align: left;
  cursor: pointer;
  background: rgb(255 255 255 / 4%);
  border: 1px solid rgb(255 255 255 / 12%);
  border-radius: 2px;
}

.enter-game__pick:hover:not(:disabled) {
  background: rgb(255 255 255 / 10%);
}

.enter-game__pick:disabled {
  cursor: default;
  opacity: 0.6;
}

.enter-game__note,
.enter-game__failure {
  margin: 0;
  font-family: var(--font-body);
  font-size: 0.85rem;
}

.enter-game__failure {
  color: var(--color-danger, #ff6b6b);
}
</style>
