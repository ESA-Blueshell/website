<script lang="ts" setup>
/* Every list in the association's Brevo account by the folder it is really in, with the cohort that
   fills it, its people and its drift. Lists the site expects and that are missing come first. */
import {computed, onMounted, ref} from "vue"
import CutButton from "@/components/island/CutButton.vue"
import FactList from "@/components/island/FactList.vue"
import FormField from "@/components/island/FormField.vue"
import ModalDialog from "@/components/island/ModalDialog.vue"
import NoticeBox from "@/components/island/NoticeBox.vue"
import SearchBox from "@/components/island/SearchBox.vue"
import SearchPicker from "@/components/island/SearchPicker.vue"
import StateMark from "@/components/island/StateMark.vue"
import CheckBox from "@/components/island/CheckBox.vue"
import TextInput from "@/components/island/TextInput.vue"
import ManagementPage from "@/components/management/ManagementPage.vue"
import ManagementRow from "@/components/management/ManagementRow.vue"
import ManagementTable, {type TableColumn} from "@/components/management/ManagementTable.vue"
import MiniButton from "@/components/management/MiniButton.vue"
import PairList from "@/components/management/PairList.vue"
import RowCheck from "@/components/management/RowCheck.vue"
import BulkAdd from "@/components/management/BulkAdd.vue"
import SelectionBar from "@/components/island/SelectionBar.vue"
import {useUserSelection} from "@/composables/useUserSelection"
import {
  FolderCare,
  type ListedTarget,
  type MissingTarget,
  type OverviewRow,
  type TargetOverview,
  TargetSystem,
  type TidyPlan,
  applyTidy,
  archiveTarget,
  createFolderInSystem,
  createListInSystem,
  createMissingLists,
  driftMarksOf,
  driftOf,
  fetchTargetFolders,
  fetchTidyPlan,
  followsOf,
  groupsOf,
  lastTidyLine,
  missingNotice,
  overviewFacts,
  readTargetOverview,
  useTargetSync,
} from "@/domains/cohorts"
import store from "@/plugins/store"
import {formatMoment} from "@/utils/timestamps"

defineOptions({name: "BrevoPage"})

const SYSTEM = TargetSystem.BREVO
const NEW_FOLDER = "__new__"

const overview = ref<TargetOverview | null>(null)
const loaded = ref(false)
const search = ref("")
const acting = ref(false)

const groups = computed(() => (overview.value ? groupsOf(overview.value, search.value) : []))

type ListRow = OverviewRow & {folder: string; kind: string}

const stateWord = (row: ListRow) => {
  if (row.missing) return row.missing.creating ? "Being created" : "Not created yet"
  return row.kind === "archive" ? "Archived" : driftOf(row.list).word
}

const COLUMNS: TableColumn<ListRow>[] = [
  {key: "name", label: "List", wrap: true, sortBy: (row) => row.missing?.cohortLabel ?? row.list?.label},
  {key: "follows", label: "Follows", wrap: true, sortBy: followsOf},
  {key: "people", label: "People", sortBy: (row) => row.missing?.memberCount ?? row.list?.memberCount},
  {key: "state", label: "State", sortBy: stateWord},
  {key: "reconciled", label: "Compared", sortBy: (row) => row.list?.lastReconciledAt},
]

// One table for every list, in the order of the folders they are in; each row names its folder.
const rows = computed<ListRow[]>(() => groups.value.flatMap((group) => group.rows.map((row) => ({...row, folder: group.name, kind: group.kind}))))
const total = computed(() => (overview.value ? groupsOf(overview.value, "").reduce((sum, group) => sum + group.rows.length, 0) : 0))
const linkOf = (row: ListRow) => (row.missing ? null : `/management/platforms/brevo/lists/${row.list.externalId}`)
const facts = computed(() => (overview.value ? overviewFacts(overview.value) : []))
const notice = computed(() => missingNotice(overview.value?.missing ?? []))

const load = async () => {
  overview.value = await readTargetOverview(SYSTEM)
  loaded.value = true
}

/* Ticked lists are compared with Brevo or filled with who is missing together. A list still to be
   created has nothing to compare, so it carries no tick. */
