<template>
  <v-main>
    <top-banner title="CommitteeModel Manager" />
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
            <edit-committee
              class="form"
              @close="getCommittees();creatingCommittee=false;creatingLoading=false;"
              @submitting="creatingLoading=true"
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
                v-for="(committee,i) in committees"
                :key="committee.name"
              >
                <v-list-item :key="committee.name">
                  <v-list-item-title class="text-h6">
                    {{ committee.name }}
                  </v-list-item-title>
                  <v-list-item-subtitle>
                    {{ committee.members.length }} membership{{ committee.members.length === 1 ? '' : 's' }}
                  </v-list-item-subtitle>

                  <template #append>
                    <v-tooltip
                      location="left"
                      text="Edit committee"
                    >
                      <template #activator="{ tooltip }">
                        <v-btn
                          v-bind="tooltip"
                          :loading="submittingId === committee.id"
                          icon="mdi-pencil"
                          variant="plain"
                          @click="editingCommitteeId= (editingCommitteeId===committee.id ? null : committee.id)"
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

                <v-expand-transition :key="committee.name">
                  <div
                    v-if="editingCommitteeId === committee.id"
                    class="form-border mx-auto rounded-b"
                  >
                    <edit-committee
                      :committee="committee"
                      class="form"
                      @close="editingCommitteeId=null;submittingId=null"
                      @submitting="submittingId=committee.id"
                    />
                  </div>
                </v-expand-transition>

                <v-divider
                  v-if="i < committees.length - 1 && editingCommitteeId !== committee.id"
                  :key="i"
                />
              </div>
            </v-list>
          </template>


          <v-card>
            <v-card-title>
              <span class="text-h6">
                Are you sure you want to delete this committee:
                {{ committeeToDelete ? committeeToDelete.name : '' }}
              </span>
            </v-card-title>
            <v-card-text>
              There will be no undo
            </v-card-text>
            <v-card-actions>
              <v-spacer />
              <v-btn
                variant="text"
                @click="committeeToDelete=null"
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

<script>
import TopBanner from "@/components/banners/TopBanner.vue";
import CommitteeEdit from "@/views/committee/CommitteeEdit.vue";
import {$require} from "@/plugins/require.js";
import {$handleNetworkError} from "@/plugins/handleNetworkError";

export default {
  name: "CommitteeManager",
  components: {EditCommittee: CommitteeEdit, TopBanner: TopBanner},
  data: () => ({
    committees: [],
    committeeToDelete: null,
    editingCommitteeId: null,
    submittingId: null,
    creatingCommittee: false,
    creatingLoading: false,
    noCommittees: false,
  }),
  mounted() {
    // Get the user's committees
    this.getCommittees()
  },
  methods: {
    $require,
    getCommittees() {
      this.$http.get('committees?fullCommittees=true', {headers: {'Authorization': `Bearer ${this.$store.getters.getLogin.token}`}})
        .then(response => {
          if (response.data.length > 0) {
            this.committees = response.data
          } else {
            this.noCommittees = true
          }
        })
        .catch(e => $handleNetworkError(e))
    },
    deleteCommittee() {
      this.$http.delete('committees/' + this.committeeToDelete.id, {headers: {'Authorization': `Bearer ${this.$store.getters.getLogin.token}`}})
        .then(response => {
          if (response.status === 200) {
            this.committees = this.committees.filter(committee => committee.id !== this.committeeToDelete.id)
            this.committeeToDelete = null
          }
        })
        .catch(e => $handleNetworkError(e))
    },
  },
}
</script>

<style scoped>

.form-border {
  border-width: 1px;
  border-color: rgb(var(--v-theme-accent));
  border-style: solid;
}

.form {
  padding: 16px;
}
</style>
