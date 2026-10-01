<script lang="ts" setup>
/* One Brevo list: the cohort that fills it, its people in step and its drift, each drifting person
   with why and what can be done, the resolutions so far, and the list itself to rename, move,
   archive, enforce or delete. */
import {computed, onMounted, ref, watch} from "vue"
import {useRoute, useRouter} from "vue-router"
import CheckBox from "@/components/island/CheckBox.vue"
import FactList from "@/components/island/FactList.vue"
import FormField from "@/components/island/FormField.vue"
import ModalDialog from "@/components/island/ModalDialog.vue"
import SearchBox from "@/components/island/SearchBox.vue"
import SearchPicker from "@/components/island/SearchPicker.vue"
import SelectionBar from "@/components/island/SelectionBar.vue"
import StateMark from "@/components/island/StateMark.vue"
import TextInput from "@/components/island/TextInput.vue"
import {
  ARCHIVE_FOLDER,
  type Cohort,
  type CohortMember,
  type DriftAction,
  type ListedTarget,
  type MissingTarget,
  RESOLUTION_WORDS,
  TargetSystem,
  adoptWord,
  archiveTarget,
  cohortTypeLabel,
  deleteTarget,
  driftRowsOf,
  fetchCohort,
  fetchTargetFolders,
  inStepOn,
  linkExistingTargetForCohort,
  memberName,
  moveTargetToFolder,
  readListedTarget,
  readTargetOverview,
  renameTarget,
  runBars,
  setTargetEnforced,
  triggerReconcile,
  useDriftResolution,
  whyOf,
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
const search = ref("")
const acting = ref(false)

const mapping = computed(() => cohort.value?.mappings.find((one) => one.system === SYSTEM) ?? null)
const mappings = computed(() => (mapping.value ? [mapping.value] : []))
const cohortId = computed(() => cohort.value?.id ?? null)
const members = computed(() => cohort.value?.members ?? [])
const drift = computed(() => driftRowsOf(members.value, SYSTEM, search.value))
const allDrift = computed(() => driftRowsOf(members.value, SYSTEM))
const resolutions = computed(() => cohort.value?.resolutions.filter((one) => one.system === SYSTEM) ?? [])
const adopt = computed(() => adoptWord(cohort.value?.type))
const said = (message: string) => store.commit("setStatusSnackbarMessage", message)

const load = async () => {
  list.value = await readListedTarget(SYSTEM, externalId.value)
  cohort.value = list.value?.cohortId != null ? await fetchCohort(list.value.cohortId) : null
  name.value = list.value?.label ?? ""
  folder.value = list.value?.folderLabel ?? null
  loaded.value = true
  if (list.value && list.value.cohortId == null) unlinkedCohorts.value = (await readTargetOverview(SYSTEM))?.missing ?? []
}

const resolution = useDriftResolution(cohortId, mappings, load)
const {selection, plan, planCount, working} = resolution

const facts = computed(() => {
  const latest = mapping.value?.runs.at(0)
  return [
    {label: "Follows", value: cohort.value?.label ?? "", sub: cohort.value ? cohortTypeLabel(cohort.value.type) : ""},
    {
      label: "People",
      value: `${inStepOn(members.value, SYSTEM)} in step`,
      sub: `${allDrift.value.filter((one) => one.sync === "ONLY_HERE").length} missing · ${allDrift.value.filter((one) => one.sync === "ONLY_EXTERNAL").length} extra`,
      testid: "brevo-list-people",
    },
    {
      label: "Reconciled",
      value: latest ? formatDateNoSeconds(latest.startedAt) : "Never",
      sub: mapping.value?.enforced ? "Enforced: extra people are removed at every reconcile" : "Nightly, and after every change",
    },
  ]
})
const bars = computed(() => runBars(mapping.value?.runs ?? []))

const ACTION_WORDS: Record<DriftAction, string> = {push: "Push", remove: "Remove", link: "Link to an account", adopt: "Take in"}
const actionWord = (action: DriftAction) => (action === "adopt" ? adopt.value ?? ACTION_WORDS.adopt : ACTION_WORDS[action])
const rowActions = (row: CohortMember): DriftAction[] =>
  (["push", "adopt", "link", "remove"] as DriftAction[]).filter((action) => (action !== "adopt" || adopt.value !== null) && resolution.canResolve(action, row))
const bulkActions = computed(() =>
  (["push", "adopt", "link", "remove"] as DriftAction[])
    .filter((action) => action !== "adopt" || adopt.value !== null)
    .map((action) => ({action, count: resolution.selectedFor(action, drift.value)}))
    .filter((one) => one.count > 0))
const ticked = (row: CohortMember) => selection.value.has(row.targetMemberId)
const prepareSelected = (action: DriftAction) => resolution.prepare(action, drift.value.filter(ticked))
const planTitle = computed(() => (plan.value ? `${actionWord(plan.value.action)}: ${planCount.value} ${planCount.value === 1 ? "person" : "people"}` : ""))
const planPeople = computed(() => plan.value?.groups.flatMap((group) => group.people) ?? [])
const proposalFor = (row: CohortMember) => plan.value?.proposals.find((one) => one.externalUserId === row.externalUserId) ?? null

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
const folderOptions = computed(() => folders.value.map((one) => ({key: one, label: one})))
const changed = computed(() => list.value != null && (name.value.trim() !== list.value.label || folder.value !== (list.value.folderLabel ?? null)))

const save = async () => {
  const current = list.value
  if (!current || !changed.value || acting.value || name.value.trim() === "") return
  acting.value = true
  try {
    if (name.value.trim() !== current.label) {
      const renamed = await renameTarget(SYSTEM, current.externalId, name.value.trim())
      if (!renamed.ok) return said(renamed.reason)
    }
    if (folder.value && folder.value !== current.folderLabel) await moveTargetToFolder(SYSTEM, current.externalId, folder.value)
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
  <div
    class="list"
    data-testid="brevo-list"
  >
    <router-link
      class="list__back"
      to="/management/platforms/brevo"
    >
      Brevo
    </router-link>

    <p
      v-if="loaded && !list"
      class="list__note"
      data-testid="brevo-list-missing"
    >
      Brevo has no such list. It may have been deleted there.
    </p>

    <template v-if="list">
      <header class="list__head">
        <div>
          <p class="list__eyebrow">
            Brevo list · {{ list.folderLabel ? `${list.folderLabel} folder` : "No folder" }}
          </p>
          <h1 class="list__title">
            {{ list.label }}
          </h1>
          <p class="list__note">
            <template v-if="cohort">
              Mail sent to this list reaches {{ cohort.label }}: {{ cohortTypeLabel(cohort.type).toLowerCase() }}.
            </template>
            <template v-else>
              This list follows nothing, so the site leaves its people alone.
            </template>
          </p>
        </div>
        <button
          v-if="mapping"
          class="list__action"
          data-testid="brevo-list-reconcile"
          :disabled="acting"
          type="button"
          @click="reconcile"
        >
          Reconcile now
        </button>
      </header>

      <template v-if="cohort && mapping">
        <fact-list :facts="facts" />
        <p
          v-if="bars.length"
          class="list__runs"
          :aria-label="`Drift over the last ${bars.length} runs`"
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

        <h2 class="list__part">
          Drift
        </h2>
        <search-box
          v-model="search"
          label="Search for a user"
          testid="brevo-list-search"
        />
        <p
          v-if="resolution.message.value"
          class="list__note"
          data-testid="brevo-list-message"
          role="status"
        >
          {{ resolution.message.value }}
        </p>
        <p
          v-if="resolution.error.value"
          class="list__failure"
          data-testid="brevo-list-error"
          role="alert"
        >
          {{ resolution.error.value }}
        </p>
        <p
          v-if="drift.length === 0"
          class="list__note"
          data-testid="brevo-list-in-step"
        >
          {{ search ? "Nobody drifting matches." : "Everybody is where they should be." }}
        </p>
        <ul class="list__rows">
          <li
            v-for="row in drift"
            :key="row.targetMemberId"
            class="list__row"
            :data-testid="`brevo-list-row-${row.targetMemberId}`"
          >
            <check-box
              :label="`Select ${memberName(row)}`"
              :model-value="ticked(row)"
              :testid="`brevo-list-select-${row.targetMemberId}`"
              @update:model-value="resolution.toggle(row)"
            />
            <span class="list__who">
              <router-link
                v-if="row.userId != null"
                :to="`/management/users/${row.userId}`"
              >{{ memberName(row) }}</router-link>
              <span v-else>Unknown contact</span>
              <span class="list__sub">{{ row.userEmail ?? row.externalLabel ?? "" }}</span>
            </span>
            <state-mark :kind="row.sync === 'ONLY_HERE' ? 'missing' : 'extra'" />
            <span class="list__sub list__why">{{ whyOf(row, cohort.label) }}</span>
            <span class="list__row-acts">
              <button
                v-for="action in rowActions(row)"
                :key="action"
                class="list__mini"
                :class="{'list__mini--danger': action === 'remove'}"
                :data-testid="`brevo-list-${action}-${row.targetMemberId}`"
                :disabled="working"
                type="button"
                @click="resolution.prepare(action, [row])"
              >
                {{ actionWord(action) }}
              </button>
            </span>
          </li>
        </ul>
        <selection-bar
          :count="selection.size"
          testid="brevo-list-selection"
          @clear="resolution.clear"
        >
          <button
            v-for="one in bulkActions"
            :key="one.action"
            class="list__action"
            :data-testid="`brevo-list-bulk-${one.action}`"
            :disabled="working"
            type="button"
            @click="prepareSelected(one.action)"
          >
            {{ actionWord(one.action) }}: {{ one.count }}
          </button>
        </selection-bar>

        <h2 class="list__part">
          Resolved
        </h2>
        <table
          v-if="resolutions.length"
          class="list__log"
          data-testid="brevo-list-resolved"
        >
          <thead>
            <tr>
              <th>When</th>
              <th>Who</th>
              <th>What</th>
              <th>Who it concerned</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="(one, index) in resolutions"
              :key="index"
            >
              <td>{{ formatDateNoSeconds(one.resolvedAt) }}</td>
              <td>{{ one.resolvedByName ?? "The site" }}</td>
              <td>{{ RESOLUTION_WORDS[one.action] }}</td>
              <td>{{ one.personName ?? "" }}</td>
            </tr>
          </tbody>
        </table>
        <p
          v-else
          class="list__note"
        >
          Nothing resolved yet.
        </p>
      </template>

      <section
        v-if="!list.cohortId"
        class="list__link"
        data-testid="brevo-list-link"
      >
        <h2 class="list__part">
          Follows nothing
        </h2>
        <p class="list__note">
          Link the list to a cohort whose list is missing, and the site fills it from then on.
        </p>
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
        <button
          class="list__action"
          data-testid="brevo-list-link-confirm"
          :disabled="acting || linkTo == null"
          type="button"
          @click="link"
        >
          Link
        </button>
      </section>

      <h2 class="list__part">
        This list
      </h2>
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
        <button
          class="list__action list__action--main"
          data-testid="brevo-list-save"
          :disabled="acting || !changed"
          type="submit"
        >
          Save
        </button>
      </form>

      <div class="list__settings">
        <button
          v-if="mapping && isAdmin"
          class="list__setting"
          data-testid="brevo-list-enforce"
          :disabled="acting"
          type="button"
          @click="enforce"
        >
          <span>
            <span class="list__setting-title">Enforce</span>
            <span class="list__sub">Remove extra people automatically at every reconcile. {{ mapping.enforced ? "On." : "Off." }}</span>
          </span>
          <span class="list__sub">{{ mapping.enforced ? "Turn off" : "Turn on" }}</span>
        </button>
        <button
          v-if="list.folderLabel !== ARCHIVE_FOLDER"
          class="list__setting"
          data-testid="brevo-list-archive"
          :disabled="acting"
          type="button"
          @click="archive"
        >
          <span>
            <span class="list__setting-title">Archive</span>
            <span class="list__sub">Moves the list to the Archive folder in Brevo. It keeps its people and can be moved back.</span>
          </span>
        </button>
        <button
          v-if="isAdmin"
          class="list__setting"
          data-testid="brevo-list-delete"
          :disabled="acting || list.cohortId != null"
          type="button"
          @click="deleting = true; typedName = ''; deleteFailure = null"
        >
          <span>
            <span class="list__setting-title">Delete</span>
            <span class="list__sub">
              {{ list.cohortId != null ? "Only a list that follows nothing can be deleted." : "Deletes the list and its contacts from Brevo for good." }}
            </span>
          </span>
        </button>
      </div>
    </template>

    <modal-dialog
      :open="plan !== null"
      testid="brevo-list-plan"
      :title="planTitle"
      @update:open="(open: boolean) => { if (!open) resolution.cancel() }"
    >
      <ul class="list__plan">
        <li
          v-for="row in planPeople"
          :key="row.targetMemberId"
        >
          {{ memberName(row) }}
          <template v-if="plan?.action === 'link'">
            : {{ proposalFor(row)?.userFullName ?? "no account has this address, so it stays as it is" }}
          </template>
        </li>
      </ul>
      <template #footer>
        <button
          class="list__action list__action--main"
          data-testid="brevo-list-plan-confirm"
          :disabled="working"
          type="button"
          @click="resolution.confirm"
        >
          {{ plan ? actionWord(plan.action) : "" }}
        </button>
      </template>
    </modal-dialog>

    <modal-dialog
      :open="deleting"
      testid="brevo-list-delete-dialog"
      title="Delete the list"
      @update:open="deleting = $event"
    >
      <form
        class="list__delete"
        @submit.prevent="confirmDelete"
      >
        <p class="list__note">
          Brevo cannot undo this. Type the list's name, {{ list?.label }}, to delete it and its contacts for good.
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
        <button
          class="list__action list__action--danger"
          data-testid="brevo-list-delete-confirm"
          :disabled="acting || typedName !== list?.label"
          type="submit"
        >
          Delete
        </button>
      </form>
    </modal-dialog>
  </div>
</template>

<style scoped>
.list {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  max-width: 76rem;
  padding: 2rem 2.4rem 3rem;
}

.list__back {
  align-self: flex-start;
  font-size: 0.84rem;
  color: var(--color-brand);
}

.list__head {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: 1rem;
}

.list__eyebrow {
  margin: 0;
  font-size: 11px;
  letter-spacing: 0.3em;
  text-transform: uppercase;
  color: var(--color-eyebrow, var(--color-ash));
}

.list__title {
  margin: 0;
  font-family: var(--font-display);
  font-size: clamp(1.4rem, 3vw, 2rem);
}

.list__part {
  margin: 1rem 0 0;
  font-size: 11px;
  font-weight: 400;
  letter-spacing: 0.3em;
  text-transform: uppercase;
  color: var(--color-ash);
}

.list__note {
  margin: 0;
  max-width: 48rem;
  color: var(--color-ash);
}

.list__failure {
  margin: 0;
  color: var(--color-error, #e5484d);
}

.list__runs {
  display: flex;
  align-items: flex-end;
  gap: 3px;
  height: 22px;
  margin: 0;
}

.list__bar {
  width: 6px;
  background-color: var(--color-hairline);
}

.list__bar--drift {
  background-color: var(--color-brand);
}

.list__action,
.list__mini {
  padding: 0.45rem 0.9rem;
  border: 1px solid var(--color-hairline);
  background: none;
  font: inherit;
  font-size: 0.86rem;
  color: var(--color-chalk);
  cursor: pointer;
}

.list__mini {
  padding: 0.25rem 0.6rem;
  font-size: 0.8rem;
}

/* Red text falls below contrast on the page; the border carries the warning instead. */
.list__mini--danger {
  border-color: var(--color-error, #e5484d);
}

.list__action--danger {
  align-self: flex-start;
  border-color: var(--color-error, #e5484d);
}

.list__delete {
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
}

.list__action--main {
  align-self: flex-start;
  border-color: var(--color-brand);
  color: var(--color-brand);
}

.list__action:disabled,
.list__mini:disabled,
.list__setting:disabled {
  opacity: 0.45;
  cursor: default;
}

.list__rows {
  display: flex;
  flex-direction: column;
  margin: 0;
  padding: 0;
  list-style: none;
  border-top: 1px solid var(--color-hairline);
}

.list__row {
  display: grid;
  grid-template-columns: 2rem minmax(0, 1fr) 6rem minmax(0, 1.4fr) auto;
  align-items: center;
  gap: 1rem;
  padding: 0.65rem 0.4rem;
  border-bottom: 1px solid var(--color-hairline);
}

.list__who {
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
  min-width: 0;
}

.list__who a {
  color: var(--color-chalk);
  font-weight: 600;
}

.list__sub {
  display: block;
  font-size: 0.84rem;
  color: var(--color-ash);
}

.list__why {
  white-space: normal;
}

.list__row-acts {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 0.4rem;
}

.list__log {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.86rem;
}

.list__log th,
.list__log td {
  padding: 0.5rem 0.4rem;
  border-bottom: 1px solid var(--color-hairline);
  text-align: left;
}

.list__log th {
  font-weight: 400;
  color: var(--color-ash);
}

.list__form,
.list__link {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 1rem;
  align-items: end;
}

.list__link .list__part,
.list__link .list__note {
  grid-column: 1 / -1;
}

.list__settings {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.list__setting {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  padding: 0.9rem 1.1rem;
  border: 0;
  background-color: var(--band-ground);
  font: inherit;
  color: var(--color-chalk);
  text-align: left;
  cursor: pointer;
}

.list__setting-title {
  font-weight: 600;
}

.list__plan {
  margin: 0;
  padding-left: 1.2rem;
}

@media (max-width: 839px) {
  .list {
    padding: 1.2rem 1.1rem 2rem;
  }

  .list__row {
    grid-template-columns: 2rem minmax(0, 1fr) auto;
  }

  .list__why,
  .list__row-acts {
    grid-column: 2 / -1;
  }

  .list__form,
  .list__link {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