const {selectedIdsArray, isSelected, toggle, headerState, toggleHeader, selectMany, clear: clearSelection} =
  useUserSelection(computed(() => rows.value.filter((row) => row.list).map(keyOf)))
const everyList = computed(() => (overview.value ? groupsOf(overview.value, "").flatMap((group) => group.rows) : []).filter((row) => row.list))
const ticked = computed(() => everyList.value.filter((row) => isSelected(keyOf(row))).map((row) => row.list!))
const sync = useTargetSync(SYSTEM, ticked, ["list", "lists"])
const synced = async () => {
  clearSelection()
  await load()
}

const said = (message: string) => store.commit("setStatusSnackbarMessage", message)

/* Creating lists goes in three steps, so nothing is made in Brevo by one press: the lists are
   shown first, then asked about once more, and only then created, a job per list. */
const pending = ref<MissingTarget[]>([])
const createStep = ref<"preview" | "confirm">("preview")
const createRefusal = ref<string | null>(null)
const pendingPeople = computed(() => pending.value.reduce((sum, one) => sum + one.memberCount, 0))
const pendingPairs = computed(() => pending.value.map((one) => ({
  label: one.cohortLabel,
  value: `${one.folder ?? "No folder"} · ${one.memberCount} ${one.memberCount === 1 ? "person" : "people"}`,
})))

const previewMissing = (targetIds: number[]) => {
  const missing = overview.value?.missing ?? []
  pending.value = targetIds.length === 0 ? missing : missing.filter((one) => targetIds.includes(one.targetId))
  createStep.value = "preview"
  createRefusal.value = null
}

const createMissing = async () => {
  if (acting.value || pending.value.length === 0) return
  acting.value = true
  const answered = await createMissingLists(SYSTEM, pending.value.map((one) => one.targetId))
  acting.value = false
  if (!answered.ok) return void (createRefusal.value = answered.reason)
  pending.value = []
  said(answered.saved === 1 ? "The list is being created." : `${answered.saved} lists are being created.`)
  await load()
}
const archive = async (list: ListedTarget) => {
  if (acting.value) return
  acting.value = true
  const answered = await archiveTarget(SYSTEM, list.externalId)
  acting.value = false
  if (!answered.ok) return said(answered.reason)
  said(`${list.label} is in the archive.`)
  await load()
}

const keyOf = (row: OverviewRow) => (row.missing ? `missing-${row.missing.targetId}` : `list-${row.list.externalId}`)
const people = (row: OverviewRow) => String(row.missing?.memberCount ?? row.list?.memberCount ?? "")

/** The new-list dialog: a name, and a folder picked or a new one named. */
const creating = ref(false)
const folders = ref<string[]>([])
const newName = ref("")
const newFolder = ref<string | null>(null)
const newFolderName = ref("")
const refusal = ref<string | null>(null)
const newStep = ref<"fill" | "preview" | "confirm">("fill")
const newFolderSaid = computed(() => {
  if (newFolder.value === NEW_FOLDER) return newFolderName.value.trim() ? `${newFolderName.value.trim()}, a new folder created first` : "No folder"
  return newFolder.value ?? "No folder"
})
const newPairs = computed(() => [
  {label: "Name in Brevo", value: newName.value.trim()},
  {label: "Folder", value: newFolderSaid.value},
  {label: "People", value: "None. A list made by hand starts empty"},
  {label: "Follows", value: "Nothing, until a cohort is linked to it"},
])
const folderOptions = computed(() => [
  ...folders.value.map((name) => ({key: name, label: name})),
  {key: NEW_FOLDER, label: "New folder…"},
])

const openCreate = async () => {
  creating.value = true
  newStep.value = "fill"
  newName.value = ""
  newFolder.value = null
  newFolderName.value = ""
  refusal.value = null
  folders.value = await fetchTargetFolders(SYSTEM).catch(() => [])
}

const confirmCreate = async () => {
  const name = newName.value.trim()
  const isNew = newFolder.value === NEW_FOLDER
  const folder = isNew ? newFolderName.value.trim() : newFolder.value
  if (name === "" || acting.value) return
  acting.value = true
  try {
    if (isNew && folder) {
      const made = await createFolderInSystem(SYSTEM, folder)
      if (!made.ok) return void (refusal.value = made.reason)
    }
    const created = await createListInSystem(SYSTEM, name, folder || null)
    if (!created.ok) return void (refusal.value = created.reason)
    creating.value = false
    said(`${created.saved.label} is made.`)
    await load()
  } finally {
    acting.value = false
  }
}

