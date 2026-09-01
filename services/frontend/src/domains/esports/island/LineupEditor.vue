<script lang="ts" setup>
import {computed, ref, watch} from "vue"
import ConfirmDialog from "@/components/island/ConfirmDialog.vue"
import IslandDialog from "@/components/island/IslandDialog.vue"
import ImagePicker from "@/components/island/ImagePicker.vue"
import IslandChoice from "@/components/island/IslandChoice.vue"
import IslandPicker from "@/components/island/IslandPicker.vue"
import LineupSource from "./LineupSource.vue"
import {
  addToRoster,
  dropRosterEntry,
  dropTeam,
  saveTeamOrReason,
  fieldTeamInSeason,
  linkRosterMember,
  loadRoster,
  loadTeamSeasons,
  loadTeams,
  saveRosterEntry,
  saveTeamAs,
  unfieldTeamFromSeason,
  type EsportsImage,
  type Fielding,
  type GameCode,
  type Team,
  type RosterEntry,
  type Season,
  type TeamRole,
} from "../adapters/esports"
import {loadMembers, type Member} from "@/domains/user"
import {countOf} from "../copy"
import {reasonFor} from "../refusals"
import {FileType, TeamRole as TeamRoleEnum} from "@/services/api"

/**
 * Who played for one team in one season, and what is said about each of them.
 *
 * It opens over the page, the same way adding a team and editing a season do. A form is a
 * form wherever it is put, and a band that rearranges itself around one reads as the page
 * coming apart rather than as something being filled in.
 *
 * Everything is held here until it is saved, so a line-up is published as one answer rather
 * than as a series of half-finished ones. A season is edited on its own: the same team in
 * another season is a different line-up and is left alone.
 */
defineOptions({name: "LineupEditor"})

/** The parts a roster is grouped by, from the api's own enum rather than a list kept in step. */
const PARTS: Array<{value: TeamRole; label: string}> = [
  {value: TeamRoleEnum.PLAYER, label: "Player"},
  {value: TeamRoleEnum.SUBSTITUTE, label: "Substitute"},
  {value: TeamRoleEnum.COACH, label: "Coach"},
]

const DESCRIPTION_CAP = 280

interface Row {
  /** The entry this row stands for, or nothing where it is somebody being added. */
  id: number | null
  handle: string
  role: TeamRole
  roleTitle: string
  description: string
  userId: number | null
  /**
   * The name recorded for them, which is what tells an admin who a handle belongs to.
   *
   * Recording it is not publishing it: the api puts a name on the public page only for a
   * member who has said it may be shown, so what is written here reaches the page only
   * where that holds.
   */
  displayName: string
  /** This entry's picture, or nothing where none was uploaded. */
  icon: EsportsImage | null
}

const props = defineProps<{
  open: boolean
  /** The game this line-up was played in, which the fielding names rather than the team. */
  game: GameCode
  teamId: number | null
  teamName: string
  /** Teams already fielded in this game this season, which there is nothing to add. */
  alreadyFielded?: number[]
  /** Where the team's banner is served, so the same dialog can replace it. */
  teamBanner?: EsportsImage | null
  /** Where the team's icon is served, so the same dialog can replace it. */
  teamIcon?: EsportsImage | null
  season: Season | null
  accent?: string
}>()

const emit = defineEmits<{
  (event: "update:open", open: boolean): void
  (event: "saved"): void
  (event: "removed"): void
}>()

/**
 * The team's own name and banner, which belong to it in every season rather than to this one.
 * They live here because this is the dialog a team is opened from, and they are marked as
 * what they are so a rename does not read as a change to one season's line-up.
 */
const draftName = ref("")

const rows = ref<Row[]>([])
const removed = ref<number[]>([])
const members = ref<Member[]>([])
const memberSearch = ref<Record<number, string>>({})
const failure = ref<string | null>(null)
const saving = ref(false)
const loading = ref(false)

/*
 * Declared with the rest of the state rather than beside what reads it: the watcher below
 * runs immediately, which is during setup, so anything it touches has to exist by then or it
 * throws before it has done anything.
 */
const droppingTeam = ref(false)
const playedIn = ref<number | null>(null)
const teamFailure = ref<string | null>(null)
const removingTeam = ref(false)

