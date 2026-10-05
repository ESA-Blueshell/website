<script lang="ts" setup>
/* Every board, newest first: its year, its members, where it stands and the role and list it
   holds. The ticked boards whose years name a role on Discord can be linked to it together.
   Opening one renders the site's own board editor inside Management. */
import {computed, onMounted, ref} from "vue"
import CutButton from "@/components/island/CutButton.vue"
import FactList from "@/components/island/FactList.vue"
import SearchBox from "@/components/island/SearchBox.vue"
import SelectionBar from "@/components/island/SelectionBar.vue"
import StateMark from "@/components/island/StateMark.vue"
import BulkAdd from "@/components/management/BulkAdd.vue"
import ManagementPage from "@/components/management/ManagementPage.vue"
import ManagementRow from "@/components/management/ManagementRow.vue"
import ManagementTable, {type TableColumn} from "@/components/management/ManagementTable.vue"
import RowCheck from "@/components/management/RowCheck.vue"
import {useUserSelection} from "@/composables/useUserSelection"
import {type Board, type BoardStanding, academicYear, boardCohortKey, boardName, standingOf, useBoards} from "@/domains/boards"
import {type CohortSummary, type SummaryTarget, TargetMark, TargetSystem, fetchCohorts, targetLabel} from "@/domains/cohorts"
import {type AdoptionMatch, adoptMatches, listMatches} from "@/domains/discord"

defineOptions({name: "BoardListPage"})

const {boards, loading} = useBoards()
const search = ref("")

const STANDING: Record<BoardStanding, {kind: "in-step" | "not-created" | "not-compared"; word: string}> = {
  "in office": {kind: "in-step", word: "In office"},
  candidate: {kind: "not-created", word: "Kandi"},
  past: {kind: "not-compared", word: "Handed over"},
}

/* The role and the list follow the standing: the board in office holds the board's, the candidate
   board the kandi's, and a board that handed over the role of its own years. */
const cohorts = ref<CohortSummary[]>([])
const cohortOf = (board: Board): string => boardCohortKey(board, boards.value)
const targetsOf = (board: Board): SummaryTarget[] => cohorts.value.find((one) => one.definitionKey === cohortOf(board))?.targets ?? []
const SYSTEMS = [TargetSystem.DISCORD, TargetSystem.BREVO]

/* The server already has a role for most board years. A ticked board is linked to the role that
   carries its name; one with a role already, or with no role of its name, is left out and said so. */
const {selectedIdsArray, isSelected, toggle, headerState, toggleHeader, selectMany, clear: clearSelection} =
  useUserSelection(computed(() => shown.value.map((one) => one.id)))
const ticked = computed(() => boards.value.filter((one) => isSelected(one.id)))
const matches = ref<AdoptionMatch[]>([])
const linking = ref(false)
const hasRole = (board: Board) => targetsOf(board).some((one) => one.system === TargetSystem.DISCORD && one.made)
const matchOf = (board: Board) => matches.value.find((one) => one.key === cohortOf(board))
const toLink = computed(() => ticked.value.flatMap((board) => {
  const match = hasRole(board) ? undefined : matchOf(board)
  return match ? [{key: board.id, name: boardName(board.number, board.name), note: `@${match.roleName}`, match}] : []
}))
const leftOut = computed(() => ticked.value
  .filter((board) => hasRole(board) || !matchOf(board))
  .map((board) => ({name: boardName(board.number, board.name), why: hasRole(board) ? "Has a role already" : "No role on Discord carries its name"})))
const link = async ({match}: {match: AdoptionMatch}) => {
  const answered = await adoptMatches([match.key])
  if (!answered.ok) return answered
  const [refused] = answered.saved.refused
  return refused ? {ok: false as const, reason: refused.reason} : {ok: true as const}
}
const startLinking = async () => {
  matches.value = await listMatches()
  linking.value = true
}
const linked = async () => {
  cohorts.value = await fetchCohorts().catch(() => [])
  clearSelection()
}

onMounted(async () => {
  cohorts.value = await fetchCohorts().catch(() => [])
})

const shown = computed(() => {
  const needle = search.value.trim().toLowerCase()
  return boards.value.filter((board) => needle === "" ||
    [boardName(board.number, board.name), academicYear(board.startDate, board.endDate), ...board.members.map((one) => one.name ?? "")]
      .some((value) => value.toLowerCase().includes(needle)))
})
const COLUMNS: TableColumn<Board>[] = [
  {key: "name", label: "Board", wrap: true, sortBy: (board) => board.number},
  {key: "year", label: "Year", sortBy: (board) => board.startDate},
  {key: "people", label: "Members", sortBy: (board) => board.members.length},
  {key: "discord", label: "Discord", sortBy: (board) => targetLabel(targetsOf(board), TargetSystem.DISCORD)},
  {key: "brevo", label: "Brevo", sortBy: (board) => targetLabel(targetsOf(board), TargetSystem.BREVO)},
  {key: "state", label: "State", sortBy: (board) => standing(board).word},
]

