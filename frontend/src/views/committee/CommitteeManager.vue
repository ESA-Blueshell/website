<script lang="ts" setup>
import {onMounted, ref} from "vue"
import TopBanner from "@/components/banners/TopBanner.vue"
import CommitteeEdit from "@/views/committee/CommitteeEdit.vue"
import {$require} from "@/plugins/require.js"
import {$handleNetworkError} from "@/plugins/handleNetworkError.js"
import {type AdvancedCommittee, type AdvancedUser, deleteCommitteeById, findCommittees, findUsers} from "@/lib"
import DeletionConfirmationDialog from "@/components/DeletionConfirmationDialog.vue"

const committees = ref<AdvancedCommittee[]>([])
const committeeToDelete = ref<AdvancedCommittee | null>(null)
const editingCommitteeId = ref<number | null>(null)
const submittingId = ref<number | null>(null)
const creatingCommittee = ref(false)
const loading = ref(false)
const noCommittees = ref(false)
const users = ref<AdvancedUser[]>([])

async function fetchCommittees(): Promise<void> {
  try {
    const resp = await findCommittees()
    if (resp.data?.length) {
      committees.value = (resp.data as AdvancedCommittee[]) ?? []
    } else {
      noCommittees.value = true
    }
  } catch (error: unknown) {
    $handleNetworkError(error)
  }
}

async function fetchUsers(): Promise<void> {
  try {
    const resp = await findUsers()
    users.value = resp.data?.content ?? []
  } catch (error: unknown) {
    $handleNetworkError(error)
  }
}

async function deleteCommittee(): Promise<void> {
  if (!committeeToDelete.value?.id) return
  try {
    await deleteCommitteeById({path: {id: committeeToDelete.value.id}})
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

function updateCommittee(committee: AdvancedCommittee) {
  const list = committees.value
  const idx = list.findIndex(e => e.id === committee.id)
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
    <div class="mx-3">
      <div
        class="mx-auto my-10"
        style="max-width: 800px"
      >
        <v-btn
          :loading="loading"
          :variant="creatingCommittee ? 'outlined' : 'text'"
          block
          @click="creatingCommittee = !creatingCommittee"
        >
          {{ creatingCommittee ? "Stop creating committee" : "Create new committee" }}
        </v-btn>

        <v-expand-transition>
          <div
            v-if="creatingCommittee"
            class="form-border mx-auto rounded-b"
            style="border-top-width: 0"
          >
            <committee-edit
              :model-value="committee"
              class="form"
              :users="users"
              @update:model-value="updateCommittee"
              @submitting="(submitting: boolean) => loading = submitting"
            />
          </div>
        </v-expand-transition>

        <v-list :lines="'two'">
          <div
            v-for="(committee, i) in committees"
            :key="committee.id || committee.name"
          >
            <v-list-item>
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
                      :loading="submittingId === committee.id"
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
              >
                <committee-edit
                  :model-value="committee"
                  class="form"
                  :users="users"
                  @update:model-value="updateCommittee"
                  @submitting="(submitting: boolean) => submittingId = submitting ? committee.id : null"
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
          @update:model-value="(open: boolean) => { if (!open) committeeToDelete = null }"
          @confirm="deleteCommittee"
        />

        <v-img
          v-if="noCommittees"
          :src="$require('@/assets/noCommittees.jpg')"
        />
      </div>
    </div>
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
