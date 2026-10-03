<script lang="ts" setup>
/* Every list in the association's Brevo account by the folder it is really in, with the cohort that
   fills it, its people and its drift. Lists the site expects and that are missing come first. */
import {computed, onMounted, ref} from "vue"
import FactList from "@/components/island/FactList.vue"
import FoldOut from "@/components/island/FoldOut.vue"
import FormField from "@/components/island/FormField.vue"
import ModalDialog from "@/components/island/ModalDialog.vue"
import NoticeBox from "@/components/island/NoticeBox.vue"
import SearchBox from "@/components/island/SearchBox.vue"
import SearchPicker from "@/components/island/SearchPicker.vue"
import StateMark from "@/components/island/StateMark.vue"
import CheckBox from "@/components/island/CheckBox.vue"
import TextInput from "@/components/island/TextInput.vue"
import {
  type ListedTarget,
  type OverviewRow,
  type TargetOverview,
  TargetSystem,
  type TidyPlan,
  applyTidy,
  archiveTarget,
  createFolderInSystem,
  createListInSystem,
  createMissingLists,
  driftOf,
  fetchTargetFolders,
  fetchTidyPlan,
  followsOf,
  groupsOf,
  lastTidyLine,
  missingNotice,
  overviewFacts,
  readTargetOverview,
} from "@/domains/cohorts"
import store from "@/plugins/store"
import {formatDateNoSeconds} from "@/utils/timestamps"

defineOptions({name: "BrevoPage"})

const SYSTEM = TargetSystem.BREVO
const NEW_FOLDER = "__new__"

const overview = ref<TargetOverview | null>(null)
const loaded = ref(false)
const search = ref("")
const acting = ref(false)

const groups = computed(() => (overview.value ? groupsOf(overview.value, search.value) : []))
const facts = computed(() => (overview.value ? overviewFacts(overview.value) : []))
const notice = computed(() => missingNotice(overview.value?.missing ?? []))

const load = async () => {
  overview.value = await readTargetOverview(SYSTEM)
  loaded.value = true
}

const said = (message: string) => store.commit("setStatusSnackbarMessage", message)

