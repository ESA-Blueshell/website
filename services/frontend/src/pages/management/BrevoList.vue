<script lang="ts" setup>
/* One Brevo list: the cohort that fills it, its people in sync and its drift, each drifting person
   with why and what can be done, the resolutions so far, and the list itself to rename, move,
   archive, enforce or delete. */
import CutButton from "@/components/island/CutButton.vue"
import CutRow from "@/components/island/CutRow.vue"
import ListHead from "@/components/management/ListHead.vue"
import ManagementPage from "@/components/management/ManagementPage.vue"
import {computed, onMounted, ref, watch} from "vue"
import {useRoute, useRouter} from "vue-router"
import FactList from "@/components/island/FactList.vue"
import FormField from "@/components/island/FormField.vue"
import ModalDialog from "@/components/island/ModalDialog.vue"
import SearchPicker from "@/components/island/SearchPicker.vue"
import TextInput from "@/components/island/TextInput.vue"
import {
  ARCHIVE_FOLDER,
  type Cohort,
  type ListedTarget,
  type MissingTarget,
  TargetDrift,
  TargetSystem,
  archiveTarget,
  cohortTypeLabel,
  deleteTarget,
  driftRowsOf,
  createFolderInSystem,
  fetchCohort,
  fetchTargetFolders,
  inSyncOn,
  linkExistingTargetForCohort,
  moveTargetToFolder,
  readListedTarget,
  readTargetOverview,
  renameTarget,
  runBars,
  setTargetEnforced,
  triggerReconcile,
} from "@/domains/cohorts"
import store from "@/plugins/store"
import {formatDateNoSeconds} from "@/utils/timestamps"

defineOptions({name: "BrevoListPage"})

const SYSTEM = TargetSystem.BREVO

const route = useRoute()
const router = useRouter()
const externalId = computed(() => String(route.params.externalId))
const isAdmin = computed(() => store.getters.isAdmin === true)

const list = ref<ListedTarget | null>(null)
const cohort = ref<Cohort | null>(null)
const loaded = ref(false)
const acting = ref(false)

const mapping = computed(() => cohort.value?.mappings.find((one) => one.system === SYSTEM) ?? null)
const cohortId = computed(() => cohort.value?.id ?? null)
const members = computed(() => cohort.value?.members ?? [])
const allDrift = computed(() => driftRowsOf(members.value, SYSTEM))
const said = (message: string) => store.commit("setStatusSnackbarMessage", message)

const load = async () => {
  list.value = await readListedTarget(SYSTEM, externalId.value)
  cohort.value = list.value?.cohortId != null ? await fetchCohort(list.value.cohortId) : null
  name.value = list.value?.label ?? ""
  folder.value = list.value?.folderLabel ?? null
  loaded.value = true
  if (list.value && list.value.cohortId == null) unlinkedCohorts.value = (await readTargetOverview(SYSTEM))?.missing ?? []
}

const facts = computed(() => {
  const latest = mapping.value?.runs.at(0)
  return [
    {label: "Follows", value: cohort.value?.label ?? "", sub: cohort.value ? cohortTypeLabel(cohort.value.type) : ""},
    {
      label: "People",
      value: `${inSyncOn(members.value, SYSTEM)} in sync`,
      sub: `${allDrift.value.filter((one) => one.sync === "ONLY_HERE").length} missing · ${allDrift.value.filter((one) => one.sync === "ONLY_EXTERNAL").length} additional`,
      testid: "brevo-list-people",
    },
    {
      label: "Reconciled",
      value: latest ? formatDateNoSeconds(latest.startedAt) : "Never",
      sub: mapping.value?.enforced ? "Enforced: additional people are removed at every reconcile" : "Nightly, and after every change",
    },
  ]
})
const bars = computed(() => runBars(mapping.value?.runs ?? []))

const reconcile = async () => {
  if (!cohortId.value || !mapping.value || acting.value) return
  acting.value = true
  const answered = await triggerReconcile(cohortId.value, mapping.value.targetId)
  acting.value = false
  said(answered.ok ? "A reconcile is queued." : answered.reason)
}

/** This list: its name and folder, saved together. */
const name = ref("")
const folder = ref<string | null>(null)
const folders = ref<string[]>([])
// A folder Brevo does not have yet is named here and made as the list is saved.
const NEW_FOLDER = "__new__"
const newFolderName = ref("")
const folderOptions = computed(() => [...folders.value.map((one) => ({key: one, label: one})), {key: NEW_FOLDER, label: "New folder…"}])
const wanted = computed(() => (folder.value === NEW_FOLDER ? newFolderName.value.trim() || null : folder.value))
const changed = computed(() => {
  if (!list.value || (folder.value === NEW_FOLDER && wanted.value === null)) return false
  return name.value.trim() !== list.value.label || wanted.value !== (list.value.folderLabel ?? null)
})

