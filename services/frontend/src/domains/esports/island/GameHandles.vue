<script lang="ts" setup>
import {computed, onMounted, ref, watch} from "vue"
import CountBadge from "@/components/island/CountBadge.vue"
import CutButton from "@/components/island/CutButton.vue"
import FactList, {type Fact} from "@/components/island/FactList.vue"
import FormField from "@/components/island/FormField.vue"
import StateTag from "@/components/island/StateTag.vue"
import TextInput from "@/components/island/TextInput.vue"
import {$handleNetworkError} from "@/plugins/handleNetworkError"
import {dropGameAccount, type Game, loadGameAccounts, saveGameAccount, type GameCode} from "../adapters/esports"
import {useGames} from "./useGames"

/**
 * What somebody is called in each game, one row per game: the ones the association fields now
 * first, then the rest, whose handles stay editable. Emptying a row and saving it removes it.
 */
defineOptions({name: "GameHandles"})

const {userId, nameShown = null} = defineProps<{
  userId: number
  nameShown?: boolean | null
}>()

const {games, identityOf} = useGames()

const stored = ref<Record<string, string>>({})
const draft = ref<Record<string, string>>({})
const saving = ref<GameCode | null>(null)
const saved = ref<GameCode | null>(null)
const loaded = ref(false)

const fielded = computed<Game[]>(() => games.value.filter(one => one.current))
const others = computed<Game[]>(() => games.value.filter(one => !one.current))
const handlesSet = computed(() => games.value.filter(one => stored.value[one.code]).length)

const facts = computed<Fact[]>(() => [
  {
    label: "Handles",
    value: `${handlesSet.value} of ${games.value.length} games`,
    sub: handlesSet.value ? "Shown on every roster you are on" : "None yet",
    testid: "game-handles-count",
  },
  {
    label: "Your name on rosters",
    value: nameShown ? "Shown" : "Hidden",
    sub: nameShown === null ? "Only the handle, as you are not a member" : "Change it on the Account tab",
  },
])

const dirty = (game: GameCode) => (draft.value[game] ?? "").trim() !== (stored.value[game] ?? "")

const refresh = async () => {
  try {
    const accounts = await loadGameAccounts(userId)
    stored.value = Object.fromEntries(accounts.map(account => [account.game, account.handle]))
    draft.value = {...stored.value}
  } catch (error) {
    $handleNetworkError(error)
  } finally {
    loaded.value = true
  }
}

const save = async (game: GameCode) => {
  if (!dirty(game)) return
  const value = (draft.value[game] ?? "").trim()
  saving.value = game
  try {
    if (value === "") await dropGameAccount(userId, game)
    else await saveGameAccount(userId, game, value)
    await refresh()
    saved.value = game
  } catch (error) {
    $handleNetworkError(error)
  } finally {
    saving.value = null
  }
}

const edited = (game: GameCode) => {
  if (saved.value === game) saved.value = null
}

watch(() => userId, refresh)
onMounted(refresh)
</script>

<template>
  <div
    class="handles"
    data-testid="game-handles"
  >
    <fact-list
      :columns="2"
      :facts="facts"
    />

    <template
      v-for="group in [{title: 'Fielded now', list: fielded}, {title: 'Other games', list: others}]"
      :key="group.title"
    >
      <template v-if="group.list.length">
        <h2 class="handles__head">
          {{ group.title }}<count-badge
            :count="group.list.length"
            said="games"
          />
        </h2>
        <div class="handles__rows">
          <form
            v-for="game in group.list"
            :key="game.code"
            class="handle"
            :style="{'--accent': identityOf(game.code).accent}"
            @submit.prevent="save(game.code)"
          >
            <span
              aria-hidden="true"
              class="handle__glyph"
            >
              <img
                v-if="identityOf(game.code).icon"
                alt=""
                :src="identityOf(game.code).icon!"
                :srcset="identityOf(game.code).iconSrcset"
                sizes="44px"
              >
              <template v-else>{{ game.name.charAt(0) }}</template>
            </span>
            <span class="handle__name">{{ game.name }}</span>
            <form-field
              v-slot="field"
              class="handle__field"
              :filled="Boolean(draft[game.code])"
              :label="`Your handle in ${game.name}`"
              :testid="`game-handle-${game.code.toLowerCase()}`"
              variant="inside"
            >
              <text-input
                v-model="draft[game.code]"
                autocomplete="off"
                :control-id="field.controlId"
                :disabled="!loaded"
                @update:model-value="edited(game.code)"
              />
            </form-field>
            <span class="handle__end">
              <state-tag
                v-if="saved === game.code && !dirty(game.code)"
                tone="ok"
              >
                Saved
              </state-tag>
              <cut-button
                v-else-if="dirty(game.code)"
                :disabled="saving === game.code"
                submit
                :testid="`game-handle-save-${game.code.toLowerCase()}`"
                tone="solid"
              >
                Save
              </cut-button>
            </span>
          </form>
        </div>
      </template>
    </template>
  </div>
</template>

<style scoped>
.handles {
  display: flex;
  flex-direction: column;
}

.handles__head {
  margin: 2rem 0 1rem;
  font-family: var(--font-display);
  font-size: 1.3rem;
  line-height: 1.1;
  text-transform: uppercase;
}

.handles__rows {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.handle {
  --row-h: 4.9rem;
  /* tan 12°, the lean every cut row takes. */
  --cut: calc(var(--row-h) * 0.2126);

  display: grid;
  grid-template-columns: 2.75rem minmax(7rem, 11rem) minmax(0, 1fr) 5.5rem;
  align-items: center;
  gap: 0 1.1rem;
  height: var(--row-h);
  padding: 0 calc(var(--cut) + 0.8rem) 0 calc(var(--cut) + 1rem);
  clip-path: polygon(var(--cut) 0, 100% 0, calc(100% - var(--cut)) 100%, 0 100%);
  background-color: var(--band-ground);
}

.handle__glyph {
  display: grid;
  place-items: center;
  width: 2.75rem;
  height: 2.75rem;
  overflow: hidden;
  background: color-mix(in oklab, var(--accent) 22%, transparent);
  font-family: var(--font-display);
  font-size: 1.2rem;
  color: var(--color-chalk);
}

.handle__glyph img {
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.handle__name {
  overflow: hidden;
  font-family: var(--font-display);
  font-size: 1.05rem;
  text-overflow: ellipsis;
  text-transform: uppercase;
  white-space: nowrap;
}

/* The field's message line is kept for errors; a handle has none, so the row keeps its height. */
.handle__field :deep(.island-field__said) {
  display: none;
}

.handle__end {
  display: flex;
  justify-content: flex-end;
}

@media (max-width: 767px) {
  .handle {
    --row-h: 7.4rem;

    grid-template-columns: 2.4rem minmax(0, 1fr) auto;
    grid-template-rows: auto auto;
    gap: 0.55rem 0.8rem;
    align-content: center;
    padding: 0 calc(var(--cut) + 0.4rem) 0 calc(var(--cut) + 0.6rem);
  }

  .handle__glyph {
    width: 2.4rem;
    height: 2.4rem;
  }

  .handle__name {
    grid-column: 2 / 4;
  }

  .handle__field {
    grid-column: 1 / 3;
  }
}
</style>
