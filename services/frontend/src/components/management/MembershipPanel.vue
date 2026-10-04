<script lang="ts" setup>
import {computed, ref, watch} from "vue"
import {useStore} from "vuex"
import MembershipForm from "@/components/form/MembershipForm.vue"
import ConfirmDialog from "@/components/island/ConfirmDialog.vue"
import CutButton from "@/components/island/CutButton.vue"
import StateMark from "@/components/island/StateMark.vue"
import ListHead from "@/components/management/ListHead.vue"
import ManagementTable, {type TableColumn} from "@/components/management/ManagementTable.vue"
import MiniButton from "@/components/management/MiniButton.vue"
import {
  IncassoStanding,
  deleteOneMembership,
  endOneMembership,
  listDeletedMembershipsFor,
  listMembershipsFor,
  MemberType,
  type MembershipResponse,
  reopenOneMembership,
  restoreOneMembership,
} from "@/domains/user"
import {$handleNetworkError} from "@/plugins/handleNetworkError.ts"
import type {TypedStore} from "@/plugins/store"
import {memberTypeLabel} from "@/utils/memberType"
import {formatDay} from "@/utils/timestamps"

/* A person's memberships, newest first: start, edit, end, resume, delete and, for an admin,
   restore a deleted one, all in place. */
defineOptions({name: "MembershipPanel"})

const props = defineProps<{userId: number}>()
const emit = defineEmits<{(e: "changed"): void}>()

const store = useStore<TypedStore>()
const isAdmin = computed(() => store.getters.isAdmin)

const COLUMNS: TableColumn[] = [
  {key: "started", label: "Started"},
  {key: "ends", label: "Ends"},
  {key: "type", label: "Type"},
  {key: "incasso", label: "Incasso"},
]

const memberships = ref<MembershipResponse[]>([])
const deletedMemberships = ref<MembershipResponse[]>([])
const isLoading = ref(false)

// Add-membership pane is collapsed by default; folds out on click.
const addOpen = ref(false)

const hasActive = computed(() => memberships.value.some((m) => !m.endDate))

/** The create form's blank model, for MembershipForm in board mode. */
const blankMembership = (): MembershipResponse => ({
  id: 0,
  userId: props.userId,
  startDate: "",
  memberType: MemberType.REGULAR,
  incasso: false,
  incassoStanding: IncassoStanding.NONE,
  pending: false,
  version: 0,
  createdAt: "",
  updatedAt: "",
})

const createModel = ref<MembershipResponse>(blankMembership())

// Inline edit models per membership id — each is a copy of the membership for editing
const editModels = ref<Record<number, MembershipResponse | undefined>>({})
const editingIds = ref<Set<number>>(new Set())

const deleteTarget = ref<MembershipResponse | null>(null)
const deleteConfirmOpen = ref(false)

async function loadMemberships() {
  isLoading.value = true
  try {
    const held = await listMembershipsFor(props.userId)
    memberships.value = held.slice().sort((a, b) => b.startDate.localeCompare(a.startDate))

    if (isAdmin.value) {
      deletedMemberships.value = await listDeletedMembershipsFor(props.userId)
    }
  } finally {
    isLoading.value = false
  }
}

watch(
  () => props.userId,
  async () => {
    editingIds.value = new Set()
    editModels.value = {}
    addOpen.value = false
    createModel.value = blankMembership()
    await loadMemberships()
  },
  {immediate: true},
)

function toggleInlineEdit(m: MembershipResponse) {
  const id = m.id
  if (editingIds.value.has(id)) {
    editingIds.value.delete(id)
    editModels.value[id] = undefined
    // Force reactivity
    editingIds.value = new Set(editingIds.value)
  } else {
    // Make a shallow copy so edits don't affect the list until saved
    editModels.value[id] = {...m}
    editingIds.value = new Set([...editingIds.value, id])
  }
}

function isEditing(id: number): boolean {
  return editingIds.value.has(id)
}

async function onCreateSubmitted(ok: boolean) {
  if (!ok) return
  createModel.value = blankMembership()
  await loadMemberships()
  emit("changed")
}

async function onEditSubmitted(m: MembershipResponse, ok: boolean) {
  if (!ok) return
  toggleInlineEdit(m)
  await loadMemberships()
  emit("changed")
}

async function onEnd(m: MembershipResponse) {
  try {
    await endOneMembership(m.id)
    await loadMemberships()
    emit("changed")
  } catch (error) {
    $handleNetworkError(error)
  }
}

async function onReopen(m: MembershipResponse) {
  try {
    await reopenOneMembership(m.id)
    await loadMemberships()
    emit("changed")
  } catch (error) {
    $handleNetworkError(error)
  }
}

// onDelete opens a confirmation dialog instead of deleting immediately
function onDelete(m: MembershipResponse) {
  deleteTarget.value = m
  deleteConfirmOpen.value = true
}

async function onDeleteConfirmed() {
  if (!deleteTarget.value) return
  const m = deleteTarget.value
  deleteTarget.value = null
  deleteConfirmOpen.value = false
  try {
    await deleteOneMembership(m.id)
    await loadMemberships()
    emit("changed")
  } catch (error) {
    $handleNetworkError(error)
  }
}

async function onRestore(m: MembershipResponse) {
  try {
    await restoreOneMembership(m.id)
    await loadMemberships()
    emit("changed")
  } catch (error) {
    $handleNetworkError(error)
  }
}

