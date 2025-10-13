<template>
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
                class="mr-2"
                color="green"
              >
                mdi-check
              </v-icon>
              <v-icon
                v-else
                class="mr-2"
                color="red"
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
                  class="mr-2"
                  color="green"
                  @click.stop="changeContributionPaid(false)"
                >
                  mdi-check
                </v-icon>
                <v-icon
                  v-else
                  class="mr-2"
                  color="red"
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
          <AdvancedUserForm
            v-model="userModel"
            class="mt-6"
            @user-changed="userChanged"
          />
        </div>
      </v-expand-transition>
    </v-list-item>
  </div>
  <start-membership-dialog
    v-model="showStartModal"
    :memberships="memberships"
    :user-id="user.id"
  />
  <delete-confirmation-dialog
    v-model="deleteDialog"
    :message="`Are you sure you want to delete ${user.fullName} (${user.username})?`"
    title="Confirm User Deletion"
    @confirm="confirmDeleteUser"
  />
</template>

<script lang="ts" setup>
import {computed, ref} from "vue"
import AdvancedUserForm from "@/components/user/AdvancedUserForm.vue"
import DeleteConfirmationDialog from "@/components/DeletionConfirmationDialog.vue"
import {DateTime} from "luxon"
import {
  type AdvancedUser,
  type Contribution,
  deleteUserById,
  type Membership,
  setContributionPaid,
  updateMembership,
} from "@/lib"
import StartMembershipDialog from "@/components/membership/StartMembershipDialog.vue"

interface Props {
  user: AdvancedUser;
  contributions?: Array<Contribution>;
  memberships?: Array<Membership>;
  expanded?: number | null;
  isMemberList?: boolean;
}

interface Emits {
  (e: "delete:user", id: number): void

  (e: "update:user", user: AdvancedUser): void

  (e: "update:membership", membership: Membership): void

  (e: "update:contribution", contribution: Contribution): void;

  (e: "delete:contribution", id: number): void;

  (e: "update:expanded", userId: number): void
}

const props = withDefaults(defineProps<Props>(), {
  contributions: () => [],
  memberships: () => [],
  expanded: null,
  isMemberList: false,
})

const emit = defineEmits<Emits>()

const deleteDialog = ref(false)
const showStartModal = ref(false)

const contribution = computed(() =>
  props.contributions.find((c) => c.userId === props.user.id),
)

const membership = computed<Membership | undefined>(() =>
  props.memberships.find((m) => m.userId === props.user.id),
)

const userModel = computed<AdvancedUser>({
  get: () => props.user,
})

const toggleExpanded = () => {
  emit("update:expanded", props.user.id as number)
}

const startMembership = () => {
  showStartModal.value = true
}

const endMembership = async () => {
  try {
    const membershipData: Membership = {
      ...membership.value!,
      userId: props.user.id as number,
      endDate: DateTime.now().toISO(),
    }

    const response = await updateMembership({
      path: {id: membershipData.id as number},
      body: membershipData,
    })

    if (response.data) {
      emit("update:membership", response.data)
    }
  } catch (error) {
    console.error("Failed to end membership:", error)
  }
}

const resumeMembership = async () => {
  try {
    const membershipData: Membership = {
      ...membership.value!,
      userId: props.user.id as number,
      endDate: undefined,
    }

    const response = await updateMembership({
      path: {id: membershipData.id as number},
      body: membershipData,
    })

    if (response.data) {
      emit("update:membership", response.data)
    }
  } catch (error) {
    console.error("Failed to resume membership:", error)
  }
}

const deleteUser = () => {
  deleteDialog.value = true
}

const confirmDeleteUser = async () => {
  try {
    deleteDialog.value = false

    await deleteUserById({
      path: {userId: props.user.id as number},
    })

    emit("delete:user", props.user.id!)
  } catch (error) {
    console.error("Failed to delete user:", error)
  }
}

const userChanged = (userData: AdvancedUser) => {
  emit("update:user", userData)
}

const changeContributionPaid = async (paid: boolean) => {
  try {
    if (contribution.value) {
      const response = await setContributionPaid({
        path: {id: contribution.value.id as number},
        query: {paid},
      })

      if (response.data) {
        emit("update:contribution", response.data)
      }
    }
  } catch (error) {
    console.error("Failed to change contribution paid status:", error)
  }
}
</script>

<style lang="scss">
span {
  font-weight: bold;
}
</style>