/*
 * Dropping the team from the shown season, which is the lesser of the two removals and is
 * asked for here rather than from the slice: the band says what a season holds, and it says
 * it without carrying a way to take things out of it.
 */
const droppingFromSeason = ref(false)
const seasonFailure = ref<string | null>(null)
const leavingSeason = ref(false)

const rowOf = (entry: RosterEntry): Row => ({
  id: entry.id,
  handle: entry.handle,
  role: entry.role,
  roleTitle: entry.roleTitle ?? "",
  description: entry.description ?? "",
  userId: entry.userId ?? null,
  displayName: entry.displayName ?? "",
  icon: entry.icon ?? null,
})

/**
 * The team's own two pictures, held like everything else here until the save.
 *
 * Choosing one puts it into storage straight away — the picker has to draw it, and it cannot
 * draw bytes nobody has stored — but nothing is on the team until Save. Cancelling therefore
 * leaves both as they were along with the name and the line-up, rather than keeping a picture
 * and discarding the rest of the form.
 */
const banner = ref<EsportsImage | null>(null)
const icon = ref<EsportsImage | null>(null)

const stageIcon = (index: number, picture: EsportsImage | null) => {
  const row = rows.value[index]
  if (row) row.icon = picture
}

/**
 * Making a team rather than correcting one, which is what having no team to open on means.
 *
 * The same editor either way: a squad is published as one answer, and that is as true of the
 * first answer as of the ones after it.
 */
const adding = computed(() => props.teamId == null)

/**
 * Which kind of adding, asked at the top because it decides what the rest of this is.
 *
 * A team that played before is picked out of the association's pool and brings its line-up; a
 * team that does not exist yet is described here. One way in from the band and the choice made
 * inside it: two plusses would read as two different things to do.
 */
type Kind = "played-before" | "new-team"
const kind = ref<Kind>("played-before")

/** The team picked out of the pool, which is fielded rather than made. */
const pool = ref<Team[]>([])
const picked = ref<Team | null>(null)
const fieldingNow = ref(false)
const carried = ref<{from: Fielding | null; entries: RosterEntry[]}>({from: null, entries: []})
const carriedSize = ref(0)

/** Every team the association has, less the ones already playing this game this season. */
const poolOffered = computed(() =>
  pool.value.filter(team => !(props.alreadyFielded ?? []).includes(team.id)))

const onCarried = (next: {from: Fielding | null; entries: RosterEntry[]}) => {
  if (next.from !== carried.value.from) carriedSize.value = next.entries.length
  carried.value = next
}

/**
 * Fields the team picked, with whichever of its people were kept.
 *
 * Everybody kept is the one request that carries them; anything less is carried by hand, so
 * nobody who was dropped is written down and then deleted.
 */
const fieldPicked = async () => {
  const team = picked.value
  const seasonId = props.season?.id
  if (!team || seasonId == null || fieldingNow.value) return
  fieldingNow.value = true
  failure.value = null
  try {
    const from = carried.value.from
    const whole = from != null && carried.value.entries.length === carriedSize.value
    const done = await fieldTeamInSeason(
      team.id, props.game, seasonId, false, null,
      whole && from ? {game: from.game, seasonId: from.season.id} : null,
    )
    if (!done) {
      failure.value = "That team could not be fielded this season."
      return
    }
    if (!whole) {
      for (const entry of carried.value.entries) {
        await addToRoster(team.id, {
          game: props.game,
          seasonId,
          handle: entry.handle,
          role: entry.role,
          userId: entry.userId,
          displayName: entry.displayName,
          roleTitle: entry.roleTitle,
          description: entry.description,
        })
      }
    }
    emit("saved")
    emit("update:open", false)
  } finally {
    fieldingNow.value = false
  }
}

watch(() => [props.open, props.teamId, props.season?.id] as const, async ([open, teamId, seasonId]) => {
  if (!open || seasonId == null) return
  loading.value = true
  failure.value = null
  removed.value = []
  memberSearch.value = {}
  draftName.value = props.teamName
  banner.value = props.teamBanner ?? null
  icon.value = props.teamIcon ?? null
  playedIn.value = null
  try {
    // Nothing to read for a team that does not exist yet: it opens on an empty form and one
    // empty row, so the first thing to do is the obvious thing.
    if (teamId == null) {
      kind.value = "played-before"
      picked.value = null
      carried.value = {from: null, entries: []}
      if (pool.value.length === 0) pool.value = await loadTeams()
    }
    rows.value = teamId == null
      ? [emptyRow()]
      : (await loadRoster(teamId, props.game, seasonId))
        .slice().sort((a, b) => a.sortIndex - b.sortIndex).map(rowOf)
    if (members.value.length === 0) members.value = await loadMembers()
  } finally {
    loading.value = false
  }
}, {immediate: true})

