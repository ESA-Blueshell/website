<script lang="ts" setup>
import {computed, ref, watch} from "vue"
import ConfirmDialog from "./ConfirmDialog.vue"
import ImagePicker from "./ImagePicker.vue"
import {
  addToRoster,
  clearRosterIcon,
  clearTeamPoster,
  dropRosterEntry,
  dropTeam,
  linkRosterMember,
  loadMembers,
  loadRoster,
  loadTeamSeasons,
  saveRosterEntry,
  renameTeam,
  setRosterIcon,
  setTeamPoster,
  type EsportsImage,
  type Member,
  type RosterEntry,
  type Season,
  type TeamRole,
} from "../adapters/esports"
import {TeamRole as TeamRoleEnum} from "@/services/api"

/**
 * Who played for one team in one season, and what is said about each of them.
 *
 * It sits in the slice that shows the team rather than over the page: what is being changed
 * is the thing on the page, and it stays the thing on the page while it changes.
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
  teamId: number | null
  teamName: string
  /** The team's banner asset, so the same dialog can change it. */
  teamImage?: string | null
  /** Where the team's uploaded poster is served, so the same dialog can replace it. */
  teamPoster?: EsportsImage | null
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
const draftImage = ref("")

const rows = ref<Row[]>([])
const removed = ref<number[]>([])
const members = ref<Member[]>([])
const memberSearch = ref<Record<number, string>>({})
const failure = ref<string | null>(null)
const saving = ref(false)
const loading = ref(false)

/*
 * Declared with the rest of the state rather than beside what reads it: the watcher below
 * runs immediately, which is during setup, and in the band it arrives already open — so
 * anything it touches has to exist by then or it throws before it has done anything.
 */
const droppingTeam = ref(false)
const playedIn = ref<number | null>(null)
const teamFailure = ref<string | null>(null)
const removingTeam = ref(false)

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
 * The poster and the icons, which unlike everything else here take effect as they are chosen.
 *
 * A file is not a draft. Holding one until the save would mean keeping the bytes in the page
 * and uploading them at a moment the visitor has stopped thinking about the picture, and a
 * failure then would be reported against a save that otherwise worked.
 */
const poster = ref<EsportsImage | null>(null)
const posterBusy = ref(false)
const iconBusy = ref<number | null>(null)

/*
 * The poster is followed on its own rather than only being read when the editor opens.
 * Uploading one reloads the page underneath, which re-runs the watcher below and would
 * otherwise reset the picker to what the prop said before the upload landed.
 */
watch(() => props.teamPoster, (image) => {
  poster.value = image ?? null
})

const uploadPoster = async (file: File) => {
  const teamId = props.teamId
  if (teamId == null || posterBusy.value) return
  posterBusy.value = true
  teamFailure.value = null
  try {
    poster.value = (await setTeamPoster(teamId, file))?.poster ?? null
    emit("saved")
  } catch {
    teamFailure.value = "That poster could not be uploaded."
  } finally {
    posterBusy.value = false
  }
}

const removePoster = async () => {
  const teamId = props.teamId
  if (teamId == null || posterBusy.value) return
  posterBusy.value = true
  teamFailure.value = null
  try {
    poster.value = (await clearTeamPoster(teamId))?.poster ?? null
    emit("saved")
  } catch {
    teamFailure.value = "That poster could not be removed."
  } finally {
    posterBusy.value = false
  }
}

const uploadIcon = async (index: number, file: File) => {
  const row = rows.value[index]
  // A row nobody has saved yet has no entry to hang a picture on.
  if (!row?.id || iconBusy.value !== null) return
  iconBusy.value = row.id
  failure.value = null
  try {
    row.icon = (await setRosterIcon(row.id, file))?.icon ?? null
    emit("saved")
  } catch {
    failure.value = "That picture could not be uploaded."
  } finally {
    iconBusy.value = null
  }
}

const removeIcon = async (index: number) => {
  const row = rows.value[index]
  if (!row?.id || iconBusy.value !== null) return
  iconBusy.value = row.id
  failure.value = null
  try {
    row.icon = (await clearRosterIcon(row.id))?.icon ?? null
    emit("saved")
  } catch {
    failure.value = "That picture could not be removed."
  } finally {
    iconBusy.value = null
  }
}

