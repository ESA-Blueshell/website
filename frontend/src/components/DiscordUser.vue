<script lang="ts" setup>
import type {WidgetMember} from "@/lib"

type PresenceStatus = WidgetMember["status"] // string in your model; keep as-is

interface Props {
  username?: string
  status?: PresenceStatus
  avatarUrl?: string
  halfWidth?: boolean
  customText?: string
}

withDefaults(defineProps<Props>(), {
  halfWidth: false,
  status: undefined,
  username: undefined,
  avatarUrl: undefined,
  customText: undefined,
})
</script>

<template>
  <v-col
    v-if="!customText"
    :md="halfWidth ? 6 : 3"
    class="discord-membership-entry"
    cols="6"
    sm="4"
  >
    <div class="discord-membership-image-wrapper">
      <v-lazy
        :options="{ threshold: 0.1 }"
        height="32px"
        width="32px"
      >
        <img
          :alt="`${username}'s avatar`"
          :src="avatarUrl"
          class="discord-membership"
        >
      </v-lazy>
      <span
        :class="{
          'discord-membership-online': status === 'online',
          'discord-membership-idle': status === 'idle',
          'discord-membership-dnd': status === 'dnd'
        }"
        class="discord-membership-status"
      />
    </div>
    <span
      class="discord-membership-name text-caption"
      v-text="username"
    />
  </v-col>

  <v-col
    v-else
    :md="halfWidth ? 6 : 3"
    class="discord-membership-entry"
    cols="6"
    sm="4"
  >
    <span
      class="discord-membership-name"
      v-text="customText"
    />
  </v-col>
</template>

<style lang="scss" scoped>
.discord-membership-entry {
  display: flex;
  align-items: center;
  margin: 6px 0;
  padding: 0 16px;
}

.discord-membership-image-wrapper {
  width: 32px;
  height: 32px;
  position: relative;
  margin-right: 4px;
}

.discord-membership {
  width: 32px;
  height: 32px;
  border-radius: 16px;
}

.discord-membership-status {
  width: 12px;
  height: 12px;
  border-radius: 6px;
  border: 1px solid #1E1E1E;
  position: absolute;
  bottom: 0;
  right: 0;
}

.discord-membership-name {
  white-space: nowrap;
  text-overflow: ellipsis;
  overflow: hidden;
  flex: 1;
  color: white;
}

.discord-membership-online {
  background-color: hsl(139, 47.4%, 38%);
}

.discord-membership-idle {
  background-color: hsl(38, 77%, 43%);
}

.discord-membership-dnd {
  background-color: hsl(359, 66.7%, 54.1%);
}
</style>
