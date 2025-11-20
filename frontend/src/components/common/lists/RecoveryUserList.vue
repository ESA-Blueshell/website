<template>
  <v-card class="overflow-hidden">
    <div
      :aria-controls="panelId"
      :aria-expanded="String(isOpen)"
      class="px-5 py-3 d-flex align-center justify-space-between"
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
              :user="user"
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
import RecoveryUserRow from "../rows/RecoveryUserRow.vue"
import type {AdvancedUser} from "@/services/api"
import {filterUsers} from "@/plugins/userFilter"

const props = withDefaults(defineProps<{
  title: string
  users: AdvancedUser[]
  /** 'activation' => resend activation (inactive) | 'password' => password reset (active) */
  actionType: "activation" | "password"
  startOpen?: boolean
}>(), {
  startOpen: false,
})

const {title, users, actionType, startOpen} = toRefs(props)

const localSearch = ref("")
const isOpen = ref<boolean>(startOpen.value)
const panelId = `rul-${Math.random().toString(36).slice(2)}`

const filtered = computed(() => filterUsers(users.value, localSearch.value))
const countLabel = computed(() =>
  localSearch.value ? `${filtered.value.length} / ${users.value.length}` : `${users.value.length}`,
)
</script>