/**
 * Rows filled from somebody else's line-up.
 *
 * Only offered while a team is being made: correcting a line-up is about the people already on
 * it, and dropping another squad into it would be a different act wearing the same clothes.
 * Nothing is preselected — a team being made has no history of its own, and the point is to
 * start from the people who have played together somewhere else.
 */
const startFrom = (carried: {from: Fielding | null; entries: RosterEntry[]}) => {
  const brought = carried.entries.map(entry => ({
    id: null,
    handle: entry.handle,
    role: entry.role,
    roleTitle: entry.roleTitle ?? "",
    description: entry.description ?? "",
    userId: entry.userId ?? null,
    displayName: entry.displayName ?? "",
    icon: entry.icon ?? null,
  }))
  // The empty row stays at the end so the next person can be typed straight in.
  rows.value = [...brought, emptyRow()]
}

const emptyRow = (): Row => ({
  id: null, handle: "", role: TeamRoleEnum.PLAYER, roleTitle: "", description: "", userId: null,
  displayName: "", icon: null,
})

const add = () => {
  rows.value = [...rows.value, emptyRow()]
}

const dropping = ref<number | null>(null)

/**
 * Taking somebody off asks first where they are already on the roster. A row added a moment
 * ago and not yet saved is nobody's record, so it simply goes.
 */
const askToRemove = (index: number) => {
  if (rows.value[index]?.id == null) remove(index)
  else dropping.value = index
}

const droppingName = computed(() => {
  const row = dropping.value == null ? null : rows.value[dropping.value]
  return row?.handle?.trim() || "this player"
})

const remove = (index: number) => {
  const row = rows.value[index]
  if (row?.id != null) removed.value = [...removed.value, row.id]
  rows.value = rows.value.filter((_, at) => at !== index)
  dropping.value = null
}

/** Order is the order they are listed in, so moving a row is the whole of setting it. */
const move = (index: number, by: number) => {
  const to = index + by
  if (to < 0 || to >= rows.value.length) return
  const next = [...rows.value]
  const [row] = next.splice(index, 1)
  if (row) next.splice(to, 0, row)
  rows.value = next
}

const nameOf = (userId: number | null) =>
  (userId == null ? null : members.value.find(one => one.id === userId)?.name ?? `Member ${userId}`)

const matches = (index: number) => {
  const term = (memberSearch.value[index] ?? "").trim().toLowerCase()
  if (term.length < 2) return []
  return members.value
    .filter(one => one.name.toLowerCase().includes(term) || (one.email ?? "").toLowerCase().includes(term))
    .slice(0, 6)
}

const attach = (index: number, userId: number | null) => {
  const row = rows.value[index]
  if (!row) return
  row.userId = userId
  memberSearch.value = {...memberSearch.value, [index]: ""}
}

/**
 * A team being made may be published with nobody on it -- fielding it and settling the squad
 * are the two decisions this whole feature exists to separate -- so an empty row is not an
 * unfinished one. A row somebody typed into has to name somebody.
 */
const complete = computed(() =>
  draftName.value.trim() !== ""
  && rows.value.every(row => row.handle.trim() !== "" || (adding.value && isBlank(row))))

const isBlank = (row: Row) =>
  row.handle.trim() === "" && row.displayName.trim() === "" && row.roleTitle.trim() === ""
  && row.description.trim() === "" && row.userId == null && row.icon == null


/** How many seasons the team played, so removing it altogether can say what that means. */
const askToRemoveTeam = async () => {
  if (props.teamId == null) return
  teamFailure.value = null
  playedIn.value = (await loadTeamSeasons(props.teamId)).length
  droppingTeam.value = true
}

const teamQuestion = computed(() => {
  const seasons = playedIn.value
  const played = seasons == null || seasons === 1 ? "one season" : `${seasons} seasons`
  return `${props.teamName} played ${played}. Deleting the team takes it out of all of them, `
    + "which is not the same as removing it from the shown season."
})

