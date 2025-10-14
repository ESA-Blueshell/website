<template>
  <div>
    <v-list-item>
      <div
        class="d-flex justify-space-between align-center"
        style="width: 100%;"
        @click="toggleExpanded"
      >
        <div class="flex-grow-1">
          <v-list-item-title>{{ user.fullName }}</v-list-item-title>
          <v-list-item-subtitle>{{ user.username }}</v-list-item-subtitle>
        </div>

        <div
          class="d-flex align-center"
          style="flex-shrink: 0;"
        >
          <v-chip
            v-if="!!user?.roles?.at(-1)"
            class="mx-3 d-flex justify-center align-center text-capitalize"
            size="small"
            style="width: 80px"
            variant="flat"
          >
            {{ user.roles.at(-1).toLocaleLowerCase() }}
          </v-chip>


          <v-chip
            class="mr-3 d-flex justify-center align-center"
            size="small"
            style="width: 50px"
            :color="hasContribution ? 'green' : 'red'"
            variant="flat"
          >
            {{ hasContribution ? "Paid" : "Unpaid" }}
          </v-chip>


          <v-btn
            v-if="enableDelete"
            :disabled="user?.roles?.includes('ADMIN')"
            color="red"
            variant="text"
            class="btn-tight"
            @click.stop="openDelete"
          >
            Delete
          </v-btn>


          <template
            v-if="membership"
          >
            <v-btn
              v-if="membership.endDate"
              variant="text"
              class="btn-tight"
              @click.stop="resumeMembership"
            >
              Resume Membership
            </v-btn>
            <v-btn
              v-else
              variant="text"
              class="btn-tight"
              :disabled="user.roles.includes('COMMITTEE')"
              @click.stop="endMembership"
            >
              End Membership
            </v-btn>
          </template>
          <template v-else>
            <v-btn
              variant="text"
              class="btn-tight"
              @click.stop="startMembership"
            >
              Start Membership
            </v-btn>
          </template>
        </div>
      </div>

      <!-- Editable user form -->
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

  <!-- Start membership -->
  <start-membership-dialog
    v-model="showStartModal"
    :memberships="memberships"
    :user-id="user.id"
    @update:membership="membershipChanged"
  />

  <!-- Delete confirm -->
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
import StartMembershipDialog from "@/components/membership/StartMembershipDialog.vue"

import {type AdvancedUser, type Contribution, deleteUserById, type Membership, updateMembership} from "@/lib"

interface Props {
  user: AdvancedUser
  memberships?: Array<Membership>
  contributions?: Array<Contribution>
  expanded?: number | null
  enableDelete?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  memberships: () => [],
  contributions: () => [],
  expanded: null,
  enableDelete: () => false,
})

const emit = defineEmits<{
  (e: "delete:user", user: AdvancedUser): void
  (e: "update:user", user: AdvancedUser): void
  (e: "update:membership", membership: Membership): void
  (e: "update:expanded", userId: number): void
}>()

const deleteDialog = ref(false)
const showStartModal = ref(false)

const membership = computed<Membership | undefined>(() =>
  props.memberships.find((m) => m.userId === props.user.id),
)


const contribution = computed<Contribution | undefined>(() =>
  props.contributions.find(
    (c) => c.userId === props.user.id,
  ),
)

const hasContribution = computed(() => !!contribution.value)

const userModel = computed<AdvancedUser>(() => props.user)

const toggleExpanded = () => emit("update:expanded", props.user.id as number)
const startMembership = () => {
  showStartModal.value = true
}

const endMembership = async () => {
  try {
    if (!membership.value) return
    const membershipData: Membership = {
      ...membership.value,
      userId: props.user.id as number,
      endDate: DateTime.now().toISODate(),
    }
    const response = await updateMembership({path: {id: membershipData.id as number}, body: membershipData})
    if (response.data) emit("update:membership", response.data)
  } catch (error) {
    console.error("Failed to end membership:", error)
  }
}

const resumeMembership = async () => {
  try {
    if (!membership.value) return
    const membershipData: Membership = {
      ...membership.value,
      userId: props.user.id as number,
      endDate: undefined,
    }
    const response = await updateMembership({path: {id: membershipData.id as number}, body: membershipData})
    if (response.data) emit("update:membership", response.data)
  } catch (error) {
    console.error("Failed to resume membership:", error)
  }
}

const membershipChanged = (value: Membership): void => {
  emit("update:membership", value)
}

const openDelete = () => {
  deleteDialog.value = true
}

const confirmDeleteUser = async () => {
  try {
    deleteDialog.value = false
    await deleteUserById({path: {userId: props.user.id as number}})
    emit("delete:user", props.user)
  } catch (error) {
    console.error("Failed to delete user:", error)
  }
}

const userChanged = (userData: AdvancedUser) => {
  emit("update:user", userData)
}
</script>

<style lang="scss">
span {
  font-weight: bold;
}

.btn-tight {
  padding-inline: 6px !important;
  min-width: auto !important;
}
</style>
