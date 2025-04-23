<template>
  <v-dialog
    v-model="showStartModal"
    max-width="500"
  >
    <v-card>
      <v-card-title class="text-h5">
        Start Membership
      </v-card-title>
      <v-card-text>
        <v-row>
          <v-text-field
            v-model="startDate"
            :max="new Date().toISOString()"
            label="Start Date"
            type="date"
            required
          />
        </v-row>
        <v-row>
          <member-type-select v-model="memberType" />
        </v-row>
      </v-card-text>
      <v-card-actions>
        <v-spacer />
        <v-btn
          color="secondary"
          @click="showStartModal = false"
        >
          Cancel
        </v-btn>
        <v-btn
          color="primary"
          :loading="isSubmitting"
          @click="confirmStartMembership"
        >
          Confirm
        </v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
  <div>
    <v-list-item>
      <div
        class="d-flex justify-space-between align-center"
        style="width: 100%;"
        @click="toggleExpanded()"
      >
        <!-- Title and Subtitle Section -->
        <div class="flex-grow-1">
          <v-list-item-title>
            {{ user.fullName }}
          </v-list-item-title>
          <v-list-item-subtitle>
            {{ user.username }}
          </v-list-item-subtitle>
        </div>

        <!-- Action Buttons -->
        <div
          class="d-flex align-center"
          style="flex-shrink: 0;"
        >
          <div
            class="d-flex align-center"
          >
            <!--            SHOW WHETHER THE ACCOUNT IS ACTIVE      -->
            <div
              class="d-flex align-center mr-4"
            >
              <span class="mr-2">Enabled</span>
              <v-icon
                v-if="user.enabled"
                color="green"
                class="mr-2"
              >
                mdi-check
              </v-icon>
              <v-icon
                v-else
                color="red"
                class="mr-2"
              >
                mdi-close
              </v-icon>
            </div>

            <template v-if="!isMemberList">
              <!--            DELETE A USER       -->
              <v-btn
                :disabled="user?.roles?.includes(Role.ADMIN)"
                color="red"
                variant="text"
                @click.stop="deleteUser()"
              >
                Delete
              </v-btn>
            </template>
            <template v-else>
              <!--              CHANGE CONTRIBUTION PAID      as-->
              <div
                v-if="contribution"
                class="d-flex align-center mr-4"
              >
                <span class="mr-2">Paid</span>
                <v-icon
                  v-if="contribution.paid"
                  color="green"
                  class="mr-2"
                  @click.stop="changeContributionPaid(false)"
                >
                  mdi-check
                </v-icon>
                <v-icon
                  v-else
                  color="red"
                  class="mr-2"
                  @click.stop="changeContributionPaid(true)"
                >
                  mdi-close
                </v-icon>
              </div>
            </template>
            <!--            TOGGLE MEMBERSHIP       -->
            <v-btn
              v-if="user?.membership?.endDate"
              variant="text"
              @click.stop="resumeMembership()"
            >
              Resume Membership
            </v-btn>
            <v-btn
              v-else-if="user?.membership?.startDate"
              variant="text"
              @click.stop="endMembership()"
            >
              End Membership
            </v-btn>
            <v-btn
              v-else
              variant="text"
              @click.stop="startMembership()"
            >
              Start Membership
            </v-btn>
          </div>
        </div>
      </div>
      <v-expand-transition>
        <div v-if="expanded === user.id">
          <UserEdit
            class="mt-4"
            :user="user"
            @user-changed="userChanged"
          />
        </div>
      </v-expand-transition>
    </v-list-item>
  </div>

  <delete-confirmation-dialog
    v-model="deleteDialog"
    title="Confirm User Deletion"
    :message="`Are you sure you want to delete ${user.fullName} (${user.username})?`"
    @confirm="confirmDeleteUser"
  />
</template>
<script lang="ts">
import UserEdit from '@/components/UserEdit.vue';
import {ContributionService, UserService, MembershipService} from "@/services";
import {type ContributionModel, type MembershipModel, MemberType} from "@/models";
import {computed, ref, toRefs} from 'vue';
import DeleteConfirmationDialog from "@/components/DeletionConfirmationDialog";
import store from "@/plugins/store.ts";
import {type AdvancedUserModel, Role} from "@/models";
import {DateTime} from 'luxon';
import MemberTypeSelect from "@/components/select/MemberTypeSelect.vue";

