<template>
  <router-link
    v-if="userId != null"
    class="person-link"
    :data-testid="testid"
    :to="`/management/users/${userId}`"
  >
    <slot>{{ name }}</slot>
  </router-link>
  <span
    v-else
    :data-testid="testid"
  ><slot>{{ name }}</slot></span>
</template>

<script lang="ts" setup>
/* A person named in a list, leading to their page in Management. Somebody without an account,
   or whose account is gone, is named without a link. */
const {userId = undefined, name = "", testid = undefined} = defineProps<{
  userId?: number | null
  name?: string | null
  testid?: string
}>()
</script>

<style scoped>
.person-link:hover,
.person-link:focus-visible {
  text-decoration: underline;
  text-underline-offset: 3px;
}
</style>
