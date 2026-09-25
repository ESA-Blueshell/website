<script lang="ts" setup>
import {computed, ref, watch} from "vue"
import ConfirmDialog from "@/components/island/ConfirmDialog.vue"
import EditPage from "@/components/island/EditPage.vue"
import PreviewFrame from "@/components/island/PreviewFrame.vue"
import SliceBand from "@/components/island/SliceBand.vue"
import {srcsetOf} from "@/components/island/pictures"
import ImagePicker from "@/components/island/ImagePicker.vue"
import type {Picture} from "@/components/island/pictures"
import SegmentedChoice from "@/components/island/SegmentedChoice.vue"
import SearchPicker from "@/components/island/SearchPicker.vue"
import CutButton from "@/components/island/CutButton.vue"
import FormControl from "@/components/island/FormControl.vue"
import FormField from "@/components/island/FormField.vue"
import IconButton from "@/components/island/IconButton.vue"
import NoticeBox from "@/components/island/NoticeBox.vue"
import FormFields from "@/components/island/FormFields.vue"
import FormSection from "@/components/island/FormSection.vue"
import LineupSource from "../island/LineupSource.vue"
import TeamRoster from "../island/TeamRoster.vue"
import {forgetCompetitionReads} from "../island/forgetCompetitionReads"
import {
  dropTeam,
  loadRoster,
  loadTeamSeasons,
  loadTeams,
  storePicture,
  unfieldTeamFromSeason,
  type Fielding,
  type GameCode,
  type Team,
  type RosterEntry,
  type Season,
  type TeamRole,
} from "../adapters/esports"
import {
  fieldExistingTeam,
  isBlank,
  publishLineup,
  type DraftEntry,
  type PublishStage,
} from "../adapters/lineup"
import {loadMemberAccounts, type MemberAccount} from "@/domains/user"
import {countOf} from "../copy"
import {FileType, TeamRole as TeamRoleEnum} from "@/services/api"

/**
 * Who played for one team in one season, and what is said about each of them, on its own page with
 * the team's slice drawn beside the form as the game page will draw it.
 *
 * Everything is held here until it is saved, so a line-up
 * is published as one answer rather than as a series of half-finished ones. A season is edited on
 * its own: the same team in another season is a different line-up and is left alone.
 */
defineOptions({name: "TeamEditor"})

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
  icon: Picture | null
}

const props = defineProps<{
  /** The game this line-up was played in, which the fielding names rather than the team. */
  game: GameCode
  teamId: number | null
  teamName: string
  /** Teams already fielded in this game this season, which there is nothing to add. */
  alreadyFielded?: number[]
  /** Where the team's banner is served, so the same page can replace it. */
  teamBanner?: Picture | null
  /** Where the team's icon is served, so the same page can replace it. */
  teamIcon?: Picture | null
  season: Season | null
  accent?: string
  /** Where the page goes back to: the game page it was opened from. */
  back: string
  /** What the game is called, for the page's head. */
  gameName: string
}>()

const emit = defineEmits<{
  (event: "cancel"): void
  (event: "saved"): void
  (event: "removed"): void
}>()

/**
 * The team's own name and banner, which belong to it in every season rather than to this one.
 * They live here because this is the page a team is opened on, and they are marked as
 * what they are so a rename does not read as a change to one season's line-up.
 */
const draftName = ref("")

const rows = ref<Row[]>([])
const removed = ref<number[]>([])
const members = ref<MemberAccount[]>([])
const failure = ref<string | null>(null)
const saving = ref(false)
const loading = ref(false)

/*
 * Set where the roster could not be read. Nothing is saved while it holds: this page writes
 * what it holds over what is recorded, so an unread line-up would be published as an empty one.
 */
const rosterUnknown = ref(false)

/*
 * Set where the accounts could not be read. A search that answers nothing would otherwise read
 * as nobody having an account, and nothing can be attached until a later open reads them.
 */
const membersUnknown = ref(false)

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

/** A row as the writer takes it: the picture goes across as the path a write can name. */
const entryOf = (row: Row): DraftEntry => ({...row, icon: row.icon?.path ?? null})

/**
 * The team's own two pictures, held like everything else here until the save.
 *
 * Choosing one puts it into storage straight away — the picker has to draw it, and it cannot
 * draw bytes nobody has stored — but nothing is on the team until Save. Cancelling therefore
 * leaves both as they were along with the name and the line-up, rather than keeping a picture
 * and discarding the rest of the form.
 */
const banner = ref<Picture | null>(null)
const icon = ref<Picture | null>(null)

