<script lang="ts" setup>
import {computed, ref, watch} from "vue"
import {useStore} from "vuex"
import {Form, type FormContext} from "vee-validate"
import VvField from "@/components/form/fields/VvField.vue"
import MemberTypeSelect from "@/components/form/fields/MemberTypeSelect.vue"
import {
  boardCreateMembership,
  type BoardCreateMembershipRequest,
  deleteMembership,
  endMembership,
  findDeletedMemberships,
  findMemberships,
  MemberType,
  type MembershipResponse,
  reopenMembership,
  restoreMembership,
  updateMembership,
} from "@/services/api"
import {$handleNetworkError} from "@/plugins/handleNetworkError.ts"
import {apply} from "@/plugins/validation.ts"
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

// Create form
const createFormRef = ref<FormContext>()
const createForm = ref<BoardCreateMembershipRequest>({
  startDate: "",
  memberType: MemberType.REGULAR,
  userId: props.userId,
  incasso: false,
})
const isCreating = ref(false)

// Inline edit state per membership id
type InlineEdit = {
  startDate: string
  endDate: string
  memberType: string
  incasso: boolean
  formRef: FormContext | undefined
  isSaving: boolean
}
const inlineEdits = ref<Record<number, InlineEdit | undefined>>({})
const editingIds = ref<Set<number>>(new Set())

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
      inlineEdits.value = {}
      createForm.value = {
        startDate: "",
        memberType: MemberType.REGULAR,
        userId: props.userId,
        incasso: false,
      }
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
    inlineEdits.value[id] = undefined
    // Force reactivity
    editingIds.value = new Set(editingIds.value)
  } else {
    inlineEdits.value[id] = {
      startDate: m.startDate,
      endDate: m.endDate ?? "",
      memberType: m.memberType,
      incasso: m.incasso,
      formRef: undefined,
      isSaving: false,
    }
    editingIds.value = new Set([...editingIds.value, id])
  }
}