const save = async () => {
  const current = list.value
  if (!current || !changed.value || acting.value || name.value.trim() === "") return
  acting.value = true
  try {
    if (name.value.trim() !== current.label) {
      const renamed = await renameTarget(SYSTEM, current.externalId, name.value.trim())
      if (!renamed.ok) return said(renamed.reason)
    }
    if (folder.value === NEW_FOLDER && wanted.value) {
      const made = await createFolderInSystem(SYSTEM, wanted.value)
      if (!made.ok) return said(made.reason)
    }
    if (wanted.value && wanted.value !== current.folderLabel) await moveTargetToFolder(SYSTEM, current.externalId, wanted.value)
    newFolderName.value = ""
    said("The list is saved.")
    await load()
  } catch {
    said("The list could not be moved.")
  } finally {
    acting.value = false
  }
}

const enforce = async () => {
  if (!cohortId.value || !mapping.value || acting.value) return
  acting.value = true
  const answered = await setTargetEnforced(cohortId.value, mapping.value.targetId, !mapping.value.enforced)
  acting.value = false
  if (!answered.ok) return said(answered.reason)
  await load()
}

const archive = async () => {
  if (!list.value || acting.value) return
  acting.value = true
  const answered = await archiveTarget(SYSTEM, list.value.externalId)
  acting.value = false
  if (!answered.ok) return said(answered.reason)
  said(`${list.value.label} is in the archive.`)
  await load()
}

/** Delete, for an admin and a list that follows nothing, confirmed by its name typed exactly. */
const deleting = ref(false)
const typedName = ref("")
const deleteFailure = ref<string | null>(null)
const confirmDelete = async () => {
  if (!list.value || acting.value) return
  acting.value = true
  const answered = await deleteTarget(SYSTEM, list.value.externalId, typedName.value)
  acting.value = false
  if (!answered.ok) return void (deleteFailure.value = answered.reason)
  deleting.value = false
  said(`${list.value.label} is deleted from Brevo.`)
  await router.push("/management/platforms/brevo")
}

/** A list that follows nothing is linked to a cohort whose list is missing. */
const unlinkedCohorts = ref<MissingTarget[]>([])
const linkTo = ref<string | null>(null)
const linkOptions = computed(() => unlinkedCohorts.value.map((one) => ({key: String(one.cohortId), label: one.cohortLabel, note: cohortTypeLabel(one.cohortType)})))
const link = async () => {
  if (!list.value || linkTo.value == null || acting.value) return
  acting.value = true
  try {
    const linked = await linkExistingTargetForCohort(Number(linkTo.value), SYSTEM, list.value.externalId)
    said(linked.type === "ok" ? `${list.value.label} now follows ${linked.mapping.label}.` : "That cohort already has a list.")
    await load()
  } catch {
    said("The list could not be linked.")
  } finally {
    acting.value = false
  }
}

watch(externalId, load)
onMounted(async () => {
  await load()
  folders.value = await fetchTargetFolders(SYSTEM).catch(() => [])
})
</script>

