<script lang="ts" setup>
import {computed, onMounted, ref} from "vue"
import {useStore} from "vuex"
import ConfirmDialog from "@/components/island/ConfirmDialog.vue"
import CutButton from "@/components/island/CutButton.vue"
import FactList, {type Fact} from "@/components/island/FactList.vue"
import FilterBar from "@/components/island/FilterBar.vue"
import FilterPicker from "@/components/island/FilterPicker.vue"
import FormControl from "@/components/island/FormControl.vue"
import ModalDialog from "@/components/island/ModalDialog.vue"
import SearchBox from "@/components/island/SearchBox.vue"
import SelectionBar from "@/components/island/SelectionBar.vue"
import StateMark from "@/components/island/StateMark.vue"
import ManagementPage from "@/components/management/ManagementPage.vue"
import ManagementTable, {type TableColumn} from "@/components/management/ManagementTable.vue"
import RowCheck from "@/components/management/RowCheck.vue"
import {
  approveDeclaration,
  day,
  type Declaration,
  DeclarationState,
  euros,
  forsOf,
  listDeclarations,
  markDeclarationPaid,
  rejectDeclaration,
  STATE_MARKS,
  stateOf,
  stepAt,
  titleOf,
  totalOf,
  withTheBoard,
} from "@/domains/declarations"
import {fold} from "@/domains/user"

/** The board's list of declarations, opened on the ones that wait to be checked. */
defineOptions({name: "DeclarationManagerPage"})

const store = useStore()
const all = ref<Declaration[]>([])
const loaded = ref(false)
const unread = ref(false)
// Opens on what waits for the board; Any shows every declaration.
type Shown = "review" | "pay" | "paid" | "rejected"
const shown = ref<Shown | null>("review")
const member = ref<string | null>(null)
const search = ref("")

const load = async () => {
  const read = await listDeclarations()
  all.value = read ?? []
  unread.value = read === null
  loaded.value = true
}
onMounted(load)

// The board sees no drafts, so a declaration sent back leaves this list until it is submitted again.
const FILTERS: Record<Shown, DeclarationState> = {
  review: DeclarationState.SUBMITTED,
  pay: DeclarationState.APPROVED,
  paid: DeclarationState.PAID,
  rejected: DeclarationState.REJECTED,
}

const inState = (key: Shown) => all.value.filter(one => one.state === FILTERS[key])
const count = (key: Shown) => inState(key).length
const sum = (key: Shown) => inState(key).reduce((total, one) => total + totalOf(one.expenses), 0)
const declarations = (n: number) => (n === 1 ? "1 declaration" : `${n} declarations`)

const facts = computed<Fact[]>(() => [
  {label: "To check", value: euros(sum("review")), sub: declarations(count("review")), testid: "declarations-to-check"},
  {label: "To pay", value: euros(sum("pay")), sub: declarations(count("pay")), testid: "declarations-to-pay"},
])

const options = computed(() => [
  {key: "review", label: `To check (${count("review")})`},
  {key: "pay", label: `To pay (${count("pay")})`},
  {key: "paid", label: "Paid"},
  {key: "rejected", label: "Rejected"},
])

// Each submitter once, by name, as the other Management lists pick a person.
const members = computed(() => {
  const named = new Map(all.value.map(one => [String(one.submitterId), one.submitter]))
  return [...named].map(([key, label]) => ({key, label})).sort((a, b) => a.label.localeCompare(b.label))
})

const submittedAt = (one: Declaration) => stepAt(one, DeclarationState.SUBMITTED)?.at ?? one.createdAt

const COLUMNS: TableColumn<Declaration>[] = [
  {key: "what", label: "Declaration", wrap: true, sortBy: one => titleOf(one)},
  {key: "who", label: "Submitted by", sortBy: one => one.submitter},
  {key: "total", label: "Total", sortBy: one => totalOf(one.expenses)},
  {key: "when", label: "Submitted", newestFirst: true, sortBy: one => Date.parse(submittedAt(one))},
  {key: "state", label: "Status", sortBy: one => stateOf(one).label},
]

const rows = computed(() => {
  const words = fold(search.value).split(" ").filter(Boolean)
  return all.value
    .filter(one => shown.value === null || one.state === FILTERS[shown.value])
    .filter(one => member.value === null || String(one.submitterId) === member.value)
    .filter(one => words.every(word =>
      [one.submitter, ...one.expenses.map(expense => expense.description), ...forsOf(one)].some(text => fold(text).includes(word))))
})

