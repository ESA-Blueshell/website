<script lang="ts" setup>
import {computed, ref, watch} from "vue"
import SearchPicker from "@/components/island/SearchPicker.vue"
import CheckBox from "@/components/island/CheckBox.vue"
import FormField from "@/components/island/FormField.vue"
import FormFields from "../../../components/island/FormFields.vue"
import FormSection from "../../../components/island/FormSection.vue"
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
 * Pick a team, pick one of its line-ups, tick who comes across — the same control both ways into a
 * season, differing only in what is chosen when it opens. A line-up is named by its game and
 * season, since a team that spans games has more than one answer to "its last line-up". Everybody
 * is ticked to begin with and unticked one at a time: a roster is published under the names of real
 * people, and last season's departure should not quietly reappear.
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
  /**
   * `unread` is what stops a caller writing on this. A line-up that could not be read carries
   * no entries, and "no entries" is also what an emptied line-up carries, so the two have to
   * be told apart by something other than the count.
   */
  (event: "update:carried",
    carried: {from: Fielding | null; entries: RosterEntry[]; unread: boolean}): void
}>()

const pool = ref<Team[]>([])
const chosenTeam = ref<Team | null>(null)
const played = ref<Fielding[]>([])
const chosen = ref<Fielding | null>(null)
const lineup = ref<RosterEntry[]>([])
const dropped = ref<Set<number>>(new Set())
const loading = ref(false)

/** Set where the chosen line-up could not be read, so it is not offered as one with nobody on it. */
const unread = ref(false)

const kept = computed(() => lineup.value.filter(entry => !dropped.value.has(entry.id)))

watch([chosen, kept, unread], () => {
  emit("update:carried", {from: chosen.value, entries: kept.value, unread: unread.value})
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
  if (!fielding) {
    unread.value = false
    lineup.value = []
    return
  }
  const roster = await loadRoster(chosenTeam.value!.id, fielding.game, fielding.season.id)
  unread.value = roster == null
  lineup.value = roster ?? []
}

const drop = (id: number) => {
  const next = new Set(dropped.value)
  if (next.has(id)) next.delete(id)
  else next.add(id)
  dropped.value = next
}

/** Named by game and season, so the reader can tell which squad they are about to copy. */
const nameOf = (fielding: Fielding) => `${fielding.game} · ${fielding.season.name}`

/** The line-ups offered, and starting from nobody, as the picker's rows. */
const NOBODY = "nobody"
const keyOf = (fielding: Fielding) => `${fielding.game}:${fielding.season.id}`
const fieldingOptions = computed(() => [
  {key: NOBODY, label: "Nobody to begin with"},
  ...played.value.map(one => ({key: keyOf(one), label: nameOf(one)})),
])
const chooseFielding = (key: string) => void show(played.value.find(one => keyOf(one) === key) ?? null)
</script>

<template>
  <form-section
    testid="lineup-source"
    title="Start from a line-up"
  >
    <form-fields>
      <!-- Kept where it was once a team is chosen: choosing again is the same act, so it is the
           same control, with the one that is chosen filled rather than taken out of the list. -->
      <form-field
        v-if="teamId == null"
        :filled="chosenTeam != null"
        label="Team"
        variant="inside"
      >
        <template #default="{controlId, labelId}">
          <search-picker
            :control-id="controlId"
            empty-note="The association has no other team to start from."
            :labelled-by="labelId"
            :options="pool.map(one => ({key: String(one.id), label: one.name}))"
            placeholder="Search every team"
            :selected-key="chosenTeam ? String(chosenTeam.id) : null"
            testid-prefix="lineup-source-team"
            @pick="key => { const team = pool.find(one => String(one.id) === key); if (team) pick(team) }"
          />
        </template>
      </form-field>

      <form-field
        v-if="(chosenTeam != null || teamId != null) && played.length > 0"
        :class="{'form-span': teamId != null}"
        filled
        label="Line-up"
        testid="lineup-source-fielding"
        variant="inside"
      >
        <template #default="{controlId, labelId}">
          <search-picker
            :control-id="controlId"
            :labelled-by="labelId"
            :options="fieldingOptions"
            :selected-key="chosen ? keyOf(chosen) : NOBODY"
            testid-prefix="lineup-source-fielding"
            @pick="chooseFielding"
          />
        </template>
      </form-field>
    </form-fields>

    <template v-if="chosenTeam != null || teamId != null">
      <p
        v-if="played.length === 0"
        class="source__note"
        data-testid="lineup-source-none"
      >
        {{ chosenTeam?.name ?? "This team" }} has no other line-up to start from.
      </p>
      <p
        v-else-if="unread"
        class="source__note"
        data-testid="lineup-source-unknown"
        role="alert"
      >
        That line-up could not be read, so there is nobody to carry across. Pick it again.
      </p>
      <ul
        v-else-if="lineup.length > 0"
        class="source__list"
        data-testid="lineup-source-people"
      >
        <li
          v-for="entry in lineup"
          :key="entry.id"
          class="source__person"
          :data-testid="`lineup-source-person-${entry.id}`"
        >
          <check-box
            :label="entry.displayName ? `${entry.handle} · ${entry.displayName}` : entry.handle"
            :model-value="!dropped.has(entry.id)"
            @update:model-value="drop(entry.id)"
          />
        </li>
      </ul>
    </template>
  </form-section>
</template>

<style scoped>
.source__note {
  font-size: 0.9rem;
  color: var(--color-ash);
}

.source__list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0.35rem 2rem;
  margin: 0;
  padding: 0;
  list-style: none;
}

@media (max-width: 767px) {
  .source__list {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
