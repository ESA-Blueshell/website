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
          block
          :variant="creatingCommittee ? 'outlined' : 'text'"
          @click="creatingCommittee = !creatingCommittee"
        >
          {{ creatingCommittee ? 'Stop creating committee' : 'Create new committee' }}
        </v-btn>

        <v-expand-transition>
          <div
            v-if="creatingCommittee"
            class="form-border mx-auto rounded-b"
            style="border-top-width: 0"
          >
            <committee-edit
              class="form"
              :committee="{} as AdvancedCommittee"
              @close="handleCreateClose"
              @submitting="creatingLoading = true"
            />
          </div>
        </v-expand-transition>

        <v-dialog
          width="auto"
          :model-value="!!committeeToDelete"
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
                    {{ committee.members?.length || 0 }} membership{{ (committee.members?.length || 0) === 1 ? '' : 's' }}
                  </v-list-item-subtitle>

                  <template #append>
                    <v-tooltip
                      location="left"
                      text="Edit committee"
                    >
                      <template #activator="{ props: tooltip }">
                        <v-btn
                          v-bind="tooltip"
                          :loading="submittingId === committee.id"
                          icon="mdi-pencil"
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
                          v-bind="{ ...tooltip, ...dialog }"
                          icon="mdi-delete"
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
                {{ committeeToDelete?.name || '' }}
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

<script lang="ts">
import { defineComponent } from 'vue'
import TopBanner from '@/components/banners/TopBanner.vue'
import CommitteeEdit from '@/views/committee/CommitteeEdit.vue'
import { $require } from '@/plugins/require.js'
import { $handleNetworkError } from '@/plugins/handleNetworkError.js'
import {
  findCommittees,
  deleteCommitteeById,
  type AdvancedCommittee
} from '@/lib'

interface Data {
  committees: AdvancedCommittee[]
  committeeToDelete: AdvancedCommittee | null
  editingCommitteeId: number | null
  submittingId: number | null
  creatingCommittee: boolean
  creatingLoading: boolean
  noCommittees: boolean
}

export default defineComponent({
  name: 'CommitteeManager',
  components: {
    CommitteeEdit,
    TopBanner
  },
  data(): Data {
    return {
      committees: [],
      committeeToDelete: null,
      editingCommitteeId: null,
      submittingId: null,
      creatingCommittee: false,
      creatingLoading: false,
      noCommittees: false
    }
  },
  mounted(): void {
    this.fetchCommittees()
  },
  methods: {
    $require,

    async fetchCommittees(): Promise<void> {
      try {
        const response = await findCommittees()
        const committeesData = response.data

        if (committeesData && committeesData.length > 0) {
          // Type assertion to ensure proper typing
          this.committees = committeesData.filter(
            (committee): committee is AdvancedCommittee =>
              committee && typeof committee === 'object' && 'name' in committee
          )
        } else {
          this.noCommittees = true
        }
      } catch (error: unknown) {
        $handleNetworkError(error)
      }
    },

    async deleteCommittee(): Promise<void> {
      if (!this.committeeToDelete?.id) {
        return
      }

      try {
        await deleteCommitteeById({
          path: {
            committeeId: this.committeeToDelete.id
          },
          client
        })

        // Remove the committee from local state
        this.committees = this.committees.filter(
          committee => committee.id !== this.committeeToDelete?.id
        )
        this.committeeToDelete = null
      } catch (error: unknown) {
        $handleNetworkError(error)
      }
    },

    toggleEditingCommittee(committeeId: number | undefined): void {
      if (!committeeId) return

      this.editingCommitteeId = this.editingCommitteeId === committeeId ? null : committeeId
    },

    handleCreateClose(): void {
      this.fetchCommittees()
      this.creatingCommittee = false
      this.creatingLoading = false
    },

    handleEditClose(): void {
      this.editingCommitteeId = null
      this.submittingId = null
      this.fetchCommittees()
    }
  }
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
