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
import {memberTypeLabel} from "@/utils/memberType"

defineOptions({name: "ManageMembershipDialog"})

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

const store = useStore<TypedStore>()
const isAdmin = computed(() => store.getters.isAdmin)

const memberships = ref<MembershipResponse[]>([])
const deletedMemberships = ref<MembershipResponse[]>([])
const isLoading = ref(false)

// Add-membership pane is collapsed by default; folds out on click.
const addOpen = ref(false)

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

const deleteTarget = ref<MembershipResponse | null>(null)
const deleteConfirmOpen = ref(false)

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
      addOpen.value = false
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
  <base-modal
    v-model="open"
    :title="`Manage memberships${userName ? `: ${userName}` : ''}`"
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
        <div class="d-flex align-center text-subtitle-1 font-weight-bold mb-2">
          <v-icon
            class="mr-2"
            icon="mdi-card-account-details-outline"
            size="20"
          />
          Memberships
        </div>

        <v-empty-state
          v-if="memberships.length === 0"
          class="py-4"
          icon="mdi-card-account-details-outline"
          text="Create a membership period to track this user's association membership history."
          title="No memberships yet"
        />

        <template
          v-for="(m, index) in memberships"
          :key="m.id"
        >
          <div :data-testid="`manage-membership-row-${m.id}`">
            <!-- Clickable summary row — folds out the edit form -->
            <div
              class="mm-row d-flex align-center justify-space-between gap-2"
              :class="{ 'mm-row--open': isEditing(m.id) }"
              role="button"
              tabindex="0"
              @click="toggleInlineEdit(m)"
              @keydown.enter="toggleInlineEdit(m)"
              @keydown.space.prevent="toggleInlineEdit(m)"
            >
              <div
                class="flex-grow-1"
                style="min-width: 0"
              >
                <div class="font-weight-medium text-truncate">
                  {{ m.startDate }} –
                  <span v-if="m.endDate">{{ m.endDate }}</span>
                  <span v-else>active</span>
                </div>
                <div class="text-medium-emphasis text-body-2">
                  <span>{{ memberTypeLabel(m.memberType) }}</span>
                  <v-icon
                    v-if="m.incasso"
                    class="ml-1"
                    color="teal"
                    icon="mdi-bank-transfer"
                    size="16"
                  />
                </div>
              </div>

              <div class="d-flex align-center gap-1 flex-nowrap flex-shrink-0">
                <template v-if="!m.endDate">
                  <v-btn
                    :data-testid="`manage-membership-end-btn-${m.id}`"
                    class="btn-tight"
                    color="orange"
                    size="small"
                    variant="text"
                    @click.stop="onEnd(m)"
                  >
                    End
                  </v-btn>
                </template>
                <template v-else>
                  <v-btn
                    :data-testid="`manage-membership-reopen-btn-${m.id}`"
                    :disabled="hasActive"
                    class="btn-tight"
                    color="green"
                    size="small"
                    variant="text"
                    @click.stop="onReopen(m)"
                  >
                    Resume
                  </v-btn>
                </template>

                <v-btn
                  :data-testid="`manage-membership-delete-btn-${m.id}`"
                  class="btn-tight"
                  color="red"
                  size="small"
                  variant="text"
                  @click.stop="onDelete(m)"
                >
                  Delete
                </v-btn>

                <v-icon
                  class="mm-chevron ml-1"
                  :icon="isEditing(m.id) ? 'mdi-chevron-up' : 'mdi-chevron-down'"
                />
              </div>
            </div>

            <!-- Fold-out edit form (animated) — uses MembershipForm in board mode -->
            <v-expand-transition>
              <div
                v-if="isEditing(m.id) && editModels[m.id]"
                class="pt-1 pb-3"
                data-testid="manage-membership-edit-pane"
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
            </v-expand-transition>

            <v-divider v-if="index < memberships.length - 1" />
          </div>
        </template>
      </div>

      <!-- Add membership (only when no active membership) — collapsed by default -->
      <div
        v-if="!hasActive"
        class="mb-4"
        data-testid="manage-membership-add-pane"
      >
        <v-divider class="mb-2" />
        <div
          class="mm-row d-flex align-center"
          :class="{ 'mm-row--open': addOpen }"
          role="button"
          tabindex="0"
          data-testid="manage-membership-add-toggle"
          @click="addOpen = !addOpen"
          @keydown.enter="addOpen = !addOpen"
          @keydown.space.prevent="addOpen = !addOpen"
        >
          <v-icon
            class="mr-2"
            icon="mdi-plus-circle-outline"
            size="20"
          />
          <span class="text-subtitle-1 font-weight-bold">Add membership</span>
          <v-spacer />
          <v-icon
            class="mm-chevron"
            :icon="addOpen ? 'mdi-chevron-up' : 'mdi-chevron-down'"
          />
        </div>

        <v-expand-transition>
          <div
            v-if="addOpen"
            class="pb-3"
            data-testid="manage-membership-create"
          >
            <membership-form
              v-model="createModel"
              :user-id="userId"
              submit-test-id="manage-membership-create-btn"
              show-submit
              submit-text="Add membership"
              @submitted="onCreateSubmitted"
            />
          </div>
        </v-expand-transition>
      </div>

      <!-- Admin: deleted memberships -->
      <div
        v-if="isAdmin && deletedMemberships.length > 0"
        class="mb-4"
      >
        <div class="d-flex align-center text-subtitle-1 font-weight-bold mb-1">
          <v-icon
            class="mr-2"
            icon="mdi-delete-clock-outline"
            size="20"
          />
          Deleted memberships
        </div>
        <v-divider class="mb-2" />

        <template
          v-for="(m, index) in deletedMemberships"
          :key="m.id"
        >
          <div
            class="mm-row d-flex align-center justify-space-between gap-2"
            :data-testid="`manage-membership-deleted-row-${m.id}`"
          >
            <div
              class="flex-grow-1"
              style="min-width: 0"
            >
              <div class="font-weight-medium text-truncate">
                {{ m.startDate }}
                <span v-if="m.endDate"> – {{ m.endDate }}</span>
              </div>
              <div class="text-medium-emphasis text-body-2">
                <span>{{ memberTypeLabel(m.memberType) }}</span>
                <span class="ml-2 text-error">(deleted)</span>
              </div>
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
          <v-divider v-if="index < deletedMemberships.length - 1" />
        </template>
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

// Interactive summary rows: whole row is the fold-out toggle.
.mm-row {
  cursor: pointer;
  padding: 10px 4px;
  border-radius: 4px;
  transition: background-color 0.15s ease;

  &:hover,
  &:focus-visible {
    background-color: rgba(255, 255, 255, 0.04);
  }

  &:focus-visible {
    outline: none;
  }
}

.mm-chevron {
  opacity: 0.6;
}

// Vuetify spells its gap utilities `ga-1`, so this one is the row's own, the same as in
// the user manager's rows.
.gap-1 {
  gap: 4px;
}

// The house green divider (housestyle.scss) is scoped under
// `.v-application.v-theme--dark`, which the teleported dialog escapes — so the
// modal's dividers fall back to grey. Apply the same accent token here so they
// match the default house divider instead.
:deep(.v-divider) {
  border-color: rgb(var(--v-theme-accent));
  opacity: 1;
}
</style>
