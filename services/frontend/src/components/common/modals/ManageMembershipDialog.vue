<script lang="ts" setup>
import {computed, ref, watch} from "vue"
import {useStore} from "vuex"
import BaseModal from "./BaseModal.vue"
import ConfirmationDialog from "./ConfirmationDialog.vue"
import MembershipForm from "@/components/form/MembershipForm.vue"
import {
  deleteMembership,
  endMembership,
  findDeletedMemberships,
  findMemberships,
  MemberType,
  type MembershipResponse,
  reopenMembership,
  restoreMembership,
} from "@/services/api"
import {$handleNetworkError} from "@/plugins/handleNetworkError.ts"
import type {TypedStore} from "@/plugins/store"

defineOptions({name: "ManageMembershipDialog"})

// ── Props / Emits ────────────────────────────────────────────────────────────

interface Props {
  modelValue: boolean
  userId: number
  userName?: string
}

const props = defineProps<Props>()
const emit = defineEmits<{
  (e: "update:modelValue", value: boolean): void
  (e: "changed"): void
}>()

const open = computed({
  get: () => props.modelValue,
  set: (val: boolean) => emit("update:modelValue", val),
})

// ── Store ────────────────────────────────────────────────────────────────────

const store = useStore<TypedStore>()
const isAdmin = computed(() => store.getters.isAdmin)

// ── State ────────────────────────────────────────────────────────────────────

const memberships = ref<MembershipResponse[]>([])
const deletedMemberships = ref<MembershipResponse[]>([])
const isLoading = ref(false)

const hasActive = computed(() => memberships.value.some((m) => !m.endDate))

// Create form — blank MembershipResponse model for MembershipForm in board mode
const createModel = ref<MembershipResponse>({
  id: 0,
  userId: props.userId,
  startDate: "",
  memberType: MemberType.REGULAR,
  incasso: false,
  version: 0,
  createdAt: "",
  updatedAt: "",
} as MembershipResponse)

// Inline edit models per membership id — each is a copy of the membership for editing
const editModels = ref<Record<number, MembershipResponse | undefined>>({})
const editingIds = ref<Set<number>>(new Set())

// Delete confirmation state
const deleteTarget = ref<MembershipResponse | null>(null)
const deleteConfirmOpen = ref(false)

// ── Data loading ─────────────────────────────────────────────────────────────

async function loadMemberships() {
  isLoading.value = true
  try {
    const resp = await findMemberships({query: {userId: props.userId}})
    const sorted = (resp.data ?? []).slice().sort((a, b) => b.startDate.localeCompare(a.startDate))
    memberships.value = sorted

    if (isAdmin.value) {
      const delResp = await findDeletedMemberships({path: {userId: props.userId}})
      deletedMemberships.value = delResp.data ?? []
    }
  } finally {
    isLoading.value = false
  }
}

watch(
  () => props.modelValue,
  async (val) => {
    if (val) {
      editingIds.value = new Set()
      editModels.value = {}
      createModel.value = {
        id: 0,
        userId: props.userId,
        startDate: "",
        memberType: MemberType.REGULAR,
        incasso: false,
        version: 0,
        createdAt: "",
        updatedAt: "",
      } as MembershipResponse
      await loadMemberships()
    }
  },
  {immediate: true},
)

// ── Inline edit helpers ───────────────────────────────────────────────────────

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

// ── MembershipForm event handlers ─────────────────────────────────────────────

async function onCreateSubmitted(ok: boolean) {
  if (!ok) return
  createModel.value = {
    id: 0,
    userId: props.userId,
    startDate: "",
    memberType: MemberType.REGULAR,
    incasso: false,
    version: 0,
    createdAt: "",
    updatedAt: "",
  } as MembershipResponse
  await loadMemberships()
  emit("changed")
}

async function onEditSubmitted(m: MembershipResponse, ok: boolean) {
  if (!ok) return
  toggleInlineEdit(m)
  await loadMemberships()
  emit("changed")
}

// ── Actions ───────────────────────────────────────────────────────────────────

async function onEnd(m: MembershipResponse) {
  try {
    await endMembership({path: {id: m.id}, throwOnError: true})
    await loadMemberships()
    emit("changed")
  } catch (error) {
    $handleNetworkError(error)
  }
}

async function onReopen(m: MembershipResponse) {
  try {
    await reopenMembership({path: {id: m.id}, throwOnError: true})
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
    await deleteMembership({path: {id: m.id}, throwOnError: true})
    await loadMemberships()
    emit("changed")
  } catch (error) {
    $handleNetworkError(error)
  }
}

async function onRestore(m: MembershipResponse) {
  try {
    await restoreMembership({path: {id: m.id}, throwOnError: true})
    await loadMemberships()
    emit("changed")
  } catch (error) {
    $handleNetworkError(error)
  }
}

function close() {
  open.value = false
}

