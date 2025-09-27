<template>
  <v-main>
    <top-banner title="Committee Manager" />
    <div class="mx-3">
      <div
        class="mx-auto my-10"
        style="max-width: 800px"
      >
        <v-btn
          :loading="creatingLoading"
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
              :committee="{} as AdvancedCommittee"
              :members="members"
              class="form"
              @close="handleCreateClose"
              @submitting="creatingLoading = true"
            />
          </div>
        </v-expand-transition>

        <v-dialog
          :model-value="!!committeeToDelete"
          width="auto"
        >
          <template #activator="{ props: dialog }">
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
                    {{ committee.members?.length || 0 }} membership{{
                      (committee.members?.length || 0) === 1 ? "" : "s"
                    }}
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
                          v-bind="{ ...tooltip, ...dialog }"
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
                      :committee="committee"
                      class="form"
                      :members="members"
                      @close="handleEditClose"
                      @submitting="submittingId = committee.id"
                    />
                  </div>
                </v-expand-transition>

                <v-divider
                  v-if="i < committees.length - 1 && editingCommitteeId !== committee.id"
                  :key="`divider-${i}`"
                />
              </div>
            </v-list>
          </template>

          <v-card>
            <v-card-title>
              <span class="text-h6">
                Are you sure you want to delete this committee:
                {{ committeeToDelete?.name || "" }}
              </span>
            </v-card-title>
            <v-card-text>
              There will be no undo
            </v-card-text>
            <v-card-actions>
              <v-spacer />
              <v-btn
                variant="text"
                @click="committeeToDelete = null"
              >
                No
              </v-btn>
              <v-btn
                color="error"
                variant="text"
                @click="deleteCommittee"
              >
                Yes
              </v-btn>
            </v-card-actions>
          </v-card>
        </v-dialog>

        <v-img
          v-if="noCommittees"
          :src="$require('@/assets/noCommittees.jpg')"
        />
      </div>
    </div>
  </v-main>
</template>

<script lang="ts" setup>
import {onMounted, ref} from "vue"
import TopBanner from "@/components/banners/TopBanner.vue"
import CommitteeEdit from "@/views/committee/CommitteeEdit.vue"
import {$require} from "@/plugins/require.js"
import {$handleNetworkError} from "@/plugins/handleNetworkError.js"
import {type AdvancedCommittee, type AdvancedUser, deleteCommitteeById, findCommittees, findUsers} from "@/lib"

// state
const committees = ref<AdvancedCommittee[]>([])
const committeeToDelete = ref<AdvancedCommittee | null>(null)
const editingCommitteeId = ref<number | null>(null)
const submittingId = ref<number | null>(null)
const creatingCommittee = ref(false)
const creatingLoading = ref(false)
const noCommittees = ref(false)
const members = ref<AdvancedUser[]>([])

// methods (same logic as before)
async function fetchCommittees(): Promise<void> {
  try {
    const resp = await findCommittees()

    if (resp.data?.length) {
      committees.value = resp.data as AdvancedCommittee[] ?? []
    } else {
      noCommittees.value = true
    }
  } catch (error: unknown) {
    $handleNetworkError(error)
  }
}

async function fetchMembers(): Promise<void> {
  try {
    const resp = await findUsers({
      query: {
        isMember: true,
      },
    })
    members.value = resp.data?.content ?? []
  } catch (error: unknown) {
    $handleNetworkError(error)
  }
}

async function deleteCommittee(): Promise<void> {
  if (!committeeToDelete.value?.id) return

  try {
    await deleteCommitteeById({
      path: {id: committeeToDelete.value.id},
    })

    committees.value = committees.value.filter(
      committee => committee.id !== committeeToDelete.value?.id,
    )
    committeeToDelete.value = null
  } catch (error: unknown) {
    $handleNetworkError(error)
  }
}

function toggleEditingCommittee(committeeId: number | undefined): void {
  if (!committeeId) return
  editingCommitteeId.value =
    editingCommitteeId.value === committeeId ? null : committeeId
}

function handleCreateClose(): void {
  fetchCommittees()
  creatingCommittee.value = false
  creatingLoading.value = false
}

function handleEditClose(): void {
  editingCommitteeId.value = null
  submittingId.value = null
  fetchCommittees()
}

// lifecycle
onMounted(async () => {
  await Promise.all([
    fetchCommittees(),
    fetchMembers(),
  ])
})
</script>

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
