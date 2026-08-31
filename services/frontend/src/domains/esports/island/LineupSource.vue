<script lang="ts" setup>
import {computed, ref, watch} from "vue"
import IslandPicker from "./IslandPicker.vue"
import {
  loadRoster,
  loadTeamSeasons,
  loadTeams,
  type Fielding,
  type GameCode,
  type RosterEntry,
  type Team,
} from "../adapters/esports"

/**
 * Starting a line-up from one that already exists.
 *
 * Pick a team, pick one of its line-ups, tick who comes across. The same control in both ways
 * into a season, because it answers the same question either way — the two differ only in what
 * is chosen for you when it opens.
 *
 * A line-up is named by its game and its season. "Its last line-up" is only useful if the
 * reader can tell which squad that was, and a team that spans games has more than one answer.
 *
 * Everybody is ticked to begin with and can be unticked one at a time: a roster is published
 * under the names of real people, and last season's departure should not quietly reappear.
 */
defineOptions({name: "LineupSource"})

const props = defineProps<{
  /** The game being filled, which decides which line-up is offered first. */
  game: GameCode
  /**
   * The team whose line-ups are offered, where one is already settled.
   *
   * Set where a team that played before is being fielded — it is that team's line-up that is
   * meant. Left out where a team is being made, since it has no history to start from and the
   * point is to start from somebody else's.
   */
  teamId?: number | null
  /** The season being filled, which is never offered as a source of itself. */
  seasonId?: number | null
}>()

const emit = defineEmits<{
  (event: "update:carried", carried: {from: Fielding | null; entries: RosterEntry[]}): void
}>()

const pool = ref<Team[]>([])
const chosenTeam = ref<Team | null>(null)
const played = ref<Fielding[]>([])
const chosen = ref<Fielding | null>(null)
const lineup = ref<RosterEntry[]>([])
const dropped = ref<Set<number>>(new Set())
const loading = ref(false)

const kept = computed(() => lineup.value.filter(entry => !dropped.value.has(entry.id)))

watch([chosen, kept], () => {
  emit("update:carried", {from: chosen.value, entries: kept.value})
})

/** A team already chosen is the one whose line-ups are offered; otherwise the pool is searched. */
watch(() => props.teamId, async (teamId) => {
  if (teamId == null) {
    if (pool.value.length === 0) pool.value = await loadTeams()
    return
  }
  const team = (pool.value.length > 0 ? pool.value : await loadTeams()).find(one => one.id === teamId)
  if (team) await pick(team)
}, {immediate: true})

/**
 * The line-up offered first: the one this team last had in the game being filled, falling back
 * to its most recent in any game where it has never played this one.
 *
 * The fall-back is what carries a team's people into a game it is playing for the first time,
 * which is the whole of what the shared pool buys.
 */
const preferred = (fieldings: Fielding[]): Fielding | null =>
  fieldings.find(one => one.game === props.game && one.season.id !== props.seasonId)
    ?? fieldings.find(one => one.season.id !== props.seasonId)
    ?? null

const pick = async (team: Team) => {
  chosenTeam.value = team
  loading.value = true
  try {
    played.value = (await loadTeamSeasons(team.id))
      .filter(one => one.season.id !== props.seasonId)
    await show(preferred(played.value))
  } finally {
    loading.value = false
  }
}

const show = async (fielding: Fielding | null) => {
  chosen.value = fielding
  dropped.value = new Set()
  lineup.value = fielding
    ? await loadRoster(chosenTeam.value!.id, fielding.game, fielding.season.id)
    : []
}

const drop = (id: number) => {
  const next = new Set(dropped.value)
  if (next.has(id)) next.delete(id)
  else next.add(id)
  dropped.value = next
}

/** Named by game and season, so the reader can tell which squad they are about to copy. */
const nameOf = (fielding: Fielding) => `${fielding.game} · ${fielding.season.name}`
</script>