/** The folder tidy: every move of a linked list ticked, one matched only by name left for the reader, and applied once confirmed. */
const tidying = ref(false)
const tidy = ref<TidyPlan | null>(null)
const tidyPicked = ref<Set<string>>(new Set())
const tidyFailures = ref<string[]>([])
const tidyRefusal = ref<string | null>(null)

const openTidy = async () => {
  tidying.value = true
  tidy.value = null
  tidyFailures.value = []
  tidyRefusal.value = null
  try {
    tidy.value = await fetchTidyPlan(SYSTEM)
    tidyPicked.value = new Set(tidy.value.moves.filter((move) => !move.byName).map((move) => move.externalId))
  } catch {
    tidyRefusal.value = "Brevo could not be read, so nothing can be proposed."
  }
}

const tidyLinked = computed(() => tidy.value?.moves.filter((move) => !move.byName) ?? [])
const tidyNamed = computed(() => tidy.value?.moves.filter((move) => move.byName) ?? [])
const pickTidy = (externalId: string, picked: boolean) => {
  const next = new Set(tidyPicked.value)
  if (picked) next.add(externalId)
  else next.delete(externalId)
  tidyPicked.value = next
}

const applyPicked = async () => {
  if (tidyPicked.value.size === 0 || acting.value) return
  acting.value = true
  try {
    const result = await applyTidy(SYSTEM, [...tidyPicked.value])
    tidyFailures.value = result.failed.map((one) => `${one.label}: ${one.message}`)
    said(`${result.moved.length} ${result.moved.length === 1 ? "list" : "lists"} moved.`)
    if (tidyFailures.value.length === 0) tidying.value = false
    await load()
  } catch {
    tidyRefusal.value = "The tidy could not be applied."
  } finally {
    acting.value = false
  }
}

onMounted(load)
</script>

