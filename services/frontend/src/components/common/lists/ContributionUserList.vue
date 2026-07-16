<template>
  <v-card
    :data-testid="`contribution-user-list-${resolvedPanelKey}`"
    class="overflow-hidden"
  >
    <div
      :aria-controls="panelId"
      :aria-expanded="String(isOpen)"
      class="px-5 py-3 d-flex align-center justify-space-between"
      :data-testid="`contribution-user-list-toggle-${resolvedPanelKey}`"
      role="button"
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
        color="grey-darken-1"
        size="24"
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
          clearable
          :data-testid="`contribution-user-list-search-${resolvedPanelKey}`"
          density="comfortable"
          hide-details
          label="Search for a user"
          prepend-inner-icon="mdi-magnify"
        />

        <v-list class="mt-1">
          <template
            v-for="user in filtered"
            :key="user.id ?? user.username"
          >
            <contribution-user-row
              :contribution-period-id="contributionPeriodId"
              :contributions="contributions"
              :period-member-user-ids="periodMemberUserIds"
              :disabled="disabled"
              :user="user"
              @update:contribution="contributionChanged"
              @delete:contribution="contributionDeleted"
            />
            <v-divider />
          </template>

          <div
            v-if="filtered.length === 0"
            :data-testid="`contribution-user-list-empty-${resolvedPanelKey}`"
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
import type {ContributionResponse, UserDetailResponse} from "@/services/api"
import {filterUsers} from "@/plugins/userFilter"

const props = withDefaults(defineProps<{
  title: string
  panelKey?: string
  users: UserDetailResponse[]
  contributionPeriodId: number
  contributions?: ContributionResponse[]
  periodMemberUserIds?: Set<number>
  disabled?: boolean
  startOpen?: boolean
}>(), {
  panelKey: "",
  contributions: () => [],
  periodMemberUserIds: () => new Set<number>(),
  disabled: false,
  startOpen: false,
})

const {
  title,
  panelKey,
  users,
  contributions,
  periodMemberUserIds,
  disabled,
  contributionPeriodId,
  startOpen,
} = toRefs(props)

const emit = defineEmits<{
  (e: "delete:contribution", userId: number): void
  (e: "update:contribution", contribution: ContributionResponse): void
}>()

const localSearch = ref("")
const isOpen = ref<boolean>(startOpen.value)
const panelId = `cul-${Math.random().toString(36).slice(2)}`
const resolvedPanelKey = computed(() => panelKey.value || title.value.toLowerCase().replace(/\s+/g, "-"))

const filtered = computed(() => filterUsers(users.value, localSearch.value))
const countLabel = computed(() =>
  localSearch.value ? `${filtered.value.length} / ${users.value.length}` : `${users.value.length}`,
)

const contributionChanged = (c: ContributionResponse) => emit("update:contribution", c)
const contributionDeleted = (userId: number) => emit("delete:contribution", userId)
</script>