<template>
  <section
    class="source"
    data-testid="lineup-source"
  >
    <h3 class="source__heading">
      Start from a line-up
    </h3>

    <!-- Kept where it was once a team is chosen: choosing again is the same act, so it is the
         same control, with the one that is chosen filled rather than taken out of the list. -->
    <island-picker
      v-if="teamId == null"
      empty-note="The association has no other team to start from."
      :options="pool.map(one => ({key: String(one.id), label: one.name}))"
      placeholder="Search every team"
      :selected-key="chosenTeam ? String(chosenTeam.id) : null"
      testid-prefix="lineup-source-team"
      @pick="key => { const team = pool.find(one => String(one.id) === key); if (team) pick(team) }"
    />

    <template v-if="chosenTeam != null || teamId != null">
      <p
        v-if="played.length === 0"
        class="source__note"
        data-testid="lineup-source-none"
      >
        {{ chosenTeam?.name ?? "This team" }} has no other line-up to start from.
      </p>

      <template v-else>
        <label class="source__field">
          <span class="source__label">Line-up</span>
          <select
            class="source__input"
            data-testid="lineup-source-fielding"
            :value="chosen ? `${chosen.game}:${chosen.season.id}` : ''"
            @change="show(played.find(one =>
              `${one.game}:${one.season.id}` === ($event.target as HTMLSelectElement).value) ?? null)"
          >
            <option value="">
              Nobody to begin with
            </option>
            <option
              v-for="one in played"
              :key="`${one.game}:${one.season.id}`"
              :value="`${one.game}:${one.season.id}`"
            >
              {{ nameOf(one) }}
            </option>
          </select>
        </label>

        <ul
          v-if="lineup.length > 0"
          class="source__list"
          data-testid="lineup-source-people"
        >
          <li
            v-for="entry in lineup"
            :key="entry.id"
          >
            <label
              class="source__person"
              :data-testid="`lineup-source-person-${entry.id}`"
            >
              <input
                :checked="!dropped.has(entry.id)"
                type="checkbox"
                @change="drop(entry.id)"
              >
              <span class="source__handle">{{ entry.handle }}</span>
              <span
                v-if="entry.displayName"
                class="source__name"
              >{{ entry.displayName }}</span>
            </label>
          </li>
        </ul>
      </template>
    </template>
  </section>
</template>

<style scoped>
/*
 * Square-edged rather than cut on the island's diagonal, and deliberately: a clip-path cuts
 * off anything a child paints outside the panel, and the picker inside this one drops a list
 * over the form below it. The slant would take that list off at the panel's edge.
 */
.source {
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
  padding: 0.85rem 1rem 1rem;
  background-color: color-mix(in oklab, var(--color-chalk) 4%, transparent);
  border-left: 2px solid color-mix(in oklab, var(--color-brand) 55%, transparent);
}

.source__heading {
  margin: 0;
  font-family: var(--font-display);
  font-size: 0.72rem;
  color: var(--color-ash);
  letter-spacing: 0.06em;
  text-transform: uppercase;
}

.source__field {
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
}

.source__label {
  font-family: var(--font-body);
  font-size: 0.72rem;
  color: var(--color-ash);
}

.source__input {
  padding: 0.55rem 0.9rem;
  font-family: var(--font-body);
  font-size: 0.9rem;
  color: var(--color-chalk);
  background-color: color-mix(in oklab, var(--color-chalk) 7%, transparent);
  border: 0;
  clip-path: polygon(0.55rem 0, 100% 0, calc(100% - 0.55rem) 100%, 0 100%);
}

/* Scrolls for the same reason the pickers do: a long line-up is read, not truncated. */
.source__list {
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
  max-height: 13rem;
  padding: 0;
  margin: 0;
  overflow-y: auto;
  list-style: none;
  overscroll-behavior: contain;
}

.source__pick {
  width: 100%;
  padding: 0.45rem 0.6rem;
  font-family: var(--font-body);
  font-size: 0.9rem;
  color: inherit;
  text-align: left;
  cursor: pointer;
  background: rgb(255 255 255 / 4%);
  border: 1px solid rgb(255 255 255 / 12%);
  border-radius: 2px;
}

.source__person {
  display: flex;
  gap: 0.55rem;
  align-items: baseline;
  padding: 0.4rem 0.6rem;
  font-family: var(--font-body);
  font-size: 0.9rem;
  cursor: pointer;
  background-color: color-mix(in oklab, var(--color-chalk) 4%, transparent);
  clip-path: polygon(0.45rem 0, 100% 0, calc(100% - 0.45rem) 100%, 0 100%);
}

.source__person:hover {
  background-color: color-mix(in oklab, var(--color-brand) 18%, transparent);
}

.source__person input {
  accent-color: var(--color-brand);
}

.source__name {
  font-size: 0.78rem;
  color: var(--color-ash);
}

.source__note {
  margin: 0;
  font-family: var(--font-body);
  font-size: 0.85rem;
  opacity: 0.85;
}
</style>
