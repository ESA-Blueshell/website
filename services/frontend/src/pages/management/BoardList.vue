<script lang="ts" setup>
/* Every board, newest first: its year, its members, where it stands and the role and list it
   holds. Opening one renders the site's own board editor inside Management. */
import {computed, onMounted, ref} from "vue"
import CutButton from "@/components/island/CutButton.vue"
import FactList from "@/components/island/FactList.vue"
import SearchBox from "@/components/island/SearchBox.vue"
import StateMark from "@/components/island/StateMark.vue"
import ManagementPage from "@/components/management/ManagementPage.vue"
import ManagementRow from "@/components/management/ManagementRow.vue"
import ManagementTable, {type TableColumn} from "@/components/management/ManagementTable.vue"
import {type Board, type BoardStanding, academicYear, boardName, standingOf, useBoards} from "@/domains/boards"
import {type CohortSummary, type SummaryTarget, TargetMark, TargetSystem, fetchCohorts} from "@/domains/cohorts"

defineOptions({name: "BoardListPage"})

const {boards, loading} = useBoards()
const search = ref("")

const STANDING: Record<BoardStanding, {kind: "in-step" | "not-created" | "not-compared"; word: string}> = {
  "in office": {kind: "in-step", word: "In office"},
  candidate: {kind: "not-created", word: "Kandi"},
  past: {kind: "not-compared", word: "Handed over"},
}

/* The role and the list follow the standing, not the board's record: the board in office holds
   the board's, the candidate board the kandi's, and a board that handed over holds neither. */
const cohorts = ref<CohortSummary[]>([])
const COHORT_OF: Partial<Record<BoardStanding, string>> = {"in office": "BOARD", candidate: "KANDI"}
const targetsOf = (board: Board): SummaryTarget[] => {
  const key = COHORT_OF[standingOf(board, boards.value)]
  return (key && cohorts.value.find((one) => one.definitionKey === key)?.targets) || []
}
const SYSTEMS = [TargetSystem.DISCORD, TargetSystem.BREVO]

onMounted(async () => {
  cohorts.value = await fetchCohorts().catch(() => [])
})

const shown = computed(() => {
  const needle = search.value.trim().toLowerCase()
  return boards.value.filter((board) => needle === "" ||
    [boardName(board.number, board.name), academicYear(board.startDate, board.endDate), ...board.members.map((one) => one.name ?? "")]
      .some((value) => value.toLowerCase().includes(needle)))
})
const COLUMNS: TableColumn[] = [
  {key: "name", label: "Board", wrap: true},
  {key: "year", label: "Year"},
  {key: "people", label: "Members"},
  {key: "discord", label: "Discord"},
  {key: "brevo", label: "Brevo"},
  {key: "state", label: "State"},
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
      testid="board-list-table"
      :to="(board) => `/management/board/${board.number}`"
    >
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
          :quiet="standingOf(row, boards) === 'past'"
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
  </management-page>
</template>

<style scoped>
.boards__facts {
  padding: 1.1rem 0 1.2rem;
}
</style>
