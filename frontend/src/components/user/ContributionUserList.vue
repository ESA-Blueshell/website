<template>
  <div>
    <p class="text-h3">
      {{ title }} ({{ users.length }})
    </p>
    <v-list class="mt-3">
      <div
        v-for="user in users"
        :key="user.id ?? user.username"
      >
        <contribution-user-list-row
          :user="user"
          :contributions="contributions"
          :selected-period-id="selectedPeriodId"
          :disabled="disabled"
          @update:contribution="contributionChanged"
          @delete:contribution="contributionDeleted"
        />
        <v-divider />
      </div>
    </v-list>
  </div>
</template>

<script lang="ts" setup>
import ContributionUserListRow from "./ContributionUserListRow.vue"
import type {AdvancedUser, Contribution} from "@/lib/types.gen.ts"
import {toRefs} from "vue"

const props = withDefaults(defineProps<{
  title: string
  users: AdvancedUser[]
  contributions?: Contribution[]
  selectedPeriodId?: number
  disabled?: boolean
}>(), {
  contributions: () => [],
  selectedPeriodId: 0,
  disabled: false,
})
const {title, users, contributions, selectedPeriodId, disabled} = toRefs(props)

const emit = defineEmits<{
  (e: "delete:contribution", id: number): void
  (e: "update:contribution", contribution: Contribution): void
}>()

const contributionChanged = (c: Contribution) => emit("update:contribution", c)
const contributionDeleted = (id: number) => emit("delete:contribution", id)
</script>
