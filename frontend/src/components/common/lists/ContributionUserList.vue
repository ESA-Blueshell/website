<template>
  <v-card class="overflow-hidden">
    <div
      class="px-5 py-3 d-flex align-center justify-space-between"
      role="button"
      :aria-expanded="String(isOpen)"
      :aria-controls="panelId"
      tabindex="0"
      @click="isOpen = !isOpen"
      @keydown.enter.prevent="isOpen = !isOpen"
      @keydown.space.prevent="isOpen = !isOpen"
    >
      <v-badge
        :content="countLabel"
        color="primary"
      >
        <h2 class="ma-0">
          {{ title }}
        </h2>
      </v-badge>

      <v-icon
        size="24"
        color="grey-darken-1"
      >
        {{ isOpen ? "mdi-chevron-up" : "mdi-chevron-down" }}
      </v-icon>
    </div>

    <v-expand-transition>
      <div
        v-show="isOpen"
        :id="panelId"
        class="px-5 pb-4 pt-2"
      >
        <v-text-field
          v-model="localSearch"
          label="Search for a user"
          clearable
          density="comfortable"
          prepend-inner-icon="mdi-magnify"
          hide-details
        />

        <v-list class="mt-1">
          <template
            v-for="user in filtered"
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
          </template>

          <div
            v-if="filtered.length === 0"
            class="text-medium-emphasis text-center py-6"
          >
            No users found.
          </div>
        </v-list>
      </div>
    </v-expand-transition>
  </v-card>
</template>

<script lang="ts" setup>
import {computed, ref, toRefs} from "vue"
import ContributionUserRow from "../rows/ContributionUserRow.vue"
import type {AdvancedUser, Contribution} from "@/services/api"
import {filterUsers} from "@/plugins/userFilter"

const props = withDefaults(defineProps<{
  title: string
  users: AdvancedUser[]
  contributionPeriodId: number
  contributions?: Contribution[]
  disabled?: boolean
  startOpen?: boolean
}>(), {
  contributions: () => [],
  disabled: false,
  startOpen: false,
})

const {title, users, contributions, disabled, contributionPeriodId, startOpen} = toRefs(props)

const emit = defineEmits<{
  (e: "delete:contribution", id: number): void
  (e: "update:contribution", contribution: Contribution): void
}>()

const localSearch = ref("")
const isOpen = ref<boolean>(startOpen.value)
const panelId = `cul-${Math.random().toString(36).slice(2)}`

const filtered = computed(() => filterUsers(users.value, localSearch.value))
const countLabel = computed(() =>
  localSearch.value ? `${filtered.value.length} / ${users.value.length}` : `${users.value.length}`,
)

const contributionChanged = (c: Contribution) => emit("update:contribution", c)
const contributionDeleted = (id: number) => emit("delete:contribution", id)
</script>