/** Creating a missing list is a job per list; the row says so until the list exists. */
const createMissing = async (targetIds: number[]) => {
  if (acting.value) return
  acting.value = true
  const answered = await createMissingLists(SYSTEM, targetIds)
  acting.value = false
  if (!answered.ok) return said(answered.reason)
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
const folderOptions = computed(() => [
  ...folders.value.map((name) => ({key: name, label: name})),
  {key: NEW_FOLDER, label: "New folder…"},
])

const openCreate = async () => {
  creating.value = true
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

/** The folder tidy: every proposed move ticked, and applied only once confirmed. */
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
    tidyPicked.value = new Set(tidy.value.moves.map((move) => move.externalId))
  } catch {
    tidyRefusal.value = "Brevo could not be read, so nothing can be proposed."
  }
}

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
  <div
    class="brevo"
    data-testid="brevo-page"
  >
    <header class="brevo__head">
      <div>
        <p class="brevo__eyebrow">
          Platforms
        </p>
        <h1 class="brevo__title">
          Brevo
        </h1>
        <p class="brevo__note">
          The mailing lists in the association's Brevo account: which rule fills each one, and where it differs from Brevo.
        </p>
      </div>
      <div class="brevo__acts">
        <button
          class="brevo__action"
          data-testid="brevo-new-list"
          type="button"
          @click="openCreate"
        >
          New list
        </button>
        <button
          class="brevo__action"
          data-testid="brevo-tidy"
          type="button"
          @click="openTidy"
        >
          Tidy folders
        </button>
      </div>
    </header>

    <p
      v-if="loaded && !overview"
      class="brevo__note"
      data-testid="brevo-unreadable"
    >
      Brevo could not be read. Try again in a moment.
    </p>

    <notice-box
      v-if="notice"
      testid="brevo-missing"
      :title="notice.title"
      tone="danger"
    >
      <p>{{ notice.body }}</p>
      <button
        class="brevo__action brevo__action--on-notice"
        data-testid="brevo-create-missing"
        :disabled="acting"
        type="button"
        @click="createMissing([])"
      >
        {{ overview!.missing.length === 1 ? "Create the list" : `Create ${overview!.missing.length} lists` }}
      </button>
    </notice-box>

    <template v-if="overview">
      <fact-list :facts="facts" />

      <search-box
        v-model="search"
        label="Search lists"
        testid="brevo-search"
      />

      <p
        v-if="groups.length === 0"
        class="brevo__note"
        data-testid="brevo-empty"
      >
        No list matches.
      </p>

      <component
        :is="group.kind === 'archive' ? FoldOut : 'section'"
        v-for="group in groups"
        :key="group.name"
        class="brevo__group"
        :data-testid="`brevo-group-${group.name}`"
        v-bind="group.kind === 'archive' ? {label: `${group.name} · ${group.rows.length}`, testid: `brevo-group-${group.name}`} : {}"
      >
        <p
          v-if="group.kind !== 'archive'"
          class="brevo__folder"
        >
          <span>{{ group.name }}</span>
          <span class="brevo__sub">{{ group.rows.length }} {{ group.rows.length === 1 ? "list" : "lists" }}</span>
        </p>
        <p
          v-if="group.kind === 'unlinked'"
          class="brevo__note brevo__note--small"
        >
          Lists made by hand in Brevo. The site leaves their people alone: link one from its cohort's page, or archive it.
        </p>
        <ul class="brevo__rows">
          <li
            v-for="row in group.rows"
            :key="keyOf(row)"
            class="brevo__row"
            :data-testid="`brevo-row-${keyOf(row)}`"
          >
            <span class="brevo__name">
              <template v-if="row.missing">{{ row.missing.cohortLabel }}</template>
              <router-link
                v-else
                :to="`/management/platforms/brevo/lists/${row.list.externalId}`"
              >{{ row.list.label }}</router-link>
            </span>
            <span class="brevo__sub brevo__follows">{{ followsOf(row) }}</span>
            <span class="brevo__sub">{{ people(row) }}</span>
            <state-mark
              v-if="row.missing"
              kind="not-created"
              :testid="`brevo-state-${keyOf(row)}`"
            >
              {{ row.missing.creating ? "Being created" : "Not created yet" }}
            </state-mark>
            <state-mark
              v-else
              :kind="driftOf(row.list).kind"
              :testid="`brevo-state-${keyOf(row)}`"
            >
              {{ driftOf(row.list).word }}
            </state-mark>
            <span class="brevo__sub">{{ row.list?.lastReconciledAt ? formatDateNoSeconds(row.list.lastReconciledAt) : "Never" }}</span>
            <span class="brevo__row-acts">
              <button
                v-if="row.missing"
                class="brevo__mini"
                :data-testid="`brevo-create-${row.missing.targetId}`"
                :disabled="acting"
                type="button"
                @click="createMissing([row.missing.targetId])"
              >
                Create
              </button>
              <button
                v-else-if="row.list.targetId == null && group.kind !== 'archive'"
                class="brevo__mini"
                :data-testid="`brevo-archive-${row.list.externalId}`"
                :disabled="acting"
                type="button"
                @click="archive(row.list)"
              >
                Archive
              </button>
            </span>
          </li>
        </ul>
      </component>
    </template>

    <modal-dialog
      :open="tidying"
      testid="brevo-tidy-dialog"
      title="Tidy folders"
      @update:open="tidying = $event"
    >
      <div class="brevo__form">
        <p class="brevo__note">
          Moves each list that follows a cohort into its cohort type's folder. Nothing moves until you apply it, and nothing moves a list back afterwards.
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
          v-if="tidy && tidy.moves.length"
          class="brevo__moves"
          data-testid="brevo-tidy-moves"
        >
          <li
            v-for="move in tidy.moves"
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
        <p
          v-if="tidy && tidy.foldersToCreate.length"
          class="brevo__note brevo__note--small"
        >
          Makes {{ tidy.foldersToCreate.join(", ") }} first.
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
        <button
          class="brevo__action brevo__action--main"
          data-testid="brevo-tidy-apply"
          :disabled="acting || tidyPicked.size === 0"
          type="button"
          @click="applyPicked"
        >
          Move {{ tidyPicked.size }} {{ tidyPicked.size === 1 ? "list" : "lists" }}
        </button>
      </template>
    </modal-dialog>

    <modal-dialog
      :open="creating"
      testid="brevo-new-list-dialog"
      title="New list"
      @update:open="creating = $event"
    >
      <form
        class="brevo__form"
        @submit.prevent="confirmCreate"
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
        <p
          v-if="refusal"
          class="brevo__failure"
          data-testid="brevo-new-refusal"
          role="alert"
        >
          {{ refusal }}
        </p>
        <button
          class="brevo__action brevo__action--main"
          data-testid="brevo-new-confirm"
          :disabled="acting || newName.trim() === ''"
          type="submit"
        >
          Make the list
        </button>
      </form>
    </modal-dialog>
  </div>