<template>
  <management-page
    eyebrow="Platforms"
    testid="brevo-page"
    title="Brevo"
  >
    <template #lede>
      The mailing lists in the association's Brevo account: which rule fills each one, and where it differs from Brevo.
    </template>
    <template #actions>
      <cut-button
        testid="brevo-new-list"
        @click="openCreate"
      >
        New list
      </cut-button>
      <cut-button
        testid="brevo-tidy"
        tone="quiet"
        @click="openTidy"
      >
        Tidy folders
      </cut-button>
    </template>

    <p
      v-if="loaded && !overview"
      class="brevo__note"
      data-testid="brevo-unreadable"
    >
      Brevo could not be read. Try again in a moment.
    </p>

    <template v-if="overview">
      <notice-box
        v-if="notice"
        class="brevo__notice"
        testid="brevo-missing"
        :title="notice.title"
        tone="danger"
      >
        <p>{{ notice.body }}</p>
        <div class="brevo__acts">
          <cut-button
            :disabled="acting"
            small
            testid="brevo-create-missing"
            @click="previewMissing([])"
          >
            {{ overview.missing.length === 1 ? "Review and create the list" : `Review and create ${overview.missing.length} lists` }}
          </cut-button>
        </div>
      </notice-box>

      <folder-care
        :after="overview"
        class="brevo__notice"
        :system="SYSTEM"
        testid="brevo-folders"
        @changed="load"
      />

      <fact-list
        class="brevo__facts"
        :facts="facts"
      />

      <management-table
        :columns="COLUMNS"
        :row-key="keyOf"
        :row-testid="(row) => `brevo-row-${keyOf(row)}`"
        :header-state="headerState"
        :rows="rows"
        :selected-count="selectedIdsArray.length"
        testid="brevo-table"
        :to="linkOf"
        :total="everyList.length"
        @clear-selection="clearSelection"
        @select-all="selectMany(everyList.map(keyOf))"
        @toggle-shown="toggleHeader"
      >
        <template #check="{row}">
          <row-check
            v-if="row.list"
            :checked="isSelected(keyOf(row))"
            :label="`Select ${row.list.label}`"
            :testid="`brevo-check-${keyOf(row)}`"
            @toggle="toggle(keyOf(row))"
          />
        </template>
        <template #count>
          {{ rows.length }} of {{ total }} lists
        </template>
        <template #search>
          <search-box
            v-model="search"
            label="Search lists"
            testid="brevo-search"
          />
        </template>
        <template #empty>
          <span data-testid="brevo-empty">No list matches.</span>
        </template>
        <template #name="{row}">
          <span
            v-if="row.missing"
            class="mg-name"
          >{{ row.missing.cohortLabel }}</span>
          <router-link
            v-else
            class="mg-name"
            :to="`/management/platforms/brevo/lists/${row.list.externalId}`"
          >
            {{ row.list.label }}
          </router-link>
          <span
            class="mg-sub"
            :data-testid="`brevo-folder-${keyOf(row)}`"
          >{{ row.kind === "unlinked" ? `${row.folder} · made by hand in Brevo` : row.folder }}</span>
        </template>
        <template #follows="{row}">
          <span class="brevo__follows">{{ followsOf(row) }}</span>
        </template>
        <template #people="{row}">
          {{ people(row) }}
        </template>
        <template #state="{row}">
          <state-mark
            v-if="row.missing"
            kind="not-created"
            :testid="`brevo-state-${keyOf(row)}`"
          >
            {{ stateWord(row) }}
          </state-mark>
          <state-mark
            v-else-if="row.kind === 'archive'"
            kind="not-compared"
            :testid="`brevo-state-${keyOf(row)}`"
          >
            {{ stateWord(row) }}
          </state-mark>
          <span
            v-else
            class="mg-marks"
            :data-testid="`brevo-state-${keyOf(row)}`"
          >
            <state-mark
              v-for="mark in driftMarksOf(row.list)"
              :key="mark.word"
              :kind="mark.kind"
            >
              {{ mark.word }}
            </state-mark>
          </span>
        </template>
        <template #reconciled="{row}">
          <span :class="{'mg-quiet': !row.list?.lastReconciledAt}">{{ row.list?.lastReconciledAt ? formatMoment(row.list.lastReconciledAt) : "Never" }}</span>
        </template>
        <template #acts="{row}">
          <mini-button
            v-if="row.missing"
            :disabled="acting || row.missing.creating"
            :testid="`brevo-create-${row.missing.targetId}`"
            @click="previewMissing([row.missing.targetId])"
          >
            Create
          </mini-button>
          <mini-button
            v-else-if="row.list.targetId == null && row.kind !== 'archive'"
            :disabled="acting"
            :testid="`brevo-archive-${row.list.externalId}`"
            @click="archive(row.list)"
          >
            Archive
          </mini-button>
        </template>
        <template #phone="{row}">
          <management-row
            :meta="`${row.folder} · ${followsOf(row)}`"
            :name="row.missing ? row.missing.cohortLabel : row.list.label"
            :testid="`brevo-row-${keyOf(row)}`"
            :to="linkOf(row) ?? ''"
          >
            <state-mark :kind="row.missing ? 'not-created' : driftOf(row.list).kind">
              {{ row.missing ? "Not created yet" : driftOf(row.list).word }}
            </state-mark>
          </management-row>
        </template>
      </management-table>

      <selection-bar
        always
        :count="selectedIdsArray.length"
        testid="brevo-selection"
        @clear="clearSelection"
      >
        <cut-button
          small
          testid="brevo-bulk-compare"
          tone="solid"
          @click="sync.task.value = 'compare'"
        >
          Compare with Brevo
        </cut-button>
        <cut-button
          small
          testid="brevo-bulk-push"
          @click="sync.task.value = 'push'"
        >
          Add missing people
        </cut-button>
      </selection-bar>

      <bulk-add
        :items="sync.items.value"
        :noun="['list', 'lists']"
        :open="sync.task.value !== null"
        :run="sync.run"
        :skipped="sync.skipped.value"
        testid="brevo-bulk-sync"
        :title="sync.title.value"
        :words="sync.words.value"
        @done="synced"
        @update:open="sync.task.value = null"
      />
    </template>

    <modal-dialog
      :open="tidying"
      testid="brevo-tidy-dialog"
      title="Tidy folders"
      @update:open="tidying = $event"
    >
      <div class="brevo__form">
        <p class="brevo__note">
          Moves each list that follows a cohort into its cohort type's folder, and offers the same for a list named like a
          cohort. Nothing moves until you apply it, and nothing moves a list back afterwards.
        </p>
        <p
          v-if="tidy"
          class="brevo__note brevo__note--small"
          data-testid="brevo-tidy-last"
        >
          {{ lastTidyLine(tidy.lastApplied) }}
        </p>
        <p
          v-if="tidy && tidy.moves.length === 0"
          class="brevo__note"
          data-testid="brevo-tidy-none"
        >
          Every list is in its folder.
        </p>
        <ul
          v-if="tidy && tidyLinked.length"
          class="brevo__moves"
          data-testid="brevo-tidy-moves"
        >
          <li
            v-for="move in tidyLinked"
            :key="move.externalId"
          >
            <check-box
              :label="`${move.label}: ${move.from ?? 'no folder'} to ${move.to}`"
              :model-value="tidyPicked.has(move.externalId)"
              :testid="`brevo-tidy-pick-${move.externalId}`"
              @update:model-value="pickTidy(move.externalId, $event)"
            />
          </li>
        </ul>
        <template v-if="tidyNamed.length">
          <p class="brevo__note brevo__note--small">
            Lists that follow no cohort but are named like one. Tick the ones to move.
          </p>
          <ul
            class="brevo__moves"
            data-testid="brevo-tidy-named"
          >
            <li
              v-for="move in tidyNamed"
              :key="move.externalId"
            >
              <check-box
                :label="`${move.label}: ${move.from ?? 'no folder'} to ${move.to}`"
                :model-value="tidyPicked.has(move.externalId)"
                :testid="`brevo-tidy-pick-${move.externalId}`"
                @update:model-value="pickTidy(move.externalId, $event)"
              />
            </li>
          </ul>
        </template>
        <p
          v-if="tidy && tidy.foldersToCreate.length"
          class="brevo__note brevo__note--small"
        >
          Creates {{ tidy.foldersToCreate.join(", ") }} first.
        </p>
        <p
          v-for="failure in tidyFailures"
          :key="failure"
          class="brevo__failure"
          data-testid="brevo-tidy-failure"
        >
          {{ failure }}
        </p>
        <p
          v-if="tidyRefusal"
          class="brevo__failure"
          data-testid="brevo-tidy-refusal"
          role="alert"
        >
          {{ tidyRefusal }}
        </p>
      </div>
      <template #footer>
        <cut-button
          :disabled="acting || tidyPicked.size === 0"
          testid="brevo-tidy-apply"
          tone="solid"
          @click="applyPicked"
        >
          Move {{ tidyPicked.size }} {{ tidyPicked.size === 1 ? "list" : "lists" }}
        </cut-button>
      </template>
    </modal-dialog>

    <modal-dialog
      :open="creating"
      testid="brevo-new-list-dialog"
      title="New list"
      @update:open="creating = $event"
    >
      <form
        v-if="newStep === 'fill'"
        id="brevo-new-form"
        class="brevo__form"
        @submit.prevent="newStep = 'preview'"
      >
        <form-field
          v-slot="field"
          label="Name in Brevo"
          testid="brevo-new-name"
        >
          <text-input
            v-model="newName"
            :control-id="field.controlId"
          />
        </form-field>
        <form-field
          label="Folder"
          testid="brevo-new-folder"
        >
          <template #default="{controlId, labelId}">
            <search-picker
              :control-id="controlId"
              :labelled-by="labelId"
              :options="folderOptions"
              :selected-key="newFolder"
              testid-prefix="brevo-new-folder-picker"
              @pick="(key: string) => newFolder = key"
            />
          </template>
        </form-field>
        <form-field
          v-if="newFolder === NEW_FOLDER"
          v-slot="field"
          label="New folder's name"
          testid="brevo-new-folder-name"
        >
          <text-input
            v-model="newFolderName"
            :control-id="field.controlId"
          />
        </form-field>
      </form>
      <div
        v-else
        class="brevo__form"
        :data-testid="`brevo-new-step-${newStep}`"
      >
        <p
          v-if="newStep === 'preview'"
          class="brevo__note"
        >
          This is what will be created. Nothing is made in Brevo yet.
        </p>
        <notice-box
          v-else
          :title="`Create ${newName.trim()} in Brevo?`"
          tone="warning"
        >
          A list cannot be taken back from here: it is deleted in Brevo itself.
        </notice-box>
        <pair-list :pairs="newPairs" />
        <p
          v-if="refusal"
          class="brevo__failure"
          data-testid="brevo-new-refusal"
          role="alert"
        >
          {{ refusal }}
        </p>
      </div>
      <template #footer>
        <cut-button
          v-if="newStep !== 'fill'"
          testid="brevo-new-back"
          tone="quiet"
          @click="newStep = newStep === 'confirm' ? 'preview' : 'fill'"
        >
          Back
        </cut-button>
        <cut-button
          v-if="newStep === 'fill'"
          :disabled="newName.trim() === ''"
          form="brevo-new-form"
          submit
          testid="brevo-new-preview"
          tone="solid"
        >
          Preview
        </cut-button>
        <cut-button
          v-else-if="newStep === 'preview'"
          testid="brevo-new-continue"
          tone="solid"
          @click="newStep = 'confirm'"
        >
          Continue
        </cut-button>
        <cut-button
          v-else
          :disabled="acting"
          testid="brevo-new-confirm"
          tone="solid"
          @click="confirmCreate"
        >
          Create the list
        </cut-button>
      </template>
    </modal-dialog>
    <modal-dialog
      :open="pending.length > 0"
      testid="brevo-create-dialog"
      :title="pending.length === 1 ? 'Create a list in Brevo' : `Create ${pending.length} lists in Brevo`"
      @update:open="pending = $event ? pending : []"
    >
      <div
        v-if="createStep === 'preview'"
        class="brevo__form"
        data-testid="brevo-create-preview"
      >
        <p class="brevo__note">
          This is what will be created. Nothing is made in Brevo yet.
        </p>
        <pair-list :pairs="pendingPairs" />
        <p class="brevo__note brevo__note--small">
          Each list is created in its folder, and the people its rule covers are added to it.
        </p>
      </div>
      <div
        v-else
        class="brevo__form"
        data-testid="brevo-create-confirm"
      >
        <notice-box
          :title="pending.length === 1 ? `Create ${pending[0]!.cohortLabel} in Brevo?` : `Create these ${pending.length} lists in Brevo?`"
          tone="warning"
        >
          {{ pendingPeople }} {{ pendingPeople === 1 ? "person is" : "people are" }} added across {{ pending.length === 1 ? "it" : "them" }}.
          A list cannot be taken back from here: it is deleted in Brevo itself.
        </notice-box>
        <p
          v-if="createRefusal"
          class="brevo__failure"
          data-testid="brevo-create-refusal"
          role="alert"
        >
          {{ createRefusal }}
        </p>
      </div>
      <template #footer>
        <cut-button
          v-if="createStep === 'preview'"
          testid="brevo-create-continue"
          tone="solid"
          @click="createStep = 'confirm'"
        >
          Continue
        </cut-button>
        <template v-else>
          <cut-button
            testid="brevo-create-back"
            tone="quiet"
            @click="createStep = 'preview'"
          >
            Back
          </cut-button>
          <cut-button
            :disabled="acting"
            testid="brevo-create-go"
            tone="solid"
            @click="createMissing"
          >
            {{ pending.length === 1 ? "Create the list" : `Create ${pending.length} lists` }}
          </cut-button>
        </template>
      </template>
    </modal-dialog>
  </management-page>
</template>

<style scoped>
.brevo__facts {
  padding: 1.1rem 0 1.2rem;
}

.brevo__notice {
  margin-bottom: 1.2rem;
}

.brevo__follows {
  font-size: 0.84rem;
  color: var(--color-ash);
}

.brevo__form {
  display: flex;
  flex-direction: column;
  gap: 0.8rem;
}

.brevo__acts {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 0.6rem;
}

.brevo__moves {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.brevo__note {
  font-size: 0.92rem;
  line-height: 1.5;
  color: var(--color-ash);
}

.brevo__note--small {
  font-size: 0.84rem;
}

.brevo__failure {
  font-size: 0.88rem;
  color: var(--color-danger);
}
</style>
