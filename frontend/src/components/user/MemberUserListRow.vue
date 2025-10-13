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
          <!--          TODO: Uncomment, and restrict visibility to admins-->
          <!--          <div class="d-flex align-center mr-4">-->
          <!--            <span class="mr-2">Enabled</span>-->
          <!--            <v-icon-->
          <!--              v-if="user.enabled"-->
          <!--              class="mr-2"-->
          <!--              color="green"-->
          <!--            >-->
          <!--              mdi-check-->
          <!--            </v-icon>-->
          <!--            <v-icon-->
          <!--              v-else-->
          <!--              class="mr-2"-->
          <!--              color="red"-->
          <!--            >-->
          <!--              mdi-close-->
          <!--            </v-icon>-->
          <!--          </div>-->

          <v-chip
            v-if="!!user?.roles?.at(-1)"
            class="mr-3"
            size="small"
            variant="flat"
          >
            {{ user.roles.at(-1) }}
          </v-chip>

          <!-- Delete user -->
          <v-btn
            :disabled="user?.roles?.includes('ADMIN')"
            :v-if="enableDelete"
            color="red"
            variant="text"
            @click.stop="openDelete"
          >
            Delete
          </v-btn>

          <!-- Membership controls -->
          <template v-if="membership">
            <v-btn
              v-if="membership.endDate"
              variant="text"
              @click.stop="resumeMembership"
            >
              Resume Membership
            </v-btn>
            <v-btn
              v-else
              variant="text"
              :disabled="user.roles.includes('COMMITTEE')"
              @click.stop="endMembership"
            >
              End Membership
            </v-btn>
          </template>
          <template v-else>
            <v-btn
              variant="text"
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

import {type AdvancedUser, deleteUserById, type Membership, updateMembership} from "@/lib"

interface Props {
  user: AdvancedUser
  memberships?: Array<Membership>
  expanded?: number | null
  enableDelete?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  memberships: () => [],
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

const userModel = computed<AdvancedUser>({
  get: () => props.user,
})

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
      endDate: DateTime.now().toISO(),
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
</style>
