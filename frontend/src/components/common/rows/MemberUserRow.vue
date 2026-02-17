<script lang="ts" setup>
import {computed, ref} from "vue"
import UserForm from "@/components/form/UserForm.vue"
import DeleteConfirmationDialog from "@/components/common/modals/DeletionConfirmationDialog.vue"
import {DateTime} from "luxon"
import StartMembershipDialog from "@/components/common/modals/StartMembershipDialog.vue"
import {
  type CreateUserRequest,
  type ContributionResponse,
  deleteUserById,
  type MembershipResponse,
  updateMembership,
  type UserDetailResponse,
} from "@/services/api"

defineOptions({name: "MemberUserRow"})

const user = defineModel<CreateUserRequest & Partial<UserDetailResponse>>("user", {required: true})
const membership = defineModel<MembershipResponse>("membership", {required: false, default: undefined})
const contribution = defineModel<ContributionResponse>("contribution", {required: false, default: undefined})
const expanded = defineModel<number>("expanded", {default: 0})

withDefaults(defineProps<{
  enableDelete?: boolean
}>(), {
  enableDelete: () => false,
})

const emit = defineEmits<{
  (e: "delete:user", user: CreateUserRequest & Partial<UserDetailResponse>): void
  (e: "update:membership", membership: MembershipResponse): void
}>()

const deleteDialog = ref(false)
const showStartModal = ref(false)

const hasContribution = computed(() => !!contribution.value)

const toggleExpanded = () => {
  expanded.value = expanded.value === user.value.id ? 0 : (user.value.id as number)
}

const startMembership = () => {
  showStartModal.value = true
}

const endMembership = async () => {
  try {
    if (!membership.value) return
    membership.value.userId = user.value.id as number
    membership.value.endDate = DateTime.now().toISODate()
    const response = await updateMembership({path: {id: membership.value.id as number}, body: membership.value})
    if (response.data) emit("update:membership", response.data)
  } catch (error) {
    console.error("Failed to end membership:", error)
  }
}

const resumeMembership = async () => {
  try {
    if (!membership.value) return
    const membershipData: MembershipResponse = {
      ...membership.value,
      userId: user.value.id as number,
      endDate: undefined,
    }
    const response = await updateMembership({path: {id: membershipData.id as number}, body: membershipData})
    if (response.data) emit("update:membership", response.data)
  } catch (error) {
    console.error("Failed to resume membership:", error)
  }
}

const membershipChanged = (value: MembershipResponse): void => {
  emit("update:membership", value)
}

const onSubmitted = (ok: boolean) => {
  if (ok) expanded.value = 0
}

const openDelete = () => {
  deleteDialog.value = true
}

const confirmDeleteUser = async () => {
  try {
    deleteDialog.value = false
    await deleteUserById({path: {userId: user.value.id as number}})
    emit("delete:user", user.value)
  } catch (error) {
    console.error("Failed to delete user:", error)
  }
}
</script>

<template>
  <div>
    <v-list-item>
      <div
        class="d-flex justify-space-between align-center"
        role="button"
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
            v-if="!!user?.roles?.length"
            class="mx-3 d-flex justify-center align-center text-capitalize"
            size="small"
            style="width: 80px"
            variant="flat"
          >
            {{ user.roles.at(-1)?.toLocaleLowerCase() }}
          </v-chip>

          <v-chip
            :color="hasContribution ? 'green' : 'red'"
            class="mr-3 d-flex justify-center align-center"
            size="small"
            style="width: 50px"
            variant="flat"
          >
            {{ hasContribution ? "Paid" : "Unpaid" }}
          </v-chip>

          <v-btn
            v-if="enableDelete"
            :disabled="user?.roles?.includes('ADMIN')"
            class="btn-tight"
            color="red"
            variant="text"
            @click.stop="openDelete"
          >
            Delete
          </v-btn>

          <template v-if="membership">
            <v-btn
              v-if="membership.endDate"
              class="btn-tight"
              variant="text"
              @click.stop="resumeMembership"
            >
              Resume Membership
            </v-btn>
            <v-btn
              v-else
              :disabled="user.roles?.includes('COMMITTEE')"
              class="btn-tight"
              variant="text"
              @click.stop="endMembership"
            >
              End Membership
            </v-btn>
          </template>
          <template v-else>
            <v-btn
              class="btn-tight"
              variant="text"
              @click.stop="startMembership"
            >
              Start Membership
            </v-btn>
          </template>
        </div>
      </div>

      <v-expand-transition>
        <div
          v-if="expanded === user.id"
          @click.stop
        >
          <user-form
            v-model="user"
            :options="{ includeMemberProfile: true, updateKind: 'board' }"
            class="mt-6"
            show-submit
            @submitted="onSubmitted"
          />
        </div>
      </v-expand-transition>
    </v-list-item>
  </div>

  <start-membership-dialog
    v-model="showStartModal"
    :membership="membership"
    :user-id="user.id"
    @update:membership="membershipChanged"
  />

  <delete-confirmation-dialog
    v-model="deleteDialog"
    :message="`Are you sure you want to delete ${user.fullName} (${user.username})?`"
    title="Confirm User Deletion"
    @confirm="confirmDeleteUser"
  />
</template>

<style lang="scss">
span {
  font-weight: bold;
}

.btn-tight {
  padding-inline: 6px !important;
  min-width: auto !important;
}
</style>