/** Which kind of picture each frame stores, which is this domain's to say rather than the picker's. */
const storeTeamBanner = (file: File) => storePicture(file, FileType.TEAM_BANNER)
const storeTeamIcon = (file: File) => storePicture(file, FileType.TEAM_ICON)
const storeRosterIcon = (file: File) => storePicture(file, FileType.ROSTER_ICON)

const stageIcon = (index: number, picture: Picture | null) => {
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
const carried = ref<{from: Fielding | null; entries: RosterEntry[]; unread: boolean}>(
  {from: null, entries: [], unread: false})
const carriedSize = ref(0)

/** Every team the association has, less the ones already playing this game this season. */
const poolOffered = computed(() =>
  pool.value.filter(team => !(props.alreadyFielded ?? []).includes(team.id)))

const onCarried = (next: {from: Fielding | null; entries: RosterEntry[]; unread: boolean}) => {
  if (next.from !== carried.value.from) carriedSize.value = next.entries.length
  carried.value = next
}

/** Fields the team picked, with whichever of its people were kept. */
const fieldPicked = async () => {
  const team = picked.value
  const seasonId = props.season?.id
  if (!team || seasonId == null || fieldingNow.value) return
  fieldingNow.value = true
  failure.value = null
  try {
    const from = carried.value.from
    const entries = carried.value.entries
    const done = await fieldExistingTeam({
      teamId: team.id,
      game: props.game,
      seasonId,
      from: from ? {game: from.game, seasonId: from.season.id} : null,
      entries: entries.map(entry => entryOf(rowOf(entry))),
      sourceSize: carriedSize.value,
      unread: carried.value.unread,
    })
    if (!done.ok) {
      // A refusal partway through the carry leaves the fielding written, so the report says
      // so: closing on "saved" would hide a half-carried line-up. The count names who stopped
      // it, since everyone before them went across.
      const stopped = done.stage === "carry" ? entries[done.written]?.handle : null
      if (done.stage === "source") {
        failure.value = "That line-up could not be read, so nobody can be carried across. "
          + "Pick another line-up, or none."
      } else if (stopped) {
        failure.value = `The team is fielded, but ${stopped} could not be carried across. ${done.reason}`
      } else {
        failure.value = done.reason
      }
      return
    }
    forgetCompetitionReads()
    emit("saved")
  } finally {
    fieldingNow.value = false
  }
}

watch(() => [props.teamId, props.season?.id] as const, async ([teamId, seasonId]) => {
  if (seasonId == null) return
  loading.value = true
  failure.value = null
  removed.value = []
  draftName.value = props.teamName
  banner.value = props.teamBanner ?? null
  icon.value = props.teamIcon ?? null
  playedIn.value = null
  rosterUnknown.value = false
  membersUnknown.value = false
  try {
    // Nothing to read for a team that does not exist yet: it opens on an empty form and one
    // empty row, so the first thing to do is the obvious thing.
    if (teamId == null) {
      kind.value = "played-before"
      picked.value = null
      carried.value = {from: null, entries: [], unread: false}
      if (pool.value.length === 0) pool.value = await loadTeams()
    }
    if (teamId == null) {
      rows.value = [emptyRow()]
    } else {
      const roster = await loadRoster(teamId, props.game, seasonId)
      rosterUnknown.value = roster == null
      rows.value = roster == null
        ? []
        : roster.slice().sort((a, b) => a.sortIndex - b.sortIndex).map(rowOf)
    }
    if (members.value.length === 0) {
      const accounts = await loadMemberAccounts()
      membersUnknown.value = accounts == null
      members.value = accounts ?? []
    }
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
const startFrom = (carried: {from: Fielding | null; entries: RosterEntry[]; unread: boolean}) => {
  // The source says it could not be read; replacing the form with its nobody would be that
  // failure written down as a squad of none.
  if (carried.unread) return
  // Their own entries stay where they are: these are new rows on this line-up, not the ones
  // they were copied from.
  const brought = carried.entries.map(entry => ({...rowOf(entry), id: null}))
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

const attach = (index: number, userId: number | null) => {
  const row = rows.value[index]
  if (!row) return
  row.userId = userId
}

/**
 * A team being made may be published with nobody on it -- fielding it and settling the squad
 * are the two decisions this whole feature exists to separate -- so an empty row is not an
 * unfinished one. A row somebody typed into has to name somebody.
 */
const complete = computed(() =>
  !rosterUnknown.value
  && draftName.value.trim() !== ""
  && rows.value.every(row => row.handle.trim() !== "" || (adding.value && isBlank(row))))

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
    forgetCompetitionReads()
    emit("removed")
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
  const season = props.season?.name ?? "this season"
  // No count where the line-up was never read: the sentence exists to say what is being lost.
  const played = rosterUnknown.value
    ? "a line-up that could not be read"
    : countOf(rows.value.length, "person", "people")
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
    forgetCompetitionReads()
    emit("removed")
  } finally {
    leavingSeason.value = false
  }
}

/**
 * What a refused publish reads as. The stage says what already landed — the team is written
 * first, so anything after it leaves the rename saved — and the count says how much of the
 * line-up did. Saying which is the honest half of the report; claiming nothing changed would
 * not be.
 */
const failureOf = (refusal: {reason: string; written: number; stage: PublishStage}): string => {
  if (refusal.stage === "team" || refusal.stage === "removals") return refusal.reason
  if (refusal.stage === "fielding") return `${refusal.reason} The team itself is saved.`
  const written = refusal.written
  const savedSoFar = written === 0
    ? "Nothing in the line-up was changed."
    : `The first ${written} of the line-up ${written === 1 ? "entry is" : "entries are"} saved.`
  return `${refusal.reason} ${savedSoFar}`
}

/** The parts a line-up is shown in, in the order the game page shows them. */
const GROUPS = [
  {role: TeamRoleEnum.PLAYER, one: "Player", many: "Players"},
  {role: TeamRoleEnum.SUBSTITUTE, one: "Substitute", many: "Substitutes"},
  {role: TeamRoleEnum.COACH, one: "Coach", many: "Coaches"},
] as const

/** Who the preview shows: the rows as typed, or the team being picked and whoever it brings. */
const previewPeople = computed(() => (adding.value && kind.value === "played-before"
  ? carried.value.entries.map(rowOf)
  : rows.value.filter(row => row.handle.trim() !== "")))

const previewGroups = computed(() => GROUPS
  .map(group => ({
    ...group,
    members: previewPeople.value
      .filter(row => row.role === group.role)
      .map(row => ({handle: row.handle, roleTitle: row.roleTitle, description: row.description})),
  }))
  .filter(group => group.members.length > 0))

/** The team's slice as the game page will draw it once this is saved. */
const previewSlices = computed(() => {
  const picking = adding.value && kind.value === "played-before"
  return [{
    id: "draft",
    title: (picking ? picked.value?.name : draftName.value.trim()) || "New team",
    meta: `${previewPeople.value.length} on the roster`,
    banner: banner.value?.url ?? "",
    srcset: srcsetOf(banner.value),
    icon: icon.value?.url ?? null,
    iconSrcset: srcsetOf(icon.value),
  }]
})

const submit = async () => {
  const seasonId = props.season?.id
  // Two guards rather than one: `complete` is what the button reads, and this is what makes an
  // unread roster unsavable however the save is reached.
  if (rosterUnknown.value || !complete.value || saving.value || seasonId == null) return
  saving.value = true
  failure.value = null
  try {
    const done = await publishLineup({
      teamId: props.teamId,
      name: draftName.value.trim(),
      game: props.game,
      seasonId,
      // The art belongs to this season's fielding rather than to the team, so it goes across
      // with the season — the same team is drawn with its own picture in every game it plays.
      banner: banner.value?.path ?? null,
      icon: icon.value?.path ?? null,
      removed: removed.value,
      entries: rows.value.map(entryOf),
    })
    if (!done.ok) {
      failure.value = failureOf(done)
      return
    }
    forgetCompetitionReads()
    emit("saved")
  } finally {
    saving.value = false
  }
}

/** The parts, as the part picker offers them. */
const partOptions = PARTS.map(part => ({key: part.value, label: part.label}))

/** Every member account, searched in the picker by name and address alike. */
const memberOptions = computed(() => members.value.map(one => ({
  key: String(one.id),
  label: one.name,
  note: one.email ?? undefined,
  terms: one.email ? [one.email] : [],
})))

/** The row number as the sign-up form numbers its questions. */
const numbered = (index: number) => String(index + 1).padStart(2, "0")
</script>

<template>
  <edit-page
    :accent="accent"
    :back="{to: back, label: gameName || 'Competition'}"
    :eyebrow="season ? `${gameName} in ${season.name}` : gameName"
    testid="team-edit"
    :title="adding ? 'Add a team' : teamName"
  >
    <!-- The removals sit in the head, the way an event's Delete does: each leaves the page, and
         neither belongs in the run of the form. -->
    <template
      v-if="!adding"
      #actions
    >
      <cut-button
        v-if="season"
        testid="lineup-drop-from-season"
        tone="quiet"
        @click="droppingFromSeason = true"
      >
        Remove from season
      </cut-button>
      <cut-button
        testid="lineup-remove-team"
        tone="quiet"
        @click="askToRemoveTeam"
      >
        Delete team
      </cut-button>
    </template>

    <div
      class="lineup"
      data-testid="lineup-editor"
    >
      <form-section
        v-if="adding"
        title="Which team"
      >
        <!-- Asked first, because the answer decides what the rest of this page is. -->
        <segmented-choice
          v-model="kind"
          :options="[
            {key: 'played-before', label: 'An existing team'},
            {key: 'new-team', label: 'A new team'},
          ]"
          testid-prefix="lineup-kind"
        />
        <form-fields v-if="kind === 'played-before'">
          <form-field
            class="form-span"
            :filled="picked != null"
            label="Team"
            required
            variant="inside"
          >
            <template #default="{controlId, labelId}">
              <search-picker
                :control-id="controlId"
                empty-note="Every team the association has already plays this game this season."
                :labelled-by="labelId"
                :options="poolOffered.map(one => ({key: String(one.id), label: one.name}))"
                placeholder="Search every team"
                :selected-key="picked ? String(picked.id) : null"
                testid-prefix="field-team"
                @pick="key => picked = poolOffered.find(one => String(one.id) === key) ?? null"
              />
            </template>
          </form-field>
        </form-fields>
      </form-section>

      <template v-if="adding && kind === 'played-before'">
        <lineup-source
          v-if="picked != null"
          :game="game"
          :season-id="season?.id ?? null"
          :team-id="picked.id"
          @update:carried="onCarried"
        />
        <notice-box
          v-if="failure"
          testid="lineup-failure"
          tone="danger"
        >
          {{ failure }}
        </notice-box>
      </template>

      <template v-else>
        <!-- The team itself, which belongs to every season rather than to this one, so a rename
             does not read as a change to the line-up underneath it. -->
        <form-section title="The team in every season">
          <form-fields>
            <div class="form-span">
              <form-control
                v-model="draftName"
                data-testid="lineup-team-name"
                label="Team name*"
                maxlength="128"
              />
            </div>
            <image-picker
              class="form-span"
              label="Banner"
              :picture="banner"
              :store="storeTeamBanner"
              testid="lineup-team-banner"
              @update:picture="banner = $event"
            />
            <image-picker
              class="form-span"
              label="Icon"
              may-be-vector
              :picture="icon"
              shape="icon"
              :store="storeTeamIcon"
              testid="lineup-team-icon"
              @update:picture="icon = $event"
            />
          </form-fields>
          <notice-box
            v-if="teamFailure"
            testid="lineup-team-failure"
            tone="danger"
          >
            {{ teamFailure }}
          </notice-box>
        </form-section>

        <!-- Only while a team is being made: correcting a line-up is about the people already
             on it, and dropping another squad into it would be a different act in the same
             clothes. -->
        <lineup-source
          v-if="adding"
          :game="game"
          :season-id="season?.id ?? null"
          @update:carried="startFrom"
        />

        <form-section
          testid="lineup-season-heading"
          :title="season ? `The line-up in ${season.name}` : 'This season\'s line-up'"
        >
          <p
            v-if="!loading && !rosterUnknown && rows.length > 0"
            class="lineup__note"
          >
            A recorded name reaches the page only for a member who allows it.
          </p>
          <p
            v-if="loading"
            class="lineup__note"
            data-testid="lineup-loading"
          >
            Reading the line-up…
          </p>

          <!-- An unread line-up is not an empty one, so it is not described as one. -->
          <notice-box
            v-else-if="rosterUnknown"
            testid="lineup-unknown"
            title="The line-up could not be read"
            tone="danger"
          >
            It is not shown and cannot be saved. Go back and open it again.
          </notice-box>

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
            <div class="lineup__head">
              <span class="lineup__number">{{ numbered(index) }}</span>
              <span class="lineup__who">{{ row.handle.trim() || "Somebody new" }}</span>
              <span class="lineup__acts">
                <icon-button
                  :disabled="index === 0"
                  :label="`Move ${row.handle || 'this player'} up`"
                  :testid="`lineup-up-${index}`"
                  @click="move(index, -1)"
                >
                  <svg
                    aria-hidden="true"
                    fill="none"
                    stroke="currentColor"
                    stroke-width="1.6"
                    viewBox="0 0 24 24"
                  ><path d="m6 14.5 6-6 6 6" /></svg>
                </icon-button>
                <icon-button
                  :disabled="index === rows.length - 1"
                  :label="`Move ${row.handle || 'this player'} down`"
                  :testid="`lineup-down-${index}`"
                  @click="move(index, 1)"
                >
                  <svg
                    aria-hidden="true"
                    fill="none"
                    stroke="currentColor"
                    stroke-width="1.6"
                    viewBox="0 0 24 24"
                  ><path d="m6 9.5 6 6 6-6" /></svg>
                </icon-button>
                <icon-button
                  danger
                  :label="`Remove ${row.handle || 'this player'}`"
                  :testid="`lineup-remove-${index}`"
                  @click="askToRemove(index)"
                >
                  <svg
                    aria-hidden="true"
                    fill="none"
                    stroke="currentColor"
                    stroke-width="1.6"
                    viewBox="0 0 24 24"
                  ><path d="M4.5 7h15M9.5 7V4.5h5V7M6.5 7l1 13h9l1-13" /></svg>
                </icon-button>
              </span>
            </div>

            <div class="lineup__body">
              <image-picker
                class="lineup__icon"
                label="Icon"
                layout="tile"
                :picture="row.icon"
                shape="icon"
                :store="storeRosterIcon"
                :testid="`lineup-icon-${index}`"
                @update:picture="stageIcon(index, $event)"
              />
              <form-fields>
                <form-control
                  v-model="row.handle"
                  :data-testid="`lineup-handle-${index}`"
                  label="Handle*"
                  maxlength="128"
                />
                <form-field
                  filled
                  label="Part"
                  variant="inside"
                >
                  <template #default="{controlId, labelId}">
                    <search-picker
                      :control-id="controlId"
                      :labelled-by="labelId"
                      :options="partOptions"
                      :selected-key="row.role"
                      :testid-prefix="`lineup-role-${index}`"
                      @pick="key => row.role = key as TeamRole"
                    />
                  </template>
                </form-field>
                <form-control
                  v-model="row.roleTitle"
                  :data-testid="`lineup-title-${index}`"
                  label="Title"
                  maxlength="64"
                />
                <form-control
                  v-model="row.displayName"
                  :data-testid="`lineup-name-${index}`"
                  label="Recorded name"
                  maxlength="128"
                />
                <div class="form-span lineup__caption">
                  <form-control
                    v-model="row.description"
                    :data-testid="`lineup-description-${index}`"
                    kind="markdown"
                    label="A word about them"
                    :max-length="DESCRIPTION_CAP"
                  />
                  <!-- Only near the cap, which the editor holds while it is typed. -->
                  <span
                    v-if="row.description.length > DESCRIPTION_CAP - 40"
                    class="lineup__count"
                    :data-testid="`lineup-count-${index}`"
                  >{{ row.description.length }}/{{ DESCRIPTION_CAP }}</span>
                </div>
                <div
                  v-if="row.userId != null"
                  class="lineup__attached form-span"
                  :data-testid="`lineup-member-${index}`"
                >
                  <span class="lineup__attached-label">Member account</span>
                  <span class="lineup__attached-name">{{ nameOf(row.userId) }}</span>
                  <icon-button
                    :label="`Detach ${nameOf(row.userId)}`"
                    :testid="`lineup-detach-${index}`"
                    @click="attach(index, null)"
                  >
                    <svg
                      aria-hidden="true"
                      fill="none"
                      stroke="currentColor"
                      stroke-width="1.6"
                      viewBox="0 0 24 24"
                    ><path d="M6 6l12 12M18 6 6 18" /></svg>
                  </icon-button>
                </div>
                <form-field
                  v-else
                  class="form-span"
                  :error="membersUnknown ? 'The accounts could not be read, so nobody can be attached.' : ''"
                  label="Member account"
                  variant="inside"
                >
                  <template #default="{controlId, labelId}">
                    <search-picker
                      :control-id="controlId"
                      :disabled="membersUnknown"
                      empty-note="Nobody has an account yet."
                      :labelled-by="labelId"
                      :options="memberOptions"
                      placeholder="No account"
                      :testid-prefix="`lineup-search-${index}`"
                      @pick="key => attach(index, Number(key))"
                    />
                  </template>
                </form-field>
              </form-fields>
            </div>
          </div>

          <div v-if="!rosterUnknown">
            <cut-button
              testid="lineup-add"
              tone="quiet"
              @click="add"
            >
              <svg
                aria-hidden="true"
                class="lineup__plus"
                fill="none"
                stroke="currentColor"
                stroke-width="1.8"
                viewBox="0 0 24 24"
              ><path d="M12 5v14M5 12h14" /></svg>Add somebody
            </cut-button>
          </div>

          <notice-box
            v-if="failure"
            testid="lineup-failure"
            tone="danger"
          >
            {{ failure }}
          </notice-box>
        </form-section>
      </template>
    </div>

    <template #footer>
      <div class="lineup__actions">
        <cut-button
          testid="lineup-cancel"
          tone="quiet"
          @click="emit('cancel')"
        >
          Cancel
        </cut-button>
        <cut-button
          v-if="adding && kind === 'played-before'"
          :disabled="picked == null || fieldingNow || carried.unread"
          testid="field-team-confirm"
          tone="solid"
          @click="fieldPicked"
        >
          {{ fieldingNow ? "Fielding" : picked ? `Field ${picked.name}` : "Field the team" }}
        </cut-button>
        <cut-button
          v-else
          :disabled="!complete || saving"
          testid="lineup-save"
          tone="solid"
          @click="submit"
        >
          {{ saving ? "Saving" : adding ? "Add the team" : "Save team" }}
        </cut-button>
      </div>
    </template>
    <template #preview>
      <preview-frame>
        <slice-band
          :accent="accent ?? 'var(--color-brand)'"
          :items="previewSlices"
          open-id="draft"
          testid-prefix="team-edit-preview"
        >
          <template #details>
            <team-roster :groups="previewGroups" />
          </template>
        </slice-band>
      </preview-frame>
    </template>
  </edit-page>

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

<style scoped>
.lineup {
  display: flex;
  flex-direction: column;
}

/* The sections draw the rules here, so the choice does not add one of its own. */
.lineup :deep(.choice) {
  margin-bottom: 0;
  padding-bottom: 0;
  border-bottom: 0;
}

.lineup__note {
  font-size: 0.9rem;
  color: var(--color-ash);
}

/* One person, drawn the way the sign-up form draws a question: a number, a name and the row's
   own actions over its fields, on the band's ground. */
.lineup__row {
  display: flex;
  flex-direction: column;
  gap: 0.8rem;
  padding: 1rem 1.2rem 1.1rem;
  background-color: var(--band-ground);
}

.lineup__head {
  display: flex;
  align-items: center;
  gap: 0.8rem;
}

.lineup__number {
  font-family: var(--font-display);
  font-size: 1.15rem;
  line-height: 1;
  color: var(--color-chalk);
}

.lineup__who {
  min-width: 0;
  overflow: hidden;
  font-size: 11px;
  font-weight: 500;
  letter-spacing: 0.3em;
  text-overflow: ellipsis;
  text-transform: uppercase;
  white-space: nowrap;
  color: var(--color-ash);
}

.lineup__acts {
  display: flex;
  flex: none;
  align-items: center;
  gap: 0.25rem;
  margin-left: auto;
}

.lineup__body {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 1rem;
  align-items: start;
}

.lineup__attached {
  display: flex;
  align-items: center;
  gap: 0.8rem;
  min-height: 3.25rem;
  padding: 0 0.4rem 0 0.9rem;
  background-color: color-mix(in oklab, var(--color-chalk) 5%, transparent);
  border-bottom: 1px solid var(--color-hairline);
}

.lineup__attached-label {
  font-size: 11px;
  font-weight: 500;
  letter-spacing: 0.2em;
  text-transform: uppercase;
  color: var(--color-ash);
}

.lineup__attached-name {
  margin-right: auto;
  color: var(--color-chalk);
}

/* Preflight draws an svg as a block, which would put the plus above the word. */
.lineup__plus {
  display: inline-block;
  width: 14px;
  height: 14px;
  margin-right: 0.4rem;
  vertical-align: -2px;
}

.lineup__actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 0.5rem;
}

@media (max-width: 767px) {
  .lineup__row {
    padding: 0.9rem 0.9rem 1rem;
  }

  .lineup__body {
    grid-template-columns: minmax(0, 1fr);
  }
}

.lineup__caption {
  position: relative;
}

.lineup__count {
  position: absolute;
  right: 0.5rem;
  bottom: 0.35rem;
  font-family: var(--font-bitmap);
  font-size: 0.68rem;
  color: var(--color-ash);
  pointer-events: none;
}
</style>