defineExpose({
  onEnd,
  onReopen,
  onDelete,
  onDeleteConfirmed,
  onRestore,
  close,
  hasActive,
  memberships,
  deleteTarget,
  deleteConfirmOpen,
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
  <base-modal
    v-model="open"
    :title="`Manage memberships${userName ? ` — ${userName}` : ''}`"
    testid="manage-membership-dialog"
    max-width="1000"
    fullscreen-mobile
    show-cancel
    cancel-label="Close"
    cancel-testid="manage-membership-close-btn"
  >
    <div
      v-if="isLoading"
      class="text-center py-4"
    >
      <v-progress-circular indeterminate />
    </div>

    <template v-else>
      <!-- Existing memberships -->
      <div class="mb-4">
        <div class="text-subtitle-1 font-weight-bold mb-2">
          Memberships
        </div>

        <div
          v-if="memberships.length === 0"
          class="text-medium-emphasis"
        >
          No memberships found.
        </div>

        <v-list v-else>
          <template
            v-for="(m, index) in memberships"
            :key="m.id"
          >
            <v-list-item :data-testid="`manage-membership-row-${m.id}`">
              <div class="d-flex align-center justify-space-between gap-2">
                <!-- Left: date range + type as title/subtitle -->
                <div class="flex-grow-1">
                  <v-list-item-title class="font-weight-medium">
                    {{ m.startDate }} –
                    <span v-if="m.endDate">{{ m.endDate }}</span>
                    <span v-else>active</span>
                  </v-list-item-title>
                  <v-list-item-subtitle>
                    <span class="text-capitalize">{{ m.memberType?.toLowerCase() }}</span>
                    <v-icon
                      v-if="m.incasso"
                      class="ml-1"
                      color="teal"
                      icon="mdi-bank-transfer"
                      size="16"
                    />
                  </v-list-item-subtitle>
                </div>

                <!-- Right: action buttons -->
                <div class="d-flex gap-1 flex-nowrap">
                  <!-- Active membership actions -->
                  <template v-if="!m.endDate">
                    <v-btn
                      :data-testid="`manage-membership-end-btn-${m.id}`"
                      class="btn-tight"
                      color="orange"
                      size="small"
                      variant="text"
                      @click="onEnd(m)"
                    >
                      End
                    </v-btn>
                  </template>

                  <!-- Ended membership actions -->
                  <template v-else>
                    <v-btn
                      :data-testid="`manage-membership-reopen-btn-${m.id}`"
                      :disabled="hasActive"
                      class="btn-tight"
                      color="green"
                      size="small"
                      variant="text"
                      @click="onReopen(m)"
                    >
                      Resume
                    </v-btn>
                  </template>

                  <!-- Delete is available for every membership (active or ended) -->
                  <v-btn
                    :data-testid="`manage-membership-delete-btn-${m.id}`"
                    class="btn-tight"
                    color="red"
                    size="small"
                    variant="text"
                    @click="onDelete(m)"
                  >
                    Delete
                  </v-btn>

                  <v-btn
                    class="btn-tight"
                    size="small"
                    variant="text"
                    @click="toggleInlineEdit(m)"
                  >
                    {{ isEditing(m.id) ? "Cancel" : "Edit" }}
                  </v-btn>
                </div>
              </div>

              <!-- Inline edit form (when editing that row) — uses MembershipForm in board mode -->
              <div
                v-if="isEditing(m.id) && editModels[m.id]"
                class="mt-3 w-100"
              >
                <membership-form
                  v-model="editModels[m.id]!"
                  :user-id="userId"
                  :submit-test-id="`manage-membership-save-btn-${m.id}`"
                  show-submit
                  submit-text="Save"
                  @submitted="onEditSubmitted(m, $event)"
                />
              </div>
            </v-list-item>

            <v-divider v-if="index < memberships.length - 1" />
          </template>
        </v-list>
      </div>

      <!-- Add membership form (only when no active membership) -->
      <div
        v-if="!hasActive"
        class="mb-4"
        data-testid="manage-membership-create"
      >
        <v-divider
          v-if="memberships.length"
          class="mb-4"
        />

        <div class="text-subtitle-1 font-weight-bold mb-2">
          Add membership
        </div>

        <membership-form
          v-model="createModel"
          :user-id="userId"
          submit-test-id="manage-membership-create-btn"
          show-submit
          submit-text="Add membership"
          @submitted="onCreateSubmitted"
        />
      </div>

      <!-- Admin: deleted memberships -->
      <div
        v-if="isAdmin && deletedMemberships.length > 0"
        class="mb-4"
      >
        <div class="text-subtitle-1 font-weight-bold mb-2">
          Deleted memberships
        </div>

        <v-list>
          <template
            v-for="(m, index) in deletedMemberships"
            :key="m.id"
          >
            <v-list-item :data-testid="`manage-membership-deleted-row-${m.id}`">
              <div class="d-flex align-center justify-space-between flex-wrap gap-2">
                <div class="flex-grow-1">
                  <v-list-item-title class="font-weight-medium">
                    {{ m.startDate }}
                    <span v-if="m.endDate"> – {{ m.endDate }}</span>
                  </v-list-item-title>
                  <v-list-item-subtitle>
                    <span class="text-capitalize">{{ m.memberType?.toLowerCase() }}</span>
                    <span class="ml-2 text-error">(deleted)</span>
                  </v-list-item-subtitle>
                </div>
                <v-btn
                  :data-testid="`manage-membership-restore-btn-${m.id}`"
                  class="btn-tight"
                  color="primary"
                  size="small"
                  variant="text"
                  @click="onRestore(m)"
                >
                  Restore
                </v-btn>
              </div>
            </v-list-item>
            <v-divider v-if="index < deletedMemberships.length - 1" />
          </template>
        </v-list>
      </div>
    </template>
  </base-modal>

  <!-- Delete membership confirmation -->
  <confirmation-dialog
    v-model="deleteConfirmOpen"
    :message="deleteTarget ? `Delete membership from ${deleteTarget.startDate} to ${deleteTarget.endDate ?? 'active'}?` : ''"
    confirm-label="Delete"
    testid="manage-membership-delete-confirmation"
    title="Delete membership"
    @confirm="onDeleteConfirmed"
  />
</template>

<style lang="scss" scoped>
.btn-tight {
  padding-inline: 6px !important;
  min-width: auto !important;
}
</style>
