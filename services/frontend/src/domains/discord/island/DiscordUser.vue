<script lang="ts" setup>
/* A Discord user, named the way Discord writes a mention: @name on Discord's tint, with their
   picture before it where asked, and a link to their profile where their account is known. One
   part for every place the site names somebody on Discord, in three sizes. */
import {computed} from "vue"

defineOptions({name: "DiscordUser"})

const {name, id = undefined, avatar = undefined, icon = false, size = "md", testid = undefined} = defineProps<{
  /** Their Discord name, written without the @. */
  name: string
  /** Their Discord user id; without one the mark names them and links nowhere. */
  id?: string | null
  /** Their picture; with icon and none, their initial stands in. */
  avatar?: string | null
  /** Draws their picture before the name. */
  icon?: boolean
  size?: "sm" | "md" | "lg"
  testid?: string
}>()

const href = computed(() => (id ? `https://discord.com/users/${id}` : undefined))
const initial = computed(() => name.trim().charAt(0).toUpperCase())
</script>

<template>
  <component
    :is="href ? 'a' : 'span'"
    class="discord-user"
    :class="[`discord-user--${size}`, {'discord-user--icon': icon}]"
    :data-testid="testid"
    :href="href"
    :rel="href ? 'noopener' : undefined"
    :target="href ? '_blank' : undefined"
    @click.stop
  >
    <template v-if="icon">
      <img
        v-if="avatar"
        alt=""
        class="discord-user__face"
        :src="avatar"
      >
      <span
        v-else
        aria-hidden="true"
        class="discord-user__face discord-user__face--initial"
      >{{ initial }}</span>
    </template>
    <span class="discord-user__name">@{{ name }}</span>
  </component>
</template>

<style scoped>
.discord-user {
  --face: 1.4em;
  display: inline-flex;
  align-items: center;
  gap: 0.4em;
  max-width: 100%;
  white-space: nowrap;
  vertical-align: middle;
}

.discord-user--sm {
  font-size: 0.8rem;
}

.discord-user--md {
  font-size: 0.9rem;
}

.discord-user--lg {
  --face: 2em;
  font-size: 1.05rem;
}

.discord-user__name {
  overflow: hidden;
  text-overflow: ellipsis;
  padding: 0 0.3em;
  border-radius: 3px;
  font-weight: 500;
  color: #c9cdfb;
  background-color: rgb(88 101 242 / 30%);
}

a.discord-user:hover .discord-user__name {
  color: #ffffff;
  background-color: #5865f2;
}

.discord-user__face {
  flex: none;
  width: var(--face);
  height: var(--face);
  border-radius: 50%;
  object-fit: cover;
}

.discord-user__face--initial {
  display: grid;
  place-items: center;
  font-size: 0.75em;
  font-weight: 600;
  color: #ffffff;
  background-color: #5865f2;
}

:where([data-theme="light"]) .discord-user__name {
  color: #4752c4;
  background-color: rgb(88 101 242 / 15%);
}
</style>
