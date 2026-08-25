<template>
  <v-card
    :data-testid="`recovery-user-list-${resolvedPanelKey}`"
    class="overflow-hidden"
  >
    <div
      :aria-controls="panelId"
      :aria-expanded="String(isOpen)"
      class="px-5 py-3 d-flex align-center justify-space-between"
      :data-testid="`recovery-user-list-toggle-${resolvedPanelKey}`"
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
          :data-testid="`recovery-user-list-search-${resolvedPanelKey}`"
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
            <recovery-user-row
              :action-type="actionType"
              :pending-activation="pendingActivations[user.id] ?? null"
              :user="user"
              @action:done="emit('action:done')"
            />
            <v-divider />
          </template>

          <div
            v-if="filtered.length === 0"
            :data-testid="`recovery-user-list-empty-${resolvedPanelKey}`"
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
import RecoveryUserRow from "../rows/RecoveryUserRow.vue"
import type {TokenPurpose, UserDetailResponse} from "@/services/api"
import {filterUsers} from "@/plugins/userFilter"

const props = withDefaults(defineProps<{
  title: string
  panelKey?: string
  users: UserDetailResponse[]
  /** 'activation' => resend activation (inactive) | 'password' => password reset (active) | 'restore' => restore user */
  actionType: "activation" | "password" | "restore"
  /** Which activation each account takes, keyed by user id. */
  pendingActivations?: Record<number, TokenPurpose>
  startOpen?: boolean
}>(), {
  panelKey: "",
  pendingActivations: () => ({}),
  startOpen: false,
})

const {title, panelKey, users, actionType, startOpen} = toRefs(props)
const emit = defineEmits<{
  (e: "action:done"): void
}>()

const localSearch = ref("")
const isOpen = ref<boolean>(startOpen.value)
const panelId = `rul-${Math.random().toString(36).slice(2)}`
const resolvedPanelKey = computed(() => panelKey.value || title.value.toLowerCase().replace(/\s+/g, "-"))

const filtered = computed(() => filterUsers(users.value, localSearch.value))
const countLabel = computed(() =>
  localSearch.value ? `${filtered.value.length} / ${users.value.length}` : `${users.value.length}`,
)
</script>
