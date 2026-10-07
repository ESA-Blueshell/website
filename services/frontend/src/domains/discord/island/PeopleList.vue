<script lang="ts" setup>
/* People in a row parted by leaning rules: by their Discord name and picture where Discord is
   linked, otherwise by username without a picture, each with a role where they hold one. Asked
   to, it keeps to its first row and offers the rest as "and N more". */
import {computed, nextTick, onBeforeUnmount, onMounted, ref, watch} from "vue"
import DiscordUser from "./DiscordUser.vue"

defineOptions({name: "PeopleList"})

/** One person as the list draws them. */
export interface ListedPerson {
  name: string
  avatar?: string | null
  /** Whether the name is their Discord name, drawn as Discord writes a mention. */
  discord: boolean
  role?: string | null
}

const {people, oneRow = false, testid = undefined, itemTestid = undefined} = defineProps<{
  people: ListedPerson[]
  /** Keeps to the first row and offers the rest on asking; how many fit is the row's to say. */
  oneRow?: boolean
  testid?: string
  /** What each person's data-testid is built from; the list's own testid where not given. */
  itemTestid?: string
}>()

const itemPrefix = computed(() => itemTestid ?? testid)

const list = ref<HTMLElement | null>(null)
const open = ref(false)
const hidden = ref(0)

// Counted off the laid-out items: those whose top is below the first item's do not fit the first row.
const measure = async () => {
  await nextTick()
  if (!oneRow || open.value || !list.value) return void (hidden.value = 0)
  const items = [...list.value.children] as HTMLElement[]
  const first = items[0]?.offsetTop ?? 0
  hidden.value = items.filter((one) => one.offsetTop > first).length
}

let observer: ResizeObserver | undefined
onMounted(() => {
  void measure()
  if (typeof ResizeObserver !== "undefined" && list.value) {
    observer = new ResizeObserver(() => void measure())
    observer.observe(list.value)
  }
})
onBeforeUnmount(() => observer?.disconnect())
watch(() => people, () => void measure(), {deep: true})

const clipped = computed(() => oneRow && !open.value)
</script>

<template>
  <div class="people">
    <ul
      ref="list"
      class="people__list"
      :class="{'people__list--one-row': clipped}"
      :data-testid="testid"
    >
      <li
        v-for="(person, at) in people"
        :key="at"
        class="people__person"
        :data-testid="itemPrefix ? `${itemPrefix}-${at}` : undefined"
      >
        <span class="people__who">
          <discord-user
            v-if="person.discord"
            :avatar="person.avatar"
            class="people__name"
            icon
            :name="person.name"
            size="lg"
          />
          <span
            v-else
            class="people__name people__name--plain"
          >{{ person.name }}</span>
          <span
            v-if="person.role"
            class="people__role"
          >{{ person.role }}</span>
        </span>
      </li>
    </ul>
    <button
      v-if="clipped && hidden > 0"
      class="people__more"
      :data-testid="testid ? `${testid}-more` : undefined"
      type="button"
      @click="open = true"
    >
      and {{ hidden }} more
    </button>
  </div>
</template>

<style scoped>
/*
 * One row parted by a rule at the lean the buttons are cut on, as the partners are. The rule
 * stands just left of each person and the row clips its left edge, so a person that wraps to the
 * start of a line has none before it.
 */
.people__list {
  display: flex;
  flex-wrap: wrap;
  row-gap: 0.9rem;
  margin: 0.7rem 0 0 -1.5rem;
  padding: 0;
  overflow: hidden;
  list-style: none;
}

/* The first row only: the height of one person, with what wraps under it out of sight. */
.people__list--one-row {
  max-height: 3rem;
}

.people__person {
  position: relative;
  display: flex;
  gap: 0.75rem;
  align-items: center;
  min-width: 0;
  padding: 0 1.5rem;
}

.people__person::before {
  position: absolute;
  top: 0.2rem;
  bottom: 0.2rem;
  left: -4px;
  width: 1px;
  content: "";
  background-color: var(--color-hairline);
  transform: skewX(-12deg);
}

.people__who {
  display: flex;
  flex-direction: column;
  min-width: 0;
  line-height: 1.2;
}

.people__name {
  font-size: 1.05rem;
  color: var(--color-chalk);
  white-space: nowrap;
}

.people__role {
  font-size: 0.72rem;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--color-ash);
}

.people__more {
  margin-top: 0.6rem;
  font-size: 0.92rem;
  color: var(--color-ash);
  text-decoration: underline;
  text-underline-offset: 3px;
  cursor: pointer;
}
</style>