function isEditing(id: number): boolean {
  return editingIds.value.has(id)
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

async function onDelete(m: MembershipResponse) {
  try {
    await deleteMembership({path: {id: m.id}, throwOnError: true})
    await loadMemberships()
    emit("changed")
  } catch (error) {
    $handleNetworkError(error)
  }
}

async function onSaveCorrect(m: MembershipResponse) {
  const edit = inlineEdits.value[m.id]
  if (!edit) return

  edit.isSaving = true
  try {
    await updateMembership({
      path: {id: m.id},
      body: {
        startDate: edit.startDate,
        endDate: edit.endDate || undefined,
        memberType: edit.memberType as MemberType,
        incasso: edit.incasso,
        userId: props.userId,
        version: m.version,
      },
      throwOnError: true,
    })
    toggleInlineEdit(m)
    await loadMemberships()
    emit("changed")
  } catch (error) {
    if (edit.formRef && !apply(edit.formRef, error)) $handleNetworkError(error)
    else if (!edit.formRef) $handleNetworkError(error)
  } finally {
    edit.isSaving = false
  }
}

async function onCreate() {
  const validation = await createFormRef.value?.validate()
  if (!validation?.valid) return

  isCreating.value = true
  try {
    await boardCreateMembership({
      path: {userId: props.userId},
      body: createForm.value,
      throwOnError: true,
    })
    createForm.value = {
      startDate: "",
      memberType: MemberType.REGULAR,
      userId: props.userId,
      incasso: false,
    }
    await loadMemberships()
    emit("changed")
  } catch (error) {
    if (createFormRef.value && !apply(createFormRef.value, error)) $handleNetworkError(error)
    else if (!createFormRef.value) $handleNetworkError(error)
  } finally {
    isCreating.value = false
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
</script>

<template>
  <v-dialog
    v-model="open"
    data-testid="manage-membership-dialog"
    max-width="760"
    scrollable
  >
    <v-card>
      <v-card-title class="text-h5">
        Manage memberships{{ userName ? ` — ${userName}` : "" }}
      </v-card-title>

      <v-card-text>
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

            <v-card
              v-for="m in memberships"
              :key="m.id"
              :data-testid="`manage-membership-row-${m.id}`"
              class="mb-2"
              variant="outlined"
            >
              <v-card-text>
                <div class="d-flex align-center justify-space-between flex-wrap gap-2">
                  <div>
                    <span class="font-weight-medium">{{ m.startDate }}</span>
                    <span v-if="m.endDate"> – {{ m.endDate }}</span>
                    <span v-else> – active</span>
                    <span class="ml-2 text-capitalize text-medium-emphasis">{{ m.memberType?.toLowerCase() }}</span>
                    <v-icon
                      v-if="m.incasso"
                      icon="mdi-bank-transfer"
                      size="16"
                      color="teal"
                      class="ml-1"
                    />
                  </div>

                  <div class="d-flex gap-1">
                    <!-- Active membership actions -->
                    <template v-if="!m.endDate">
                      <v-btn
                        :data-testid="`manage-membership-end-btn-${m.id}`"
                        color="orange"
                        size="small"
                        variant="tonal"
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
                        color="green"
                        size="small"
                        variant="tonal"
                        @click="onReopen(m)"
                      >
                        Reopen
                      </v-btn>
                      <v-btn
                        :data-testid="`manage-membership-delete-btn-${m.id}`"
                        color="red"
                        size="small"
                        variant="tonal"
                        @click="onDelete(m)"
                      >
                        Delete
                      </v-btn>
                    </template>

                    <v-btn
                      size="small"
                      variant="tonal"
                      @click="toggleInlineEdit(m)"
                    >
                      {{ isEditing(m.id) ? "Cancel" : "Correct" }}
                    </v-btn>
                  </div>
                </div>

                <!-- Inline correction form -->
                <div
                  v-if="isEditing(m.id) && inlineEdits[m.id]"
                  class="mt-3"
                >
                  <Form
                    :ref="(el) => { if (el && inlineEdits[m.id]) inlineEdits[m.id]!.formRef = el as unknown as FormContext }"
                    as="div"
                  >
                    <v-row dense>
                      <v-col
                        cols="12"
                        sm="6"
                      >
                        <VvField
                          v-model="inlineEdits[m.id]!.startDate"
                          :component-props="{ type: 'date' }"
                          label="Start Date"
                          name="startDate"
                          rules="required"
                        />
                      </v-col>
                      <v-col
                        cols="12"
                        sm="6"
                      >
                        <VvField
                          v-model="inlineEdits[m.id]!.endDate"
                          :component-props="{ type: 'date' }"
                          label="End Date"
                          name="endDate"
                        />
                      </v-col>
                    </v-row>
                    <v-row dense>
                      <v-col
                        cols="12"
                        sm="6"
                      >
                        <VvField
                          v-model="inlineEdits[m.id]!.memberType"
                          :component="MemberTypeSelect"
                          label="Member Type"
                          name="memberType"
                          rules="required"
                        />
                      </v-col>
                      <v-col
                        cols="12"
                        sm="6"
                        class="d-flex align-center"
                      >
                        <v-checkbox
                          v-model="inlineEdits[m.id]!.incasso"
                          label="Incasso"
                          hide-details
                        />
                      </v-col>
                    </v-row>
                  </Form>
                  <div class="mt-2">
                    <v-btn
                      :data-testid="`manage-membership-save-btn-${m.id}`"
                      :loading="inlineEdits[m.id]?.isSaving"
                      color="primary"
                      size="small"
                      @click="onSaveCorrect(m)"
                    >
                      Save
                    </v-btn>
                  </div>
                </div>
              </v-card-text>
            </v-card>
          </div>

          <!-- Add membership form -->
          <div
            class="mb-4"
            data-testid="manage-membership-create"
          >
            <div class="text-subtitle-1 font-weight-bold mb-2">
              Add membership
            </div>

            <Form
              ref="createFormRef"
              as="div"
            >
              <v-row dense>
                <v-col
                  cols="12"
                  sm="6"
                >
                  <VvField
                    v-model="createForm.startDate"
                    :component-props="{ type: 'date', 'data-testid': 'manage-membership-create-start-date' }"
                    label="Start Date"
                    name="startDate"
                    rules="required"
                  />
                </v-col>
                <v-col
                  cols="12"
                  sm="6"
                >
                  <VvField
                    v-model="createForm.memberType"
                    :component="MemberTypeSelect"
                    :component-props="{ 'data-testid': 'manage-membership-create-member-type' }"
                    label="Member Type"
                    name="memberType"
                    rules="required"
                  />
                </v-col>
              </v-row>
              <v-row dense>
                <v-col cols="12">
                  <v-checkbox
                    v-model="createForm.incasso"
                    label="Incasso"
                    hide-details
                    data-testid="manage-membership-create-incasso"
                  />
                </v-col>
              </v-row>
            </Form>

            <v-btn
              :data-testid="`manage-membership-create-btn`"
              :disabled="hasActive"
              :loading="isCreating"
              color="primary"
              class="mt-2"
              @click="onCreate"
            >
              Add membership
            </v-btn>
          </div>

          <!-- Admin: deleted memberships -->
          <div
            v-if="isAdmin && deletedMemberships.length > 0"
            class="mb-4"
          >
            <div class="text-subtitle-1 font-weight-bold mb-2">
              Deleted memberships
            </div>

            <v-card
              v-for="m in deletedMemberships"
              :key="m.id"
              :data-testid="`manage-membership-deleted-row-${m.id}`"
              class="mb-2"
              variant="outlined"
            >
              <v-card-text>
                <div class="d-flex align-center justify-space-between">
                  <div>
                    <span class="font-weight-medium">{{ m.startDate }}</span>
                    <span v-if="m.endDate"> – {{ m.endDate }}</span>
                    <span class="ml-2 text-capitalize text-medium-emphasis">{{ m.memberType?.toLowerCase() }}</span>
                    <span class="ml-2 text-caption text-error">(deleted)</span>
                  </div>
                  <v-btn
                    :data-testid="`manage-membership-restore-btn-${m.id}`"
                    color="primary"
                    size="small"
                    variant="tonal"
                    @click="onRestore(m)"
                  >
                    Restore
                  </v-btn>
                </div>
              </v-card-text>
            </v-card>
          </div>
        </template>
      </v-card-text>

      <v-card-actions>
        <v-spacer />
        <v-btn
          data-testid="manage-membership-close-btn"
          color="secondary"
          @click="close"
        >
          Close
        </v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>