<template>
  <management-page
    v-if="loaded && !list"
    :back="{to: '/management/platforms/brevo', label: 'Brevo'}"
    eyebrow="Platforms · Brevo"
    testid="brevo-list"
    title="No such list"
  >
    <p
      class="list__note"
      data-testid="brevo-list-missing"
    >
      Brevo has no such list. It may have been deleted there.
    </p>
  </management-page>
  <management-page
    v-else-if="list"
    :back="{to: '/management/platforms/brevo', label: 'Brevo'}"
    :eyebrow="`Brevo list · ${list.folderLabel ? `${list.folderLabel} folder` : 'No folder'}`"
    testid="brevo-list"
    :title="list.label"
  >
    <template #lede>
      <template v-if="cohort">
        Mail sent to this list reaches {{ cohort.label }}: {{ cohortTypeLabel(cohort.type).toLowerCase() }}.
      </template>
      <template v-else>
        This list is not linked to a cohort, so the site leaves its people alone.
      </template>
    </template>
    <template
      v-if="mapping"
      #actions
    >
      <cut-button
        :disabled="acting"
        testid="brevo-list-reconcile"
        @click="reconcile"
      >
        Compare with Brevo now
      </cut-button>
    </template>

    <template v-if="cohort && mapping">
      <fact-list
        class="list__facts"
        :facts="facts"
      />
      <p
        v-if="bars.length"
        :aria-label="`Drift over the last ${bars.length} runs`"
        class="list__runs"
        data-testid="brevo-list-runs"
        role="img"
      >
        <span
          v-for="(bar, index) in bars"
          :key="index"
          class="list__bar"
          :class="{'list__bar--drift': bar.drift}"
          :style="{height: `${bar.height}px`}"
        />
      </p>
      <target-drift
        :cohort="cohort"
        :mapping="mapping"
        :reload="load"
        testid="brevo-list"
      />
    </template>

    <section
      v-if="!list.cohortId"
      data-testid="brevo-list-link"
    >
      <list-head title="Not linked to a cohort" />
      <p class="list__note">
        Link the list to a cohort whose list is missing, and the site fills it from then on.
      </p>
      <div class="list__form">
        <form-field
          label="Cohort"
          testid="brevo-list-link-cohort"
        >
          <template #default="{controlId, labelId}">
            <search-picker
              :control-id="controlId"
              empty-note="Every cohort has its list."
              :labelled-by="labelId"
              :options="linkOptions"
              :selected-key="linkTo"
              testid-prefix="brevo-list-link-picker"
              @pick="(key: string) => linkTo = key"
            />
          </template>
        </form-field>
        <div class="list__acts">
          <cut-button
            :disabled="acting || linkTo == null"
            testid="brevo-list-link-confirm"
            tone="solid"
            @click="link"
          >
            Link
          </cut-button>
        </div>
      </div>
    </section>

    <list-head title="This list" />
    <form
      class="list__form"
      @submit.prevent="save"
    >
      <form-field
        v-slot="field"
        label="Name in Brevo"
        testid="brevo-list-name"
      >
        <text-input
          v-model="name"
          :control-id="field.controlId"
        />
      </form-field>
      <form-field
        label="Folder"
        testid="brevo-list-folder"
      >
        <template #default="{controlId, labelId}">
          <search-picker
            :control-id="controlId"
            :labelled-by="labelId"
            :options="folderOptions"
            :selected-key="folder"
            testid-prefix="brevo-list-folder-picker"
            @pick="(key: string) => folder = key"
          />
        </template>
      </form-field>
      <form-field
        v-if="folder === NEW_FOLDER"
        v-slot="field"
        label="New folder's name"
        testid="brevo-list-new-folder"
      >
        <text-input
          v-model="newFolderName"
          :control-id="field.controlId"
        />
      </form-field>
      <div class="list__acts">
        <cut-button
          :disabled="acting || !changed"
          submit
          testid="brevo-list-save"
          tone="solid"
        >
          Save
        </cut-button>
      </div>
    </form>

    <list-head title="Settings" />
    <div class="list__settings">
      <cut-row
        v-if="mapping && isAdmin"
        :meta="`Remove additional people automatically every time the list is compared. ${mapping.enforced ? 'On.' : 'Off.'}`"
        title="Enforce"
      >
        <template #end>
          <cut-button
            :disabled="acting"
            small
            testid="brevo-list-enforce"
            @click="enforce"
          >
            {{ mapping.enforced ? "Turn off" : "Turn on" }}
          </cut-button>
        </template>
      </cut-row>
      <cut-row
        v-if="list.folderLabel !== ARCHIVE_FOLDER"
        meta="Moves the list to the Archive folder in Brevo. It keeps its people and can be moved back."
        title="Archive"
      >
        <template #end>
          <cut-button
            :disabled="acting"
            small
            testid="brevo-list-archive"
            @click="archive"
          >
            Archive
          </cut-button>
        </template>
      </cut-row>
      <cut-row
        v-if="isAdmin"
        :meta="list.cohortId != null ? 'Only a list that is not linked to a cohort can be deleted.' : 'Deletes the list and its contacts from Brevo for good.'"
        title="Delete"
      >
        <template #end>
          <cut-button
            :disabled="acting || list.cohortId != null"
            small
            testid="brevo-list-delete"
            tone="danger"
            @click="deleting = true; typedName = ''; deleteFailure = null"
          >
            Delete
          </cut-button>
        </template>
      </cut-row>
    </div>

    <modal-dialog
      cancel-testid="brevo-list-delete-cancel"
      danger
      :open="deleting"
      testid="brevo-list-delete-dialog"
      title="Delete the list"
      @update:open="deleting = $event"
    >
      <form
        id="brevo-list-delete-form"
        class="list__form"
        data-testid="brevo-list-delete-form"
        @submit.prevent="confirmDelete"
      >
        <p class="list__note">
          Brevo cannot undo this. Type the list's name, {{ list.label }}, to delete it and its contacts for good.
        </p>
        <form-field
          v-slot="field"
          label="The list's name"
          testid="brevo-list-delete-name"
        >
          <text-input
            v-model="typedName"
            :control-id="field.controlId"
          />
        </form-field>
        <p
          v-if="deleteFailure"
          class="list__failure"
          data-testid="brevo-list-delete-failure"
          role="alert"
        >
          {{ deleteFailure }}
        </p>
      </form>
      <template #footer>
        <cut-button
          :disabled="acting || typedName !== list.label"
          form="brevo-list-delete-form"
          submit
          testid="brevo-list-delete-confirm"
          tone="danger"
        >
          Delete
        </cut-button>
      </template>
    </modal-dialog>
  </management-page>
</template>

<style scoped>
.list__facts {
  padding: 1.1rem 0 0.6rem;
}

.list__runs {
  display: flex;
  align-items: flex-end;
  gap: 4px;
  height: 26px;
  margin-bottom: 0.4rem;
}

.list__bar {
  width: 9px;
  min-height: 2px;
  background: color-mix(in oklab, var(--color-chalk) 18%, transparent);
  transform: skewX(-12deg);
}

.list__bar--drift {
  background: var(--color-warning);
}

.list__form {
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
  max-width: 34rem;
}

.list__acts {
  display: flex;
  flex-wrap: wrap;
  gap: 0.6rem;
}

.list__settings {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.list__note {
  margin-bottom: 0.8rem;
  font-size: 0.92rem;
  color: var(--color-ash);
}

.list__failure {
  font-size: 0.88rem;
  color: var(--color-danger);
}
</style>
