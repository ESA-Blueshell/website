<script lang="ts" setup>
import {computed, onMounted, ref, watch} from "vue"
import {$handleNetworkError} from "@/plugins/handleNetworkError"
import {
  dropGameAccount,
  loadGameAccounts,
  saveGameAccount,
  type GameCode,
} from "../adapters/esports"
import {useGames} from "@/domains/esports/island/useGames"

defineOptions({name: "GameHandles"})

const props = defineProps<{
  userId: number
}>()

// Every game, not only the ones still fielded: a handle in a retired game stays editable.
// What each is called is its record's to say, rather than its code title-cased.
const {games, identityOf} = useGames()

const GAMES = computed<GameCode[]>(() => games.value.map(one => one.code))

const gameLabel = (game: GameCode) => identityOf(game).name || game

/** What is stored, against what is typed: a row is dirty when the two differ. */
const stored = ref<Record<string, string>>({})
const draft = ref<Record<string, string>>({})
const saving = ref<string | null>(null)
const loading = ref<boolean>(true)

const dirty = computed<(game: GameCode) => boolean>(() =>
  (game: GameCode) => (draft.value[game] ?? "").trim() !== (stored.value[game] ?? ""),
)

const refresh = async () => {
  loading.value = true
  try {
    const accounts = await loadGameAccounts(props.userId)
    stored.value = Object.fromEntries(accounts.map((account) => [account.game, account.handle]))
    draft.value = {...stored.value}
  } catch (error) {
    $handleNetworkError(error)
  } finally {
    loading.value = false
  }
}

const save = async (game: GameCode) => {
  const value = (draft.value[game] ?? "").trim()
  saving.value = game
  try {
    // An emptied field means "I am not on this one", which is a removal rather than a blank.
    if (value === "") {
      await dropGameAccount(props.userId, game)
    } else {
      await saveGameAccount(props.userId, game, value)
    }
    await refresh()
  } catch (error) {
    $handleNetworkError(error)
  } finally {
    saving.value = null
  }
}

watch(() => props.userId, refresh)
onMounted(refresh)
</script>

<template>
  <v-card
    class="manager-card"
    data-testid="game-handles"
    rounded="lg"
    variant="flat"
  >
    <div class="manager-card__header">
      <div class="manager-card__heading">
        <p class="text-overline mb-1">
          Game handles
        </p>
        <p class="text-body-2 text-medium-emphasis mb-0">
          What you are called in each game. Rosters you appear on show this name, so changing it
          here changes it everywhere at once.
        </p>
      </div>
    </div>

    <div class="manager-card__body">
      <v-progress-circular v-if="loading" />

      <div
        v-for="game in GAMES"
        v-else
        :key="game"
        class="handle-row"
      >
        <v-text-field
          v-model="draft[game]"
          clearable
          density="compact"
          :data-testid="`game-handle-${game.toLowerCase()}`"
          hide-details
          :label="gameLabel(game)"
          @keydown.enter="save(game)"
        />
        <v-btn
          :data-testid="`game-handle-save-${game.toLowerCase()}`"
          :disabled="!dirty(game)"
          :loading="saving === game"
          size="small"
          variant="text"
          @click="save(game)"
        >
          Save
        </v-btn>
      </div>
    </div>
  </v-card>
</template>

<style lang="scss" scoped>
.handle-row {
  display: flex;
  align-items: center;
  gap: 8px;

  & + & {
    margin-top: 10px;
  }

  .v-text-field {
    flex: 1 1 auto;
    min-width: 0;
  }
}
</style>