watch(() => [props.open, props.teamId, props.season?.id] as const, async ([open, teamId, seasonId]) => {
  if (!open || teamId == null || seasonId == null) return
  loading.value = true
  failure.value = null
  removed.value = []
  memberSearch.value = {}
  draftName.value = props.teamName
  draftImage.value = props.teamImage ?? ""
  poster.value = props.teamPoster ?? null
  playedIn.value = null
  try {
    const entries = await loadRoster(teamId, seasonId)
    rows.value = entries.slice().sort((a, b) => a.sortIndex - b.sortIndex).map(rowOf)
    if (members.value.length === 0) members.value = await loadMembers()
  } finally {
    loading.value = false
  }
}, {immediate: true})

const add = () => {
  // No icon: there is no entry to hang one on until this row has been saved.
  rows.value = [...rows.value, {
    id: null, handle: "", role: TeamRoleEnum.PLAYER, roleTitle: "", description: "", userId: null,
    displayName: "", icon: null,
  }]
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

const complete = computed(() =>
  draftName.value.trim() !== "" && rows.value.every(row => row.handle.trim() !== ""))


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
  return `${props.teamName} played ${played}. Removing the team takes it out of all of them, `
    + "which is not the same as dropping it from the season on show."
})

const removeTeam = async () => {
  const teamId = props.teamId
  if (teamId == null || removingTeam.value) return
  removingTeam.value = true
  teamFailure.value = null
  try {
    await dropTeam(teamId)
    droppingTeam.value = false
    emit("removed")
    emit("update:open", false)
  } catch (error) {
    const body = (error as {detail?: string; title?: string})
    teamFailure.value = body?.detail || body?.title || "The team could not be removed."
  } finally {
    removingTeam.value = false
  }
}

const reasonFrom = (error: unknown): string => {
  const body = (error as {detail?: string; title?: string})
  return body?.detail || body?.title || "The line-up could not be saved."
}