const facts = computed(() => {
  const named = (state: BoardStanding) => boards.value.filter((board) => standingOf(board, boards.value) === state)
    .map((board) => boardName(board.number, board.name)).join(", ")
  return [
    {label: "Boards", value: String(boards.value.length), sub: "Since the association began"},
    {label: "In office", value: named("in office") || "Nobody", sub: ""},
    {label: "Kandi", value: named("candidate") || "None yet", sub: "The next board, before it takes office"},
  ]
})

const standing = (board: Board) => STANDING[standingOf(board, boards.value)]
const people = (board: Board) => `${board.members.length} ${board.members.length === 1 ? "member" : "members"}`
</script>

<template>
  <management-page
    eyebrow="Content"
    testid="board-list"
    title="Board"
  >
    <template #lede>
      Every board the association has had, the one in office and the one about to take over.
    </template>
    <template #actions>
      <cut-button
        href="/management/board/new"
        testid="board-list-new"
      >
        Add a board
      </cut-button>
    </template>

    <fact-list
      class="boards__facts"
      :facts="facts"
    />

    <management-table
      :columns="COLUMNS"
      :row-key="(board) => board.number"
      :row-testid="(board) => `board-row-${board.number}`"
      :rows="shown"
      :header-state="headerState"
      :selected-count="selectedIdsArray.length"
      testid="board-list-table"
      :to="(board) => `/management/board/${board.number}`"
      :total="boards.length"
      @clear-selection="clearSelection"
      @select-all="selectMany(boards.map((one) => one.id))"
      @toggle-shown="toggleHeader"
    >
      <template #check="{row}">
        <row-check
          :checked="isSelected(row.id)"
          :label="`Select ${boardName(row.number, row.name)}`"
          :testid="`board-check-${row.number}`"
          @toggle="toggle(row.id)"
        />
      </template>
      <template #count>
        <b>{{ shown.length }}</b> of {{ boards.length }} boards
      </template>
      <template #search>
        <search-box
          v-model="search"
          label="Search boards"
          testid="board-list-search"
        />
      </template>
      <template
        v-if="!loading"
        #empty
      >
        <span data-testid="board-list-empty">No board matches.</span>
      </template>
      <template #name="{row}">
        <router-link
          class="mg-name"
          :to="`/management/board/${row.number}`"
        >
          {{ boardName(row.number, row.name) }}
        </router-link>
      </template>
      <template #year="{row}">
        {{ academicYear(row.startDate, row.endDate) }}
      </template>
      <template #people="{row}">
        {{ people(row) }}
      </template>
      <template
        v-for="system in SYSTEMS"
        :key="system"
        #[system.toLowerCase()]="{row}"
      >
        <target-mark
          :quiet="system === TargetSystem.BREVO && standingOf(row, boards) === 'past'"
          :system="system"
          :targets="targetsOf(row)"
          :testid="`board-${system.toLowerCase()}-${row.number}`"
        />
      </template>
      <template #state="{row}">
        <state-mark
          :kind="standing(row).kind"
          :testid="`board-standing-${row.number}`"
        >
          {{ standing(row).word }}
        </state-mark>
      </template>
      <template #phone="{row}">
        <management-row
          :meta="`${academicYear(row.startDate, row.endDate)} · ${people(row)}`"
          :name="boardName(row.number, row.name)"
          :testid="`board-row-${row.number}`"
          :to="`/management/board/${row.number}`"
        >
          <state-mark
            :kind="standing(row).kind"
            :testid="`board-standing-${row.number}`"
          >
            {{ standing(row).word }}
          </state-mark>
        </management-row>
      </template>
    </management-table>

    <selection-bar
      always
      :count="selectedIdsArray.length"
      testid="board-list-selection"
      @clear="clearSelection"
    >
      <cut-button
        small
        testid="board-link-roles"
        tone="solid"
        @click="startLinking"
      >
        Link Discord roles by name
      </cut-button>
    </selection-bar>

    <bulk-add
      each="the Discord role that carries its name"
      :items="toLink"
      :noun="['board', 'boards']"
      :open="linking"
      :run="link"
      :skipped="leftOut"
      testid="board-bulk-link"
      title="Link Discord roles by name"
      @done="linked"
      @update:open="linking = $event"
    />
  </management-page>
</template>

<style scoped>
.boards__facts {
  padding: 1.1rem 0 1.2rem;
}
</style>
