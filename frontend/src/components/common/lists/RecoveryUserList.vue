<template>
  <div>
    <p class="text-h5">
      {{ title }} ({{ users.length }})
    </p>
    <v-list class="mt-3">
      <div
        v-for="user in users"
        :key="user.id ?? user.username"
      >
        <recovery-user-row
          :user="user"
          :action-type="actionType"
        />
        <v-divider />
      </div>
    </v-list>
  </div>
</template>

<script lang="ts" setup>
import {toRefs} from "vue"
import RecoveryUserRow from "../rows/RecoveryUserRow.vue"
import type {AdvancedUser} from "@/services/api"

const props = withDefaults(defineProps<{
  title: string
  users: AdvancedUser[]
  /**
   * 'activation' => resend activation / recovery mail (inactive users)
   * 'password'   => resend password recovery mail (active users)
   */
  actionType: "activation" | "password"
}>(), {})

toRefs(props)
</script>