const removeTeam = async () => {
  const teamId = props.teamId
  if (teamId == null || removingTeam.value) return
  removingTeam.value = true
  teamFailure.value = null
  try {
    const result = await dropTeam(teamId)
    if (!result.ok) {
      teamFailure.value = result.reason
      return
    }
    droppingTeam.value = false
    emit("removed")
    emit("update:open", false)
  } finally {
    removingTeam.value = false
  }
}

/**
 * Dropping the team from this season only, which is not removing the team.
 *
 * Said in the same breath as the count, because the two removals are a sentence apart and
 * the difference between them is the whole point of asking.
 */
const seasonQuestion = computed(() => {
  const played = countOf(rows.value.length, "person", "people")
  const season = props.season?.name ?? "this season"
  return `${props.teamName} played ${season} with ${played}. Removing it from this season `
    + "leaves the team, and the other seasons it played, as they are."
})

const dropFromSeason = async () => {
  const teamId = props.teamId
  const seasonId = props.season?.id
  if (teamId == null || seasonId == null || leavingSeason.value) return
  leavingSeason.value = true
  seasonFailure.value = null
  try {
    const result = await unfieldTeamFromSeason(teamId, props.game, seasonId)
    if (!result.ok) {
      seasonFailure.value = result.reason
      return
    }
    droppingFromSeason.value = false
    emit("removed")
    emit("update:open", false)
  } finally {
    leavingSeason.value = false
  }
}

const reasonFrom = (error: unknown): string =>
  reasonFor(error, "The line-up could not be saved.")

