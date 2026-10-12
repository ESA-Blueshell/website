<template>
  <management-page
    eyebrow="Management"
    testid="management-more"
    title="More"
  >
    <div
      v-for="group in groups"
      :key="group.label"
      class="mg-more__group"
    >
      <list-head :title="group.label" />
      <management-row
        v-for="entry in group.entries"
        :key="entry.to"
        :data-testid="`management-more-${slug(entry.label)}`"
        :name="entry.label"
        :to="entry.to"
      />
    </div>
  </management-page>
</template>

<script lang="ts" setup>
import ListHead from "@/components/management/ListHead.vue"
import {slug} from "@/components/management/managementNav"
import ManagementPage from "@/components/management/ManagementPage.vue"
import ManagementRow from "@/components/management/ManagementRow.vue"
import {useManagementGroups} from "@/composables/useManagementGroups"

defineOptions({name: "ManagementMore"})

/** Grouped as the sidebar groups them; a phone has no sidebar. */
const groups = useManagementGroups()
</script>

<style scoped>
.mg-more__group {
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin-top: 1.2rem;
}
</style>
