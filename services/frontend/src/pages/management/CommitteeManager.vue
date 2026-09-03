<script lang="ts" setup>
import {onMounted, ref} from "vue"
import TopBanner from "@/components/common/banners/TopBanner.vue"
import CommitteeForm from "@/components/form/CommitteeForm.vue"
import {$require} from "@/plugins/require.ts"
import {$handleNetworkError} from "@/plugins/handleNetworkError.ts"
import {
  type CommitteeDetailResponse,
  deleteCommitteeById,
  findCommittees,
  findUsers,
  type UserDetailResponse,
} from "@/services/api"
import DeletionConfirmationDialog from "@/components/common/modals/DeletionConfirmationDialog.vue"

type CommitteeModel = {
  id?: number
  name: string
  description: string
  members: Array<{ userId: number; role: string }>
  version?: number
}

const toCommitteeModel = (committee: CommitteeDetailResponse): CommitteeModel => ({
  id: committee.id,
  name: committee.name,
  description: committee.description,
  version: committee.version,
  members: committee.members.map((member) => ({
    userId: member.userId,
    role: member.role,
  })),
})

const committees = ref<CommitteeModel[]>([])
const committeeToDelete = ref<CommitteeModel | null>(null)
const editingCommitteeId = ref<number | null>(null)
const submittingId = ref<number | null>(null)
const creatingCommittee = ref(false)
const loading = ref(false)
const noCommittees = ref(false)
/** Set where the list could not be read, which is not the same as there being none. */
const committeesUnknown = ref(false)
const users = ref<UserDetailResponse[]>([])

async function fetchCommittees(): Promise<void> {
  committeesUnknown.value = false
  try {
    // Throws, so a failed read reaches the handler instead of arriving as an empty list.
    const resp = await findCommittees({throwOnError: true})
    if (resp.data?.length) {
      committees.value = (resp.data as CommitteeDetailResponse[]).map(toCommitteeModel)
      noCommittees.value = false
    } else {
      noCommittees.value = true
    }
  } catch (error: unknown) {
    committeesUnknown.value = true
    $handleNetworkError(error)
  }
}

async function fetchUsers(): Promise<void> {
  try {
    const resp = await findUsers({throwOnError: true})
    users.value = resp.data?.content ?? []
  } catch (error: unknown) {
    $handleNetworkError(error)
  }
}

async function deleteCommittee(): Promise<void> {
  if (!committeeToDelete.value?.id) return
  try {
    await deleteCommitteeById({path: {id: committeeToDelete.value.id}, throwOnError: true})
    committees.value = committees.value.filter(
      c => c.id !== committeeToDelete.value?.id,
    )
  } catch (error: unknown) {
    $handleNetworkError(error)
  } finally {
    committeeToDelete.value = null
  }
}

function toggleEditingCommittee(committeeId: number | undefined): void {
  if (!committeeId) return
  editingCommitteeId.value =
    editingCommitteeId.value === committeeId ? null : committeeId
}

function updateCommittee(committee: CommitteeModel) {
  const list = committees.value
  const idx = list.findIndex((e) => e.id === committee.id)
  committees.value =
    idx >= 0
      ? [...list.slice(0, idx), committee, ...list.slice(idx + 1)]
      : [...list, committee]
  loading.value = false
  creatingCommittee.value = false
  editingCommitteeId.value = null
  submittingId.value = null
}

onMounted(async () => {
  await Promise.all([fetchCommittees(), fetchUsers()])
})
</script>

<template>
  <v-main>
    <top-banner title="Committee Manager" />
    <v-container>
      <div
        class="mx-auto my-10"
        style="max-width: 800px"
      >
        <v-btn
          :disabled="loading"
          :loading="loading"
          :variant="creatingCommittee ? 'outlined' : 'text'"
          block
          data-testid="committee-manager-create-toggle-btn"
          @click="creatingCommittee = !creatingCommittee"
        >
          {{ creatingCommittee ? "Stop creating committee" : "Create new committee" }}
        </v-btn>

        <v-expand-transition>
          <div
            v-if="creatingCommittee"
            class="form-border mx-auto rounded-b"
            data-testid="committee-manager-create-form"
            style="border-top-width: 0"
          >
            <committee-form
              :users="users"
              class="form"
              show-submit
              @submitting="(submitting: boolean) => loading = submitting"
              @update:model-value="updateCommittee"
            />
          </div>
        </v-expand-transition>

        <v-list :lines="'two'">
          <div
            v-for="(committee, i) in committees"
            :key="committee.id || committee.name"
          >
            <v-list-item :data-testid="`committee-row-${committee.id}`">
              <v-list-item-title class="text-h6">
                {{ committee.name }}
              </v-list-item-title>
              <v-list-item-subtitle>
                {{ committee.members?.length || 0 }}
                member{{ (committee.members?.length || 0) === 1 ? "" : "s" }}
              </v-list-item-subtitle>

              <template #append>
                <v-tooltip
                  location="left"
                  text="Edit committee"
                >
                  <template #activator="{ props: tooltip }">
                    <v-btn
                      :disabled="submittingId === committee.id"
                      :loading="submittingId === committee.id"
                      :data-testid="`committee-edit-btn-${committee.id}`"
                      icon="mdi-pencil"
                      v-bind="tooltip"
                      variant="plain"
                      @click="toggleEditingCommittee(committee.id)"
                    />
                  </template>
                </v-tooltip>

                <v-tooltip
                  v-if="$store.getters.isBoard"
                  location="left"
                  text="Delete committee"
                >
                  <template #activator="{ props: tooltip }">
                    <v-btn
                      icon="mdi-delete"
                      :data-testid="`committee-delete-btn-${committee.id}`"
                      v-bind="tooltip"
                      variant="plain"
                      @click="committeeToDelete = committee"
                    />
                  </template>
                </v-tooltip>
              </template>
            </v-list-item>

            <v-expand-transition>
              <div
                v-if="editingCommitteeId === committee.id"
                class="form-border mx-auto rounded-b"
                :data-testid="`committee-manager-edit-form-${committee.id}`"
              >
                <committee-form
                  :model-value="committee"
                  :users="users"
                  class="form"
                  show-submit
                  @submitting="(submitting: boolean) => submittingId = submitting ? committee.id : null"
                  @update:model-value="updateCommittee"
                />
              </div>
            </v-expand-transition>

            <v-divider
              v-if="i < committees.length - 1 && editingCommitteeId !== committee.id"
              :key="`divider-${i}`"
            />
          </div>
        </v-list>

        <deletion-confirmation-dialog
          :model-value="!!committeeToDelete"
          :title="`Delete committee: ${committeeToDelete?.name ?? ''}`"
          message="There will be no undo."
          @confirm="deleteCommittee"
          @update:model-value="(open: boolean) => { if (!open) committeeToDelete = null }"
        />

        <v-alert
          v-if="committeesUnknown"
          data-testid="committee-manager-load-failed"
          type="warning"
          variant="tonal"
        >
          The committees could not be read, so none are shown. Reload the page to try again.
        </v-alert>

        <v-img
          v-else-if="noCommittees"
          :src="$require('@/assets/noCommittees.jpg')"
        />
      </div>
    </v-container>
  </v-main>
</template>

<style lang="scss" scoped>
.form-border {
  border-width: 1px;
  border-color: rgb(var(--v-theme-accent));
  border-style: solid;
}

.form {
  padding: 16px;
}
</style>