</template>

<style scoped>
.brevo {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  max-width: 76rem;
  padding: 2rem 2.4rem 3rem;
}

.brevo__head {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: 1rem;
}

.brevo__eyebrow {
  margin: 0;
  font-size: 11px;
  letter-spacing: 0.3em;
  text-transform: uppercase;
  color: var(--color-eyebrow, var(--color-ash));
}

.brevo__title {
  margin: 0;
  font-family: var(--font-display);
  font-size: clamp(1.4rem, 3vw, 2rem);
}

.brevo__note {
  margin: 0;
  max-width: 48rem;
  color: var(--color-ash);
}

.brevo__note--small {
  font-size: 0.86rem;
}

.brevo__failure {
  margin: 0;
  color: var(--color-error, #e5484d);
}

.brevo__acts {
  display: flex;
  gap: 0.5rem;
}

.brevo__action,
.brevo__mini {
  padding: 0.45rem 0.9rem;
  border: 1px solid var(--color-hairline);
  background: none;
  font: inherit;
  font-size: 0.86rem;
  color: var(--color-chalk);
  cursor: pointer;
}

.brevo__mini {
  padding: 0.25rem 0.6rem;
  font-size: 0.8rem;
}

/* The notice's tint takes the brand text below contrast, so the button keeps the page's ink. */
.brevo__action--on-notice {
  align-self: flex-start;
  border-color: var(--color-brand);
}

.brevo__action--main {
  align-self: flex-start;
  border-color: var(--color-brand);
  color: var(--color-brand);
}

.brevo__action:disabled,
.brevo__mini:disabled {
  opacity: 0.45;
  cursor: default;
}

.brevo__group {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
}

.brevo__folder {
  display: flex;
  align-items: baseline;
  gap: 0.8rem;
  margin: 0.8rem 0 0;
  font-weight: 600;
}

.brevo__rows {
  display: flex;
  flex-direction: column;
  margin: 0;
  padding: 0;
  list-style: none;
  border-top: 1px solid var(--color-hairline);
}

.brevo__row {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(0, 1.4fr) 4rem 10rem 8.5rem 6rem;
  align-items: center;
  gap: 1rem;
  padding: 0.65rem 0.4rem;
  border-bottom: 1px solid var(--color-hairline);
}

.brevo__name {
  overflow: hidden;
  font-weight: 600;
  text-overflow: ellipsis;
}

.brevo__name a {
  color: var(--color-chalk);
}

.brevo__sub {
  overflow: hidden;
  font-size: 0.84rem;
  color: var(--color-ash);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.brevo__row-acts {
  display: flex;
  justify-content: flex-end;
}

.brevo__moves {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
  margin: 0;
  padding: 0;
  list-style: none;
}

.brevo__form {
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
}

@media (max-width: 839px) {
  .brevo {
    padding: 1.2rem 1.1rem 2rem;
  }

  .brevo__row {
    grid-template-columns: minmax(0, 1fr) auto;
  }

  .brevo__row > .brevo__sub:not(.brevo__follows) {
    display: none;
  }

  .brevo__follows {
    grid-row: 2;
  }
}
</style>