const filtered = computed(() => shown.value != null || member.value != null || search.value.trim() !== "")
const clear = () => {
  shown.value = null
  member.value = null
  search.value = ""
}

/* Ticked rows stay ticked while the filter or the search changes, as the other lists keep them. */
const selected = ref(new Set<number>())
const picked = computed(() => all.value.filter(one => selected.value.has(one.id)))
const toggle = (id: number) => {
  const next = new Set(selected.value)
  if (!next.delete(id)) next.add(id)
  selected.value = next
}
const headerState = computed<"checked" | "indeterminate" | "unchecked">(() => {
  const ticked = rows.value.filter(one => selected.value.has(one.id)).length
  if (ticked === 0) return "unchecked"
  return ticked === rows.value.length ? "checked" : "indeterminate"
})
const toggleShown = () => {
  const next = new Set(selected.value)
  if (headerState.value === "checked") rows.value.forEach(one => next.delete(one.id))
  else rows.value.forEach(one => next.add(one.id))
  selected.value = next
}
const selectAll = () => {
  selected.value = new Set(all.value.map(one => one.id))
}
const clearSelection = () => {
  selected.value = new Set()
}

// TWIN: `approve`, `markPaid` and `WITH_THE_BOARD` in the api's `DeclarationReviews.kt`.
const approvable = computed(() => picked.value.filter(one => one.state === DeclarationState.SUBMITTED))
const payable = computed(() => picked.value.filter(one => one.state === DeclarationState.APPROVED))
const rejectable = computed(() => picked.value.filter(withTheBoard))
const skipped = (n: number) => (picked.value.length - n === 1 ? "1 other is skipped" : `${picked.value.length - n} others are skipped`)

const working = ref(false)
const approving = ref(false)
const paying = ref(false)
const rejecting = ref(false)
const reason = ref("")

const runOn = async (list: Declaration[], move: (id: number) => Promise<{ok: boolean}>) => {
  working.value = true
  try {
    const answers = await Promise.all(list.map(one => move(one.id)))
    const failed = answers.filter(answer => !answer.ok).length
    if (failed > 0) store.commit("setStatusSnackbarMessage", failed === 1 ? "1 declaration could not be changed." : `${failed} declarations could not be changed.`)
    clearSelection()
    await load()
  } finally {
    working.value = false
    approving.value = false
    paying.value = false
    rejecting.value = false
  }
}

const startRejecting = () => {
  reason.value = ""
  rejecting.value = true
}
</script>