export default {
  name: 'UserListRow',
  components: {MemberTypeSelect, UserEdit, DeleteConfirmationDialog},
  props: {
    user: {
      type: Object as () => AdvancedUserModel,
      required: true,
    },
    contributions: {
      type: Array as () => ContributionModel[],
      default: () => [],
    },
    expanded: {
      type: Number,
      default: null,
    },
    isMemberList: {
      type: Boolean,
      default: false,
    },
  },
  emits: ['toggle-expanded', 'user-changed', 'contribution-changed', 'delete-user'],
  setup(props, {emit}) {
    const roles = store.getters.getLogin?.roles;
    const disableDelete = !roles || !roles.includes('ADMIN');
    const deleteDialog = ref(false);
    const userService: UserService = new UserService();
    const contributionService: ContributionService = new ContributionService();
    const membershipService: MembershipService = new MembershipService();
    const {user, contributions} = toRefs(props);
    const showStartModal = ref(false);
    const startDate = ref(DateTime.now().toISODate());
    const memberType = ref(MemberType.REGULAR);
    const isSubmitting = ref(false);

    const toggleExpanded = () => {
      emit('toggle-expanded', props.user.id);
    };

    const toggleMembership = async () => {
      const userData: AdvancedUserModel = await userService.toggleRole(user.value.id as number, Role.MEMBER);
      userChanged(userData)
    };

    const startMembership = () => {
      showStartModal.value = true;
    };

    const confirmStartMembership = async () => {
      try {
        isSubmitting.value = true;
        const newMembership = await membershipService.createMembership({
          type: 'MembershipDTO',
          userId: props.user.id as number,
          memberType: memberType.value,
          startDate: DateTime.fromISO(startDate.value).toISO() as string,
          endDate: undefined
        });

        const changedUser = {
          ...props.user,
          membership: newMembership
        };
        userChanged(changedUser);
        showStartModal.value = false;
      } finally {
        isSubmitting.value = false;
      }
    };


    const endMembership = async () => {
      const membership: MembershipModel = user.value.membership as MembershipModel;
      membership.endDate = DateTime.now().toISO().toString();
      const changedMembership: MembershipModel = await membershipService.updateMembership(membership.id as number, membership);
      const changedUser: AdvancedUserModel = user.value;
      changedUser.membership = changedMembership;
      userChanged(changedUser)
    }

    const resumeMembership = async () => {
      const membership: MembershipModel = user.value.membership as MembershipModel;
      membership.endDate = undefined;
      const changedMembership: MembershipModel = await membershipService.updateMembership(membership.id as number, membership);
      const changedUser: AdvancedUserModel = user.value;
      changedUser.membership = changedMembership;
      userChanged(changedUser)
    }

    const deleteUser = () => {
      deleteDialog.value = true;
    };

    const confirmDeleteUser = async () => {
      deleteDialog.value = false;
      await userService.deleteUser(props.user.id as number);
      emit('delete-user', props.user);
    };

    const userChanged = (userData: AdvancedUserModel) => {
      emit('user-changed', userData);
    }

    const contribution = computed(() => {
      return contributions.value.find((c) => c.userId === user.value.id);
    });

    const changeContributionPaid = async (paid: boolean) => {
      if (contribution.value) {
        const changedContribution = await contributionService.markAsPaid(contribution.value.id as number, paid)
        emit('contribution-changed', changedContribution);
      }
    };

    return {
      deleteDialog,
      contribution,
      deleteUser,
      confirmDeleteUser,
      toggleExpanded,
      toggleMembership,
      changeContributionPaid,
      endMembership,
      resumeMembership,
      startMembership,
      disableDelete,
      userChanged,
      Role,
      showStartModal,
      startDate,
      isSubmitting,
      confirmStartMembership,
      memberType
    };
  },
}
</script>

<style scoped>
span {
  font-weight: bold;
}
</style>
