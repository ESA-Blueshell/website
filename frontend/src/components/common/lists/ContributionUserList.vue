<template>
  <div>
    <v-card>
      <h2 class="text-center">
        {{ title }} ({{ users.length }})
      </h2>
    </v-card>

    <v-list class="mt-3">
      <div
        v-for="user in users"
        :key="user.id ?? user.username"
      >
        <contribution-user-row
          :contribution-period-id="contributionPeriodId"
          :contributions="contributions"
          :disabled="disabled"
          :user="user"
          @update:contribution="contributionChanged"
          @delete:contribution="contributionDeleted"
        />
        <v-divider />
      </div>
    </v-list>
  </div>
</template>

<script lang="ts" setup>
import ContributionUserRow from "../rows/ContributionUserRow.vue"
import type {AdvancedUser, Contribution} from "@/services/api"
import {toRefs} from "vue"

const props = withDefaults(defineProps<{
  title: string
  users: AdvancedUser[]
  contributionPeriodId: number
  contributions?: Contribution[]
  disabled?: boolean
}>(), {
  contributions: () => [],
  selectedPeriodId: 0,
  disabled: false,
})
const {title, users, contributions, disabled} = toRefs(props)

const emit = defineEmits<{
  (e: "delete:contribution", id: number): void
  (e: "update:contribution", contribution: Contribution): void
}>()

const contributionChanged = (c: Contribution) => emit("update:contribution", c)
const contributionDeleted = (id: number) => emit("delete:contribution", id)
</script>
