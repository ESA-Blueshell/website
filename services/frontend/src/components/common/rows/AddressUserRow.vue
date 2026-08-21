<template>
  <div>
    <v-list-item :data-testid="`address-user-row-${user.id}`">
      <div
        class="d-flex justify-space-between align-center"
        :data-testid="`address-user-toggle-${user.id}`"
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
            v-if="!!user?.roles?.at(-1)"
            class="mx-3 d-flex justify-center align-center text-capitalize"
            size="small"
            style="width: 80px"
            variant="flat"
          >
            {{ user.roles.at(-1).toLocaleLowerCase() }}
          </v-chip>

          <v-btn
            v-if="hasAddress"
            :disabled="user?.roles?.includes('MEMBER')"
            class="btn-tight"
            color="red"
            :data-testid="`address-user-delete-btn-${user.id}`"
            variant="text"
            @click.stop="openDelete"
          >
            Delete Address
          </v-btn>

          <v-btn
            class="btn-tight"
            :data-testid="`address-user-edit-btn-${user.id}`"
            variant="text"
            @click.stop="toggleExpanded"
          >
            {{ hasAddress ? "Edit Address" : "Add Address" }}
          </v-btn>
        </div>
      </div>

      <v-expand-transition>
        <div
          v-if="expanded === user.id"
          class="mb-3"
          :data-testid="`address-user-form-${user.id}`"
          @click.stop
        >
          <!-- Writable v-model proxy pushes updates upward via emit -->
          <address-form
            v-model="addressModel"
            :user-id="user.id"
            class="mt-6"
            :data-testid="`address-user-edit-form-${user.id}`"
            show-submit
            submit-text="Save Address"
            @submitted="onSubmitted"
          />
        </div>
      </v-expand-transition>
    </v-list-item>
  </div>

  <delete-confirmation-dialog
    v-model="deleteDialog"
    :message="`Are you sure you want to delete the address belonging to ${user.fullName} (${user.username})?`"
    title="Confirm Address deletion"
    @confirm="confirmDeleteAddress"
  />
</template>

<script lang="ts" setup>
import {computed, ref} from "vue"
import AddressForm from "@/components/form/AddressForm.vue"
import DeleteConfirmationDialog from "@/components/common/modals/DeletionConfirmationDialog.vue"
import {type AddressResponse, deleteAddressById, type UserDetailResponse} from "@/services/api"

type ManagedUser = UserDetailResponse & { addressId?: number | null }
type ManagedAddress = AddressResponse & { userId?: number | null }

interface Props {
  user: ManagedUser
  addresses?: Array<ManagedAddress>
  expanded?: number | null
}

const props = withDefaults(defineProps<Props>(), {
  addresses: () => [],
  expanded: null,
})

const emit = defineEmits<{
  (e: "update:address", address: ManagedAddress): void
  (e: "delete:address", addressId: number): void
  (e: "update:expanded", userId: number): void
}>()

const deleteDialog = ref(false)

const address = computed<ManagedAddress | undefined>(() =>
  props.addresses.find((a) => a.id === props.user.addressId || a.userId === props.user.id),
)
const hasAddress = computed(() => !!address.value)

/** Writable proxy so AddressForm v-model updates bubble up to the list */
const addressModel = computed<ManagedAddress | undefined>({
  get: () => address.value,
  set: (next?: ManagedAddress) => {
    if (next) emit("update:address", next)
  },
})

const user = computed(() => props.user)

const toggleExpanded = () => emit("update:expanded", props.user.id as number)

const onSubmitted = (ok: boolean) => {
  if (!ok) return
  if (address.value) emit("update:address", address.value)
  emit("update:expanded", 0)
}

const openDelete = () => {
  deleteDialog.value = true
}

const confirmDeleteAddress = async () => {
  if (!props.user.id || !address.value?.id) return
  try {
    deleteDialog.value = false
    await deleteAddressById({path: {id: address.value.id}})
    emit("delete:address", address.value.id)
  } catch (error) {
    console.error("Failed to delete user:", error)
  }
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