defineExpose({
  onEnd,
  onReopen,
  onDelete,
  onDeleteConfirmed,
  onRestore,
  hasActive,
  memberships,
  deleteTarget,
  deleteConfirmOpen,
  addOpen,
  // Exposed for tests
  createModel,
  editModels,
  editingIds,
  toggleInlineEdit,
  onCreateSubmitted,
  onEditSubmitted,
})
</script>

<template>
  <section
    class="membership-panel"
    data-testid="membership-panel"
  >
    <template v-if="!isLoading">
      <list-head title="Memberships" />
      <p
        v-if="memberships.length === 0"
        class="membership-panel__note"
        data-testid="manage-membership-empty"
      >
        No memberships yet. Add one to start this person's membership history.
      </p>
      <management-table
        v-else
        :columns="COLUMNS"
        :row-key="(m) => m.id"
        :row-testid="(m) => `manage-membership-row-${m.id}`"
        search-label="Search memberships"
        :search-text="(m) => `${memberTypeLabel(m.memberType)} ${formatDay(m.startDate)}`"
        :rows="memberships"
      >
        <template #started="{row}">
          <span class="membership-panel__date">{{ formatDay(row.startDate) }}</span>
        </template>
        <template #ends="{row}">
          <state-mark
            v-if="!row.endDate"
            kind="in-step"
          >
            Active
          </state-mark>
          <template v-else>
            {{ formatDay(row.endDate) }}
          </template>
        </template>
        <template #type="{row}">
          {{ memberTypeLabel(row.memberType) }}
        </template>
        <template #incasso="{row}">
          <span :class="{'mg-quiet': !row.incasso}">{{ row.incasso ? "On" : "Off" }}</span>
        </template>
        <template #acts="{row}">
          <mini-button
            :testid="`manage-membership-edit-btn-${row.id}`"
            @click="toggleInlineEdit(row)"
          >
            {{ isEditing(row.id) ? "Close" : "Edit" }}
          </mini-button>
          <mini-button
            v-if="!row.endDate"
            :testid="`manage-membership-end-btn-${row.id}`"
            @click="onEnd(row)"
          >
            End today
          </mini-button>
          <mini-button
            v-else
            :disabled="hasActive"
            :testid="`manage-membership-reopen-btn-${row.id}`"
            @click="onReopen(row)"
          >
            Resume
          </mini-button>
          <mini-button
            :testid="`manage-membership-delete-btn-${row.id}`"
            tone="danger"
            @click="onDelete(row)"
          >
            Delete
          </mini-button>
        </template>
      </management-table>

      <template
        v-for="m in memberships"
        :key="m.id"
      >
        <div
          v-if="isEditing(m.id) && editModels[m.id]"
          class="membership-panel__form"
          data-testid="manage-membership-edit-pane"
        >
          <list-head :title="`Edit the membership started ${formatDay(m.startDate)}`" />
          <membership-form
            v-model="editModels[m.id]!"
            show-submit
            :submit-test-id="`manage-membership-save-btn-${m.id}`"
            submit-text="Save"
            :user-id="userId"
            @submitted="onEditSubmitted(m, $event)"
          />
        </div>
      </template>

      <div
        v-if="!hasActive"
        data-testid="manage-membership-add-pane"
      >
        <list-head title="Add a membership">
          <cut-button
            small
            testid="manage-membership-add-toggle"
            @click="addOpen = !addOpen"
          >
            {{ addOpen ? "Close" : "Add a membership" }}
          </cut-button>
        </list-head>
        <div
          v-if="addOpen"
          class="membership-panel__form"
          data-testid="manage-membership-create"
        >
          <membership-form
            v-model="createModel"
            show-submit
            submit-test-id="manage-membership-create-btn"
            submit-text="Add membership"
            :user-id="userId"
            @submitted="onCreateSubmitted"
          />
        </div>
      </div>

      <template v-if="isAdmin && deletedMemberships.length > 0">
        <list-head title="Deleted memberships" />
        <management-table
          :columns="COLUMNS.slice(0, 3)"
          :row-key="(m) => m.id"
          :row-testid="(m) => `manage-membership-deleted-row-${m.id}`"
          search-label="Search memberships"
          :search-text="(m) => `${memberTypeLabel(m.memberType)} ${formatDay(m.startDate)}`"
          :rows="deletedMemberships"
        >
          <template #started="{row}">
            {{ formatDay(row.startDate) }}
          </template>
          <template #ends="{row}">
            <span :class="{'mg-quiet': !row.endDate}">{{ row.endDate ? formatDay(row.endDate) : "·" }}</span>
          </template>
          <template #type="{row}">
            {{ memberTypeLabel(row.memberType) }}
          </template>
          <template #acts="{row}">
            <mini-button
              :testid="`manage-membership-restore-btn-${row.id}`"
              @click="onRestore(row)"
            >
              Restore
            </mini-button>
          </template>
        </management-table>
      </template>
    </template>

    <confirm-dialog
      confirm-label="Delete"
      :open="deleteConfirmOpen"
      :question="deleteTarget ? `The membership from ${formatDay(deleteTarget.startDate)} to ${deleteTarget.endDate ? formatDay(deleteTarget.endDate) : 'today'} goes. An admin can restore it.` : ''"
      testid="manage-membership-delete-confirmation"
      title="Delete this membership?"
      working-label="Deleting"
      @confirm="onDeleteConfirmed"
      @update:open="deleteConfirmOpen = $event"
    />
  </section>
</template>

<style scoped>
.membership-panel__note {
  font-size: 0.9rem;
  color: var(--color-ash);
}

.membership-panel__date {
  font-size: 0.9rem;
  color: var(--color-chalk);
}

.membership-panel__form {
  max-width: 40rem;
}
</style>
