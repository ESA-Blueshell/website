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
                :disabled="user?.roles?.includes('ADMIN')"
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
              v-if="membership?.endDate"
              variant="text"
              @click.stop="resumeMembership()"
            >
              Resume Membership
            </v-btn>
            <v-btn
              v-else-if="membership?.startDate"
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
        <div
          v-if="expanded === user.id"
          @click.stop
        >
          <AdvancedUserEdit
            v-model="userModel"
            class="mt-6"
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

<script setup lang="ts">
import {computed, ref} from 'vue';
import AdvancedUserEdit from '@/components/user/AdvancedUserEdit.vue';
import DeleteConfirmationDialog from "@/components/DeletionConfirmationDialog.vue";
import MemberTypeSelect from "@/components/select/MemberTypeSelect.vue";
import {DateTime} from 'luxon';
import {
  type AdvancedUserDto,
  type ContributionDto,
  createMembership,
  deleteUserById,
  type MembershipDto, MemberType,
  setContributionPaid,
  updateMembership
} from "@/lib";

interface Props {
  user: AdvancedUserDto;
  contributions?: Array<ContributionDto>;
  memberships?: Array<MembershipDto>;
  expanded?: number | null;
  isMemberList?: boolean;
}

interface Emits {
  (e: 'toggle-expanded', userId: number): void;
  (e: 'user-changed', userData: AdvancedUserDto): void;
  (e: 'contribution-changed', contribution: ContributionDto): void;
  (e: 'membership-changed', membership: MembershipDto): void;
  (e: 'delete-user', user: AdvancedUserDto): void;
}

const props = withDefaults(defineProps<Props>(), {
  contributions: () => [],
  memberships: () => [],
  expanded: null,
  isMemberList: false,
});

const emit = defineEmits<Emits>();

// Reactive state
const deleteDialog = ref(false);
const showStartModal = ref(false);
const startDate = ref(DateTime.now().toISODate());
const memberType = ref<MemberType>(MemberType.REGULAR);
const isSubmitting = ref(false);

const contribution = computed(() =>
  props.contributions.find((c) => c.userId === props.user.id)
);

const membership = computed(() =>
  props.memberships.find((m) => m.userId === props.user.id)
)

const userModel = computed<AdvancedUserDto>({
  get: () => props.user
});

const toggleExpanded = () => {
  emit('toggle-expanded', props.user.id as number);
};

const startMembership = () => {
  showStartModal.value = true;
};

const confirmStartMembership = async () => {
  try {
    isSubmitting.value = true;

    const membershipData: MembershipDto = {
      userId: props.user.id as number,
      memberType: memberType.value,
      startDate: DateTime.fromISO(startDate.value).toISO() as string,
      endDate: undefined
    };

    const response = await createMembership({
      body: membershipData
    });

    if (response.data) {
      const changedUser = {
        ...props.user,
        membership: response.data
      };
      userChanged(changedUser);
      showStartModal.value = false;
    }
  } catch (error) {
    console.error('Failed to create membership:', error);
  } finally {
    isSubmitting.value = false;
  }
};

const endMembership = async () => {
  try {
    const membershipData: MembershipDto = {
      userId: props.user.id as number,
      ...membership,
      endDate: DateTime.now().toISO()
    };

    const response = await updateMembership({
      path: {id: membershipData.id as number},
      body: membershipData
    });

    if (response.data) {
      emit('membership-changed', response.data);
    }
  } catch (error) {
    console.error('Failed to end membership:', error);
  }
};

const resumeMembership = async () => {
  try {
    const membershipData: MembershipDto = {
      id: 0,
      userId: props.user.id as number,
      ...membership,
      endDate: undefined
    };

    const response = await updateMembership({
      path: {id: membershipData.id as number},
      body: membershipData
    });

    if (response.data) {
      emit('membership-changed', response.data);
    }
  } catch (error) {
    console.error('Failed to resume membership:', error);
  }
};

const deleteUser = () => {
  deleteDialog.value = true;
};

const confirmDeleteUser = async () => {
  try {
    deleteDialog.value = false;

    await deleteUserById({
      path: {userId: props.user.id as number}
    });

    emit('delete-user', props.user);
  } catch (error) {
    console.error('Failed to delete user:', error);
  }
};

const userChanged = (userData: AdvancedUserDto) => {
  emit('user-changed', userData);
};

const changeContributionPaid = async (paid: boolean) => {
  try {
    if (contribution.value) {
      const response = await setContributionPaid({
        path: {id: contribution.value.id as number},
        query: {paid}
      });

      if (response.data) {
        emit('contribution-changed', response.data);
      }
    }
  } catch (error) {
    console.error('Failed to change contribution paid status:', error);
  }
};
</script>

<style lang="scss">
span {
  font-weight: bold;
}
</style>