<template>
  <management-page
    eyebrow="Money"
    testid="declaration-manager"
    title="Declarations"
  >
    <template #lede>
      Money that people spent for Blueshell and want back. Check each one, then pay it.
    </template>

    <fact-list
      class="list__facts"
      :facts="facts"
    />

    <management-table
      class="list__table"
      :columns="COLUMNS"
      :header-state="headerState"
      label="Declarations"
      :row-key="(one) => one.id"
      :row-testid="(one) => `declarations-manage-${one.id}`"
      :rows="rows"
      :selected-count="picked.length"
      :start-sort="{key: 'when', descending: true}"
      testid="declaration-table"
      :to="(one) => `/management/declarations/${one.id}`"
      :total="all.length"
      @clear-selection="clearSelection"
      @select-all="selectAll"
      @toggle-shown="toggleShown"
    >
      <template #count>
        <b>{{ rows.length }}</b> of {{ all.length }} declarations
      </template>
      <template #filters>
        <filter-bar
          :active="filtered"
          testid="declarations-filters"
          @clear="clear"
        >
          <filter-picker
            v-model="shown"
            label="Status"
            :options="options"
            testid="declarations-status"
          />
          <filter-picker
            v-model="member"
            label="Submitted by"
            :options="members"
            testid="declarations-member"
          />
        </filter-bar>
      </template>
      <template #search>
        <search-box
          v-model="search"
          label="Search by name, expense, committee or event"
          testid="declarations-search"
        />
      </template>
      <template
        v-if="loaded"
        #empty
      >
        <span
          v-if="unread"
          data-testid="declarations-unread"
        >We could not load the declarations.</span>
        <span
          v-else
          data-testid="declarations-empty"
        >No declarations here.</span>
      </template>
      <template #check="{row}">
        <row-check
          :checked="selected.has(row.id)"
          :label="`Select ${titleOf(row)}`"
          :testid="`declarations-check-${row.id}`"
          @toggle="toggle(row.id)"
        />
      </template>
      <template #what="{row}">
        <router-link
          class="mg-name"
          :to="`/management/declarations/${row.id}`"
        >
          {{ titleOf(row) }}
        </router-link>
        <span class="mg-sub">{{ [row.expenses.length === 1 ? "1 expense" : `${row.expenses.length} expenses`, ...forsOf(row)].join(" · ") }}</span>
      </template>
      <template #who="{row}">
        {{ row.submitter }}
      </template>
      <template #total="{row}">
        {{ euros(totalOf(row.expenses)) }}
      </template>
      <template #when="{row}">
        {{ day(submittedAt(row)) }}
      </template>
      <template #state="{row}">
        <state-mark :kind="STATE_MARKS[row.state]">
          {{ stateOf(row).label }}
        </state-mark>
      </template>
    </management-table>

    <selection-bar
      always
      class="list__selection"
      :count="picked.length"
      testid="declarations-selection"
      @clear="clearSelection"
    >
      <cut-button
        :disabled="approvable.length === 0 || working"
        small
        testid="declarations-approve"
        tone="solid"
        @click="approving = true"
      >
        Approve
      </cut-button>
      <cut-button
        :disabled="payable.length === 0 || working"
        small
        testid="declarations-mark-paid"
        tone="solid"
        @click="paying = true"
      >
        Mark as paid
      </cut-button>
      <cut-button
        :disabled="rejectable.length === 0 || working"
        small
        testid="declarations-reject"
        tone="danger"
        @click="startRejecting"
      >
        Reject
      </cut-button>
    </selection-bar>

    <confirm-dialog
      v-model:open="approving"
      :confirm-label="approvable.length === 1 ? 'Approve 1' : `Approve ${approvable.length}`"
      :question="approvable.length === picked.length
        ? 'Approved declarations wait to be paid.'
        : `Only declarations waiting to be checked can be approved, so ${skipped(approvable.length)}. The rest wait to be paid.`"
      testid="declarations-approve-dialog"
      :title="approvable.length === 1 ? 'Approve 1 declaration?' : `Approve ${approvable.length} declarations?`"
      :working="working"
      @confirm="runOn(approvable, approveDeclaration)"
    />

    <confirm-dialog
      v-model:open="paying"
      :confirm-label="`Mark ${payable.length} as paid`"
      :question="(payable.length === picked.length ? '' : `Only approved declarations can be marked paid, so ${skipped(payable.length)}. `) +
        'Make sure you have sent each declaration\'s money to its bank account first. Each person gets an email saying their declaration is paid.'"
      testid="declarations-paid-dialog"
      :title="payable.length === 1 ? 'Mark 1 declaration as paid?' : `Mark ${payable.length} declarations as paid?`"
      :working="working"
      @confirm="runOn(payable, markDeclarationPaid)"
    />

    <modal-dialog
      v-model:open="rejecting"
      testid="declarations-reject-dialog"
      :title="rejectable.length === 1 ? 'Reject 1 declaration?' : `Reject ${rejectable.length} declarations?`"
    >
      <p class="list__ask">
        Each person gets an email with your reason. A rejected declaration cannot be submitted again.
        <template v-if="rejectable.length < picked.length">
          Only declarations waiting to be checked or approved can be rejected, so {{ skipped(rejectable.length) }}.
        </template>
      </p>
      <form-control
        v-model="reason"
        kind="textarea"
        label="Reason*"
        testid="declarations-reject-reason"
      />
      <template #footer>
        <cut-button
          :disabled="!reason.trim() || working"
          testid="declarations-reject-confirm"
          tone="danger"
          @click="runOn(rejectable, (id) => rejectDeclaration(id, reason))"
        >
          Reject
        </cut-button>
      </template>
    </modal-dialog>
  </management-page>
</template>

<style scoped>
.list__facts {
  padding: 0.4rem 0 1.4rem;
}

/* The search takes what the bar leaves, so a long query stays readable. */
.list__table :deep(.mg-table__search) {
  flex: 1 1 18rem;
}

.list__table :deep(.mg-table__search .search-box) {
  width: 100%;
}

.list__selection {
  position: sticky;
  bottom: 0;
  z-index: 2;
  margin-top: 2px;
}

.list__ask {
  margin-bottom: 1rem;
}
</style>
