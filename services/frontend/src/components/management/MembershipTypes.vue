<script lang="ts" setup>
import {ref} from "vue"
import ListHead from "@/components/management/ListHead.vue"
import ManagementTable, {type TableColumn} from "@/components/management/ManagementTable.vue"
import MembershipTypeFields from "@/components/management/MembershipTypeFields.vue"
import MiniButton from "@/components/management/MiniButton.vue"
import {type MembershipResponse, type MembershipTypeResponse, removeTypeFrom} from "@/domains/user"
import {$showStatusMessage} from "@/plugins/handleNetworkError.ts"
import {memberTypeLabel} from "@/utils/memberType"
import {formatDay} from "@/utils/timestamps"

/* The types one membership has had, earliest first, as in "alumni since 1 September 2025". The
   board adds, corrects and removes them here; the first starts with the membership and stays. */
defineOptions({name: "MembershipTypes"})

const {membership} = defineProps<{membership: MembershipResponse}>()
const emit = defineEmits<{changed: [membership: MembershipResponse]}>()

const COLUMNS: TableColumn<MembershipTypeResponse>[] = [
  {key: "type", label: "Type", sortBy: (row) => memberTypeLabel(row.type)},
  {key: "since", label: "Starts on", sortBy: (row) => row.since},
]

const editingId = ref<number | null>(null)

const isFirst = (entry: MembershipTypeResponse) => entry.id === membership.types[0]?.id

const saved = (changed: MembershipResponse) => {
  editingId.value = null
  emit("changed", changed)
}

const remove = async (entry: MembershipTypeResponse) => {
  const answer = await removeTypeFrom(membership.id, entry.id)
  if (answer.ok) saved(answer.saved)
  else $showStatusMessage(answer.reason)
}
</script>

<template>
  <div
    class="membership-types"
    :data-testid="`manage-membership-types-${membership.id}`"
  >
    <list-head title="Membership types" />
    <management-table
      :columns="COLUMNS"
      :row-key="(entry) => entry.id"
      :row-testid="(entry) => `manage-membership-type-row-${entry.id}`"
      :rows="membership.types"
      search-label="Search membership types"
      :search-text="(entry) => `${memberTypeLabel(entry.type)} ${formatDay(entry.since)}`"
    >
      <template #type="{row}">
        {{ memberTypeLabel(row.type) }}
      </template>
      <template #since="{row}">
        {{ formatDay(row.since) }}
      </template>
      <template #acts="{row}">
        <mini-button
          :testid="`manage-membership-type-edit-btn-${row.id}`"
          @click="editingId = editingId === row.id ? null : row.id"
        >
          {{ editingId === row.id ? "Close" : "Edit" }}
        </mini-button>
        <mini-button
          v-if="!isFirst(row)"
          :testid="`manage-membership-type-remove-btn-${row.id}`"
          tone="danger"
          @click="remove(row)"
        >
          Remove
        </mini-button>
      </template>
    </management-table>

    <template
      v-for="entry in membership.types"
      :key="entry.id"
    >
      <membership-type-fields
        v-if="editingId === entry.id"
        :entry="entry"
        :membership="membership"
        submit-text="Save type"
        :submit-testid="`manage-membership-type-save-btn-${entry.id}`"
        @saved="saved"
      />
    </template>

    <list-head title="Add a membership type" />
    <membership-type-fields
      :key="membership.types.length"
      :membership="membership"
      submit-text="Add type"
      :submit-testid="`manage-membership-type-add-btn-${membership.id}`"
      @saved="saved"
    />
  </div>
</template>

<style scoped>
.membership-types {
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
  margin-top: 1rem;
}
</style>
