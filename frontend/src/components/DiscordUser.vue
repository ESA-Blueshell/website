<script setup>
const props = defineProps([
  'username',
  'status',
  'avatarUrl',
  'halfWidth',
  'customText',
])
</script>

<template>
  <v-col
    v-if="!customText"
    class="discord-membership-entry"
    cols="6"
    sm="4"
    :md="halfWidth ? 6 : 3"
  >
    <div class="discord-membership-image-wrapper">
      <v-lazy
        width="32px"
        height="32px"
        :options="{'threshold':0.1}"
      >
        <img
          :alt="`${username}'s avatar`"
          :src="avatarUrl"
          class="discord-membership-image"
        >
      </v-lazy>
      <span
        class="discord-membership-status"
        :class="{ 'discord-membership-online': status==='online', 'discord-membership-idle': status==='idle', 'discord-membership-dnd': status==='dnd' }"
      />
    </div>
    <span
      class="discord-membership-name text-caption"
      v-text="username"
    />
  </v-col>


  <v-col
    v-else
    class="discord-membership-entry"
    cols="auto"
  >
    <span
      class="discord-membership-name"
      v-text="customText"
    />
  </v-col>
</template>

<style scoped>

.discord-membership-entry {
  display: -webkit-box;
  display: -ms-flexbox;
  display: flex;
  -webkit-box-align: center;
  -ms-flex-align: center;
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

.discord-membership-image {
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
  -webkit-box-flex: 1;
  -ms-flex: 1;
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