const submit = async () => {
  const teamId = props.teamId
  const seasonId = props.season?.id
  if (!complete.value || saving.value || teamId == null || seasonId == null) return
  saving.value = true
  failure.value = null
  try {
    // The team's own name and banner first: a line-up written against a team that was
    // meant to be renamed would leave the rename half-applied if anything after it failed.
    if (draftName.value.trim() !== props.teamName || (draftImage.value.trim() || null) !== (props.teamImage ?? null)) {
      const renamed = await renameTeam(teamId, draftName.value.trim(), draftImage.value.trim() || null)
      if (!renamed.ok) {
        failure.value = renamed.reason
        return
      }
    }

    for (const id of removed.value) await dropRosterEntry(id)

    for (const [index, row] of rows.value.entries()) {
      const shared = {
        handle: row.handle.trim(),
        role: row.role,
        roleTitle: row.roleTitle.trim() || null,
        description: row.description.trim() || null,
        sortIndex: index,
      }
      if (row.id == null) {
        await addToRoster(teamId, {
          seasonId, ...shared, userId: row.userId, displayName: row.displayName.trim() || null,
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
  <div
    class="lineup"
    data-testid="lineup-editor"
  >
    <p class="lineup__where">
      {{ season ? `${teamName} in ${season.name}` : teamName }}
    </p>
    <!--
        The team itself, marked as belonging to every season rather than to this one, so a
        rename does not read as a change to the line-up underneath it.
      -->
    <fieldset class="lineup__team">
      <legend class="lineup__legend">
        The team, in every season
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
        <input
          v-model="draftImage"
          aria-label="Banner"
          class="lineup__input"
          data-testid="lineup-team-image"
          maxlength="255"
          placeholder="Banner asset, e.g. valorantesports1.jpg"
          type="text"
        >
      </div>
      <!-- Applies as it is chosen, unlike the two fields above, which wait for the save. -->
      <image-picker
        :busy="posterBusy"
        label="Poster"
        testid="lineup-team-poster"
        :url="poster?.url ?? null"
        @clear="removePoster"
        @pick="uploadPoster"
      />
      <p
        v-if="teamFailure"
        class="lineup__failure"
        data-testid="lineup-team-failure"
      >
        {{ teamFailure }}
      </p>
    </fieldset>

    <p
      v-if="loading"
      class="lineup__note"
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
        v-if="row.id"
        :busy="iconBusy === row.id"
        label="Picture"
        :testid="`lineup-icon-${index}`"
        :url="row.icon?.url ?? null"
        @clear="removeIcon(index)"
        @pick="uploadIcon(index, $event)"
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

    <div class="lineup__actions">
      <button
        class="lineup__button lineup__button--drop"
        data-testid="lineup-remove-team"
        type="button"
        @click="askToRemoveTeam"
      >
        Remove team
      </button>
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
        {{ saving ? "Saving" : "Save" }}
      </button>
    </div>
  </div>

  <confirm-dialog
    :accent="accent"
    confirm-label="Remove the team"
    :failure="teamFailure"
    :open="droppingTeam"
    :question="teamQuestion"
    testid="team-remove-dialog"
    title="Remove this team altogether?"
    :working="removingTeam"
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
/* Unscoped: the island's own reset styles these controls, and the editor is rendered into
   the band's slot rather than into a subtree of its own. */
.lineup {
  display: flex;
  flex-direction: column;
  gap: 0.7rem;
}

.lineup__team {
  margin: 0;
  padding: 0.55rem;
  border: 1px solid rgb(255 255 255 / 10%);
}

.lineup__legend {
  padding: 0 0.3rem;
  color: #a0a6ac;
  font-size: 0.68rem;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.lineup__button--drop {
  margin-right: auto;
  background: none;
  color: #d98080;
}

.lineup__where {
  margin: 0 0 0.2rem;
  color: #a0a6ac;
  font-family: "Fugaz One", system-ui, sans-serif;
  font-size: 0.82rem;
  font-style: italic;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.lineup__row {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  padding: 0.55rem;
  background: #1c1c1c;
  border-left: 2px solid var(--dialog-accent, #3387fa);
}

.lineup__line {
  display: flex;
  gap: 0.4rem;
  align-items: flex-start;
}

.lineup__line--member {
  flex-direction: column;
  gap: 0.25rem;
}

.lineup__input {
  flex: 1;
  min-width: 0;
  padding: 0.35rem 0.45rem;
  background: #262626;
  border: 1px solid rgb(255 255 255 / 12%);
  color: #f2f4f6;
  font-family: inherit;
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
  color: #a0a6ac;
  font-size: 0.68rem;
}

.lineup__count--full {
  color: #ffb020;
}

.lineup__order {
  display: flex;
  gap: 0.2rem;
}

.lineup__step {
  width: 1.6rem;
  height: 1.9rem;
  background: #2e2e2e;
  border: 0;
  color: #a0a6ac;
  cursor: pointer;
  font-size: 0.85rem;
}

.lineup__step:disabled {
  cursor: not-allowed;
  opacity: 0.35;
}

.lineup__step--drop:hover {
  background: #5b2020;
  color: #f2f4f6;
}

.lineup__attached {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  color: #f2f4f6;
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
  background: #262626;
  border: 0;
  color: #f2f4f6;
  cursor: pointer;
  font-family: inherit;
  font-size: 0.85rem;
  text-align: left;
}

.lineup__match:hover,
.lineup__match:focus-visible {
  background: color-mix(in oklab, var(--dialog-accent, #3387fa) 30%, #262626);
}

.lineup__add {
  align-self: flex-start;
  padding: 0.35rem 0.7rem;
  background: none;
  border: 1px dashed rgb(255 255 255 / 22%);
  color: #a0a6ac;
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
  color: #a0a6ac;
}

.lineup__failure {
  color: #ff6b6b;
}

.lineup__actions {
  display: flex;
  justify-content: flex-end;
  gap: 0.6rem;
  margin-top: 0.35rem;
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
  background: #2e2e2e;
  color: #a0a6ac;
}

.lineup__button--go {
  background: var(--dialog-accent, #3387fa);
  color: #0f1115;
}

.lineup__button:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}
</style>