const submit = async () => {
  const seasonId = props.season?.id
  if (!complete.value || saving.value || seasonId == null) return
  saving.value = true
  failure.value = null
  try {
    // The team itself first — its name and its logo. A line-up written against a team that
    // was meant to be renamed would leave the rename half-applied if anything after it failed;
    // a team being made has to exist before anything can be written against it at all, which
    // is why nothing above this point writes.
    const saved = props.teamId == null
      ? await saveTeamOrReason({name: draftName.value.trim(), icon: icon.value?.path ?? null})
      : await saveTeamAs(props.teamId, {
        name: draftName.value.trim(),
        icon: icon.value?.path ?? null,
      })
    if (!saved.ok) {
      failure.value = saved.reason
      return
    }
    const teamId = props.teamId ?? saved.team?.id
    if (teamId == null) {
      failure.value = "The team could not be saved."
      return
    }

    // The art belongs to this season's fielding rather than to the team, so it is written
    // there — the same team is drawn with its own picture in every other game it plays.
    await fieldTeamInSeason(teamId, props.game, seasonId, false, banner.value?.path ?? null)

    for (const id of removed.value) {
      const gone = await dropRosterEntry(id)
      if (!gone.ok) {
        failure.value = gone.reason
        return
      }
    }

    for (const [index, row] of rows.value.entries()) {
      const shared = {
        handle: row.handle.trim(),
        role: row.role,
        roleTitle: row.roleTitle.trim() || null,
        description: row.description.trim() || null,
        sortIndex: index,
        icon: row.icon?.path ?? null,
      }
      if (isBlank(row)) continue
      if (row.id == null) {
        await addToRoster(teamId, {
          game: props.game,
          seasonId,
          ...shared,
          userId: row.userId,
          displayName: row.displayName.trim() || null,
        })
      } else {
        await saveRosterEntry(row.id, {...shared, displayName: row.displayName.trim() || null})
        await linkRosterMember(row.id, row.userId)
      }
    }

    emit("saved")
    emit("update:open", false)
  } catch (error) {
    failure.value = reasonFrom(error)
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <island-dialog
    :accent="accent"
    :open="open"
    testid="lineup-dialog"
    :title="adding
      ? (season ? `A new team in ${season.name}` : 'A new team')
      : (season ? `${teamName} in ${season.name}` : teamName)"
    @update:open="emit('update:open', $event)"
  >
    <div
      class="lineup"
      data-testid="lineup-editor"
    >
      <!--
        The team itself, marked as belonging to every season rather than to this one, so a
        rename does not read as a change to the line-up underneath it.
      -->
      <!-- Asked first, because the answer decides what the rest of this dialog is. -->
      <island-choice
        v-if="adding"
        v-model="kind"
        :options="[
          {key: 'played-before', label: 'An existing team'},
          {key: 'new-team', label: 'A new team'},
        ]"
        testid-prefix="lineup-kind"
      />

      <template v-if="adding && kind === 'played-before'">
        <island-picker
          empty-note="Every team the association has already plays this game this season."
          :options="poolOffered.map(one => ({key: String(one.id), label: one.name}))"
          placeholder="Search every team"
          :selected-key="picked ? String(picked.id) : null"
          testid-prefix="field-team"
          @pick="key => picked = poolOffered.find(one => String(one.id) === key) ?? null"
        />

        <template v-if="picked != null">
          <lineup-source
            :game="game"
            :season-id="season?.id ?? null"
            :team-id="picked.id"
            @update:carried="onCarried"
          />
          <p
            v-if="failure"
            class="lineup__failure"
            data-testid="lineup-failure"
            role="alert"
          >
            {{ failure }}
          </p>
        </template>
      </template>

      <template v-else>
        <fieldset class="lineup__team">
          <legend class="lineup__legend">
            The team
          </legend>
          <div class="lineup__line">
            <input
              v-model="draftName"
              aria-label="Team name"
              class="lineup__input"
              data-testid="lineup-team-name"
              maxlength="128"
              placeholder="Team name"
              type="text"
            >
          </div>
          <!-- Both held until the save, like the field above them. Side by side, because they
               are decided together and are the two halves of how a team is drawn. They wrap
               onto their own lines where there is no room for both. -->
          <div class="lineup__pictures">
            <image-picker
              :kind="FileType.TEAM_BANNER"
              label="Banner"
              :picture="banner"
              testid="lineup-team-banner"
              @update:picture="banner = $event"
            />
            <image-picker
              :kind="FileType.TEAM_ICON"
              label="Icon"
              :picture="icon"
              testid="lineup-team-icon"
              @update:picture="icon = $event"
            />
          </div>
          <p
            v-if="teamFailure"
            class="lineup__failure"
            data-testid="lineup-team-failure"
          >
            {{ teamFailure }}
          </p>
        </fieldset>

        <!-- Only while a team is being made: correcting a line-up is about the people already
           on it, and dropping another squad into it would be a different act in the same
           clothes. -->
        <lineup-source
          v-if="adding"
          :game="game"
          :season-id="season?.id ?? null"
          @update:carried="startFrom"
        />

        <!-- Named, so that the block above it reading "The team" is plainly about something
             else: one belongs to the team in every season, this one only to this season. -->
        <h3
          class="lineup__legend lineup__legend--heading"
          data-testid="lineup-season-heading"
        >
          This season's line-up
        </h3>

        <p
          v-if="loading"
          class="lineup__note"
          data-testid="lineup-loading"
        >
          Reading the line-up…
        </p>

        <p
          v-else-if="rows.length === 0"
          class="lineup__note"
          data-testid="lineup-empty"
        >
          Nobody has played for this team this season yet.
        </p>

        <div
          v-for="(row, index) in rows"
          :key="row.id ?? `new-${index}`"
          class="lineup__row"
          :data-testid="`lineup-row-${row.id ?? `new-${index}`}`"
        >
          <image-picker
            :kind="FileType.ROSTER_ICON"
            label="Icon"
            :picture="row.icon"
            :testid="`lineup-icon-${index}`"
            @update:picture="stageIcon(index, $event)"
          />
          <div class="lineup__line">
            <input
              v-model="row.handle"
              aria-label="Handle"
              class="lineup__input lineup__input--handle"
              :data-testid="`lineup-handle-${index}`"
              maxlength="128"
              placeholder="Handle"
              type="text"
            >
            <select
              v-model="row.role"
              aria-label="Part"
              class="lineup__input lineup__input--part"
              :data-testid="`lineup-role-${index}`"
            >
              <option
                v-for="part in PARTS"
                :key="part.value"
                :value="part.value"
              >
                {{ part.label }}
              </option>
            </select>
            <input
              v-model="row.roleTitle"
              aria-label="In their own words"
              class="lineup__input"
              :data-testid="`lineup-title-${index}`"
              maxlength="64"
              placeholder="Captain, in-game leader…"
              type="text"
            >
            <span class="lineup__order">
              <button
                :aria-label="`Move ${row.handle || 'this player'} up`"
                class="lineup__step"
                :data-testid="`lineup-up-${index}`"
                :disabled="index === 0"
                type="button"
                @click="move(index, -1)"
              >↑</button>
              <button
                :aria-label="`Move ${row.handle || 'this player'} down`"
                class="lineup__step"
                :data-testid="`lineup-down-${index}`"
                :disabled="index === rows.length - 1"
                type="button"
                @click="move(index, 1)"
              >↓</button>
              <button
                :aria-label="`Remove ${row.handle || 'this player'}`"
                class="lineup__step lineup__step--drop"
                :data-testid="`lineup-remove-${index}`"
                type="button"
                @click="askToRemove(index)"
              >×</button>
            </span>
          </div>

          <div class="lineup__line">
            <label class="lineup__note-field">
              <textarea
                v-model="row.description"
                aria-label="A word about them"
                class="lineup__input lineup__input--note"
                :data-testid="`lineup-description-${index}`"
                :maxlength="DESCRIPTION_CAP"
                placeholder="A word about them"
                rows="2"
              />
              <span
                class="lineup__count"
                :class="{'lineup__count--full': row.description.length === DESCRIPTION_CAP}"
                :data-testid="`lineup-count-${index}`"
              >{{ row.description.length }}/{{ DESCRIPTION_CAP }}</span>
            </label>
          </div>

          <div class="lineup__line">
            <label class="lineup__note-field">
              <input
                v-model="row.displayName"
                :aria-label="`Recorded name for ${row.handle || 'this player'}`"
                class="lineup__input"
                :data-testid="`lineup-name-${index}`"
                maxlength="128"
                placeholder="Recorded name — shown on the page only with their consent"
                type="text"
              >
            </label>
          </div>

          <div class="lineup__line lineup__line--member">
            <span
              v-if="row.userId != null"
              class="lineup__attached"
              :data-testid="`lineup-member-${index}`"
            >
              {{ nameOf(row.userId) }}
              <button
                :aria-label="`Detach ${nameOf(row.userId)}`"
                class="lineup__step"
                :data-testid="`lineup-detach-${index}`"
                type="button"
                @click="attach(index, null)"
              >×</button>
            </span>
            <template v-else>
              <input
                :aria-label="`Attach ${row.handle || 'this player'} to a member`"
                class="lineup__input lineup__input--search"
                :data-testid="`lineup-search-${index}`"
                placeholder="No account — search a member"
                type="text"
                :value="memberSearch[index] ?? ''"
                @input="memberSearch = {...memberSearch, [index]: ($event.target as HTMLInputElement).value}"
              >
              <ul
                v-if="matches(index).length > 0"
                class="lineup__matches"
                :data-testid="`lineup-matches-${index}`"
              >
                <li
                  v-for="member in matches(index)"
                  :key="member.id"
                >
                  <button
                    class="lineup__match"
                    :data-testid="`lineup-match-${member.id}`"
                    type="button"
                    @click="attach(index, member.id)"
                  >
                    {{ member.name }}
                  </button>
                </li>
              </ul>
            </template>
          </div>
        </div>

        <button
          class="lineup__add"
          data-testid="lineup-add"
          type="button"
          @click="add"
        >
          Add somebody
        </button>

        <p
          v-if="failure"
          class="lineup__failure"
          data-testid="lineup-failure"
          role="alert"
        >
          {{ failure }}
        </p>
      </template>
    </div>

    <!--
      Every button that leaves this dialog, in the footer: the removals are the two most
      consequential things in here and they used to sit in the run of the form, where a long
      line-up scrolled them past the fields they had nothing to do with.
    -->
    <template #footer>
      <div
        v-if="!(adding && kind === 'played-before') || picked != null"
        class="lineup__actions"
      >
        <template v-if="adding && kind === 'played-before'">
          <div class="lineup__group">
            <button
              class="lineup__button lineup__button--ghost"
              data-testid="lineup-cancel"
              type="button"
              @click="emit('update:open', false)"
            >
              Cancel
            </button>
            <button
              class="lineup__button lineup__button--go"
              data-testid="field-team-confirm"
              :disabled="fieldingNow"
              type="button"
              @click="fieldPicked"
            >
              {{ fieldingNow ? "Fielding" : `Field ${picked!.name}` }}
            </button>
          </div>
        </template>

        <template v-else>
          <!-- The lesser removal first: it is the one asked for more often, and the one meant.
             Neither is offered while a team is being made: there is nothing yet to drop from
             a season or to remove, and Cancel is what leaves without writing. -->
          <div
            v-if="!adding"
            class="lineup__group"
          >
            <button
              v-if="season"
              class="lineup__button lineup__button--drop"
              data-testid="lineup-drop-from-season"
              type="button"
              @click="droppingFromSeason = true"
            >
              Remove team
            </button>
            <button
              class="lineup__button lineup__button--drop"
              data-testid="lineup-remove-team"
              type="button"
              @click="askToRemoveTeam"
            >
              Delete
            </button>
          </div>
          <div class="lineup__group">
            <button
              class="lineup__button lineup__button--ghost"
              data-testid="lineup-cancel"
              type="button"
              @click="emit('update:open', false)"
            >
              Cancel
            </button>
            <button
              class="lineup__button lineup__button--go"
              data-testid="lineup-save"
              :disabled="!complete || saving"
              type="button"
              @click="submit"
            >
              {{ saving ? "Saving" : adding ? "Create" : "Save" }}
            </button>
          </div>
        </template>
      </div>
    </template>
  </island-dialog>

  <confirm-dialog
    :accent="accent"
    confirm-label="Remove from this season"
    :failure="seasonFailure"
    :open="droppingFromSeason"
    :question="seasonQuestion"
    testid="team-drop-dialog"
    title="Remove this team from the season?"
    :working="leavingSeason"
    @confirm="dropFromSeason"
    @update:open="droppingFromSeason = $event"
  />

  <confirm-dialog
    :accent="accent"
    confirm-label="Delete the team"
    :failure="teamFailure"
    :open="droppingTeam"
    :question="teamQuestion"
    testid="team-remove-dialog"
    title="Delete this team altogether?"
    :working="removingTeam"
    working-label="Deleting"
    @confirm="removeTeam"
    @update:open="droppingTeam = $event"
  />

  <confirm-dialog
    :accent="accent"
    confirm-label="Take them off"
    :open="dropping !== null"
    :question="`${droppingName} comes off this season's line-up when it is saved. `
      + `The seasons they played before are untouched.`"
    testid="lineup-remove-dialog"
    title="Take this player off?"
    @confirm="dropping !== null && remove(dropping)"
    @update:open="dropping = $event ? dropping : null"
  />
</template>

<style>
/* Unscoped: the island's own reset styles these controls, and the dialog portals its content
   out of this component's subtree. */
.lineup {
  display: flex;
  flex-direction: column;
  gap: 0.7rem;
}

.lineup__team {
  display: flex;
  flex-direction: column;
  gap: 0.7rem;
  margin: 0;
  padding: 0.9rem 1rem 1rem;
  border: 1px solid color-mix(in oklab, var(--color-chalk) 10%, transparent);
}

.lineup__pictures {
  display: flex;
  flex-wrap: wrap;
  gap: 1.1rem;
  align-items: flex-end;
}

/* Standing on its own rather than notched into a fieldset's border, so it needs the spacing
   the border was providing. */
.lineup__legend--heading {
  margin: 0.5rem 0 0;
}

.lineup__legend {
  padding: 0 0.35rem;
  font-family: var(--font-display);
  font-size: 0.62rem;
  color: var(--color-ash);
  letter-spacing: 0.1em;
  text-transform: uppercase;
}

.lineup__button--drop {
  background: color-mix(in oklab, var(--color-danger-tint) 18%, transparent);
  color: var(--color-danger-ink);
}

.lineup__button--drop:hover {
  background: color-mix(in oklab, var(--color-danger-tint) 34%, transparent);
  color: var(--color-danger-ink-strong);
}

.lineup__row {
  display: flex;
  flex-direction: column;
  gap: 0.45rem;
  padding: 0.7rem 0.8rem;
  background-color: color-mix(in oklab, var(--color-chalk) 5%, transparent);
  border-left: 2px solid var(--dialog-accent, var(--color-brand));
}

.lineup__line {
  display: flex;
  flex-wrap: wrap;
  gap: 0.55rem;
  align-items: flex-end;
}

.lineup__line--member {
  flex-direction: column;
  gap: 0.25rem;
}

.lineup__input {
  flex: 1;
  min-width: 0;
  padding: 0.5rem 0.7rem;
  font-family: inherit;
  color: var(--color-chalk);
  background-color: color-mix(in oklab, var(--color-chalk) 7%, transparent);
  border: 0;
  font-size: 0.88rem;
}

.lineup__input--handle {
  flex: 1.2;
}

.lineup__input--part {
  flex: 0 0 7.5rem;
}

.lineup__input--note {
  resize: vertical;
}

.lineup__input--search {
  width: 100%;
}

.lineup__note-field {
  position: relative;
  display: flex;
  width: 100%;
}

.lineup__count {
  position: absolute;
  right: 0.4rem;
  bottom: 0.3rem;
  color: var(--color-ash);
  font-size: 0.68rem;
}

.lineup__count--full {
  color: var(--color-warning);
}

.lineup__order {
  display: flex;
  gap: 0.2rem;
}

.lineup__step {
  width: 1.6rem;
  height: 1.9rem;
  background: var(--color-raised);
  border: 0;
  color: var(--color-ash);
  cursor: pointer;
  font-size: 0.85rem;
}

.lineup__step:disabled {
  cursor: not-allowed;
  opacity: 0.35;
}

.lineup__step--drop:hover {
  background: var(--color-danger-ground);
  color: var(--color-chalk);
}

.lineup__attached {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  color: var(--color-chalk);
  font-size: 0.85rem;
}

.lineup__matches {
  display: flex;
  flex-direction: column;
  gap: 0.1rem;
  margin: 0;
  padding: 0;
  list-style: none;
}

.lineup__match {
  width: 100%;
  padding: 0.3rem 0.45rem;
  background: var(--color-surface);
  border: 0;
  color: var(--color-chalk);
  cursor: pointer;
  font-family: inherit;
  font-size: 0.85rem;
  text-align: left;
}

.lineup__match:hover,
.lineup__match:focus-visible {
  background: color-mix(in oklab, var(--dialog-accent, var(--color-brand)) 30%, var(--color-surface));
}

.lineup__add {
  align-self: flex-start;
  padding: 0.35rem 0.7rem;
  background: none;
  border: 1px dashed color-mix(in oklab, var(--color-chalk) 22%, transparent);
  color: var(--color-ash);
  cursor: pointer;
  font-family: inherit;
  font-size: 0.82rem;
}

.lineup__note,
.lineup__failure {
  margin: 0;
  font-size: 0.85rem;
}

.lineup__note {
  color: var(--color-ash);
}

.lineup__failure {
  color: var(--color-danger);
}

/* Side by side, aligned along the bottom so a wide banner and a square logo share a baseline. */
.lineup__pictures {
  display: flex;
  flex-wrap: wrap;
  gap: 1.1rem;
  align-items: flex-end;
  padding-top: 0.15rem;
}

/*
 * Its own rule and its own spacing: see the footer in IslandDialog.
 *
 * The two removals and the two ways out do not share a line at this width, so the row breaks
 * between the groups rather than between buttons -- removals on the left of one line, the way
 * out on the right of the next, which is a shape rather than an overflow.
 */
.lineup__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.55rem;
  align-items: center;
  justify-content: space-between;
  row-gap: 0.55rem;
  margin-top: 1rem;
  padding-top: 0.85rem;
  border-top: 1px solid color-mix(in oklab, var(--color-chalk) 12%, transparent);
}

/*
 * The removals lead and the two ways out of the form close the row, wherever the row breaks.
 * Four buttons do not fit the width of a dialog on a phone, and a Save that wrapped to the
 * left under a Remove read as the pair of them belonging together.
 */
.lineup__group {
  display: flex;
  gap: 0.6rem;
}

.lineup__group:last-child {
  margin-left: auto;
}

.lineup__button {
  padding: 0.45rem 1.1rem;
  border: 0;
  clip-path: polygon(10px 0, 100% 0, calc(100% - 10px) 100%, 0 100%);
  font-family: "Fugaz One", system-ui, sans-serif;
  font-size: 0.8rem;
  font-style: italic;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  cursor: pointer;
}

.lineup__button--ghost {
  background: var(--color-raised);
  color: var(--color-ash);
}

.lineup__button--go {
  background: var(--dialog-accent, var(--color-brand));
  color: var(--color-void);
}

.lineup__button:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}
</style>
