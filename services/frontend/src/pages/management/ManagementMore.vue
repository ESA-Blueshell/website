<template>
  <management-page
    eyebrow="Management"
    testid="management-more"
    title="More"
  >
    <div
      v-for="group in groups"
      :key="group.label ?? ''"
      class="mg-more__group"
    >
      <list-head
        v-if="group.label"
        :title="group.label"
      />
      <management-row
        v-for="entry in group.entries"
        :key="entry.to"
        :data-testid="`management-more-${entry.label.toLowerCase().replace(/\s+/g, '-')}`"
        :name="entry.label"
        :to="entry.to"
      />
    </div>
  </management-page>
</template>

<script lang="ts" setup>
import {computed} from "vue"
import {useStore} from "vuex"
import ListHead from "@/components/management/ListHead.vue"
import {managementFor} from "@/components/management/managementNav"
import ManagementPage from "@/components/management/ManagementPage.vue"
import ManagementRow from "@/components/management/ManagementRow.vue"

defineOptions({name: "ManagementMore"})

const store = useStore()

/** Every page the reader may open, grouped as the sidebar groups them; a phone has no sidebar. */
const groups = computed(() => managementFor({board: store.getters.isBoard === true, admin: store.getters.isAdmin === true}))
</script>

<style scoped>
.mg-more__group {
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin-top: 1.2rem;
}
</style>
