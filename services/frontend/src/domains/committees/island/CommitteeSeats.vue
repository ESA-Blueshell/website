<script lang="ts">
/** A person on the committee and the role they hold there, empty where they hold none. */
export type Seat = {userId: number; role: string}
</script>

<script lang="ts" setup>
import {computed, onBeforeUnmount, ref, watch} from "vue"
import FormControl from "@/components/island/FormControl.vue"
import FormField from "@/components/island/FormField.vue"
import IconButton from "@/components/island/IconButton.vue"
import SearchPicker from "@/components/island/SearchPicker.vue"
import {findMemberAccounts, readUser, type UserDetailResponse} from "@/domains/user"

/**
 * Who sits on a committee, as the board sets it: each person by name with the role they hold, a
 * way to take them off, and a search that finds anybody else by what is typed. The accounts are
 * asked for as the reader types, never the whole table, and the people already seated are named
 * one by one.
 */
defineOptions({name: "CommitteeSeats"})

const seats = defineModel<Seat[]>({required: true})

/** One page is what a reader reads before typing more. */
const PAGE = 20
const SETTLE_MS = 250

const names = ref(new Map<number, string>())
const found = ref<UserDetailResponse[] | null>([])
const typed = ref("")
const searching = ref(false)
let settling: ReturnType<typeof setTimeout> | undefined
let latest = 0

const nameOf = (user: UserDetailResponse) => user.fullName || user.email || `Account ${user.id}`
const shownName = (userId: number) => names.value.get(userId) ?? "…"

watch(() => seats.value.map(one => one.userId), async ids => {
  const unnamed = ids.filter(id => !names.value.has(id))
  const read = await Promise.all(unnamed.map(readUser))
  const next = new Map(names.value)
  read.forEach((user, at) => next.set(unnamed[at]!, user ? nameOf(user) : `Account ${unnamed[at]}`))
  names.value = next
}, {immediate: true})

const ask = async (term: string) => {
  const mine = ++latest
  searching.value = true
  try {
    const answer = await findMemberAccounts(term, PAGE)
    if (mine === latest) found.value = answer
  } finally {
    if (mine === latest) searching.value = false
  }
}

const onSearch = (term: string) => {
  typed.value = term.trim()
  if (settling) clearTimeout(settling)
  if (typed.value === "") {
    latest++
    found.value = []
    searching.value = false
    return
  }
  settling = setTimeout(() => void ask(typed.value), SETTLE_MS)
}

onBeforeUnmount(() => {
  if (settling) clearTimeout(settling)
})

const options = computed(() => (found.value ?? [])
  .filter(user => user.id != null && !seats.value.some(seat => seat.userId === user.id))
  .map(user => ({
    key: String(user.id),
    label: nameOf(user),
    note: user.discord ?? user.email ?? undefined,
  })))

/** Said under the search only once somebody has searched, and only for what the search found. */
const emptyNote = computed(() => {
  if (typed.value === "" || searching.value) return ""
  if (found.value === null) return "The search did not go through. Try again, or sign in again if it keeps failing."
  return found.value.length === 0
    ? `Nobody found for "${typed.value}".`
    : `Everybody found for "${typed.value}" is on the committee already.`
})

const seat = (key: string) => {
  const user = found.value?.find(one => String(one.id) === key)
  if (!user?.id) return
  names.value = new Map(names.value).set(user.id, nameOf(user))
  seats.value = [...seats.value, {userId: user.id, role: ""}]
}

const unseat = (userId: number) => {
  seats.value = seats.value.filter(one => one.userId !== userId)
}
</script>

<template>
  <div class="committee-seats">
    <p
      v-if="seats.length === 0"
      class="committee-seats__none"
      data-testid="committee-edit-no-seats"
    >
      Nobody is on this committee yet.
    </p>
    <ul
      v-else
      class="committee-seats__list"
    >
      <li
        v-for="one in seats"
        :key="one.userId"
        class="committee-seats__seat"
        :data-testid="`committee-edit-seat-${one.userId}`"
      >
        <span class="committee-seats__who">{{ shownName(one.userId) }}</span>
        <form-control
          v-model="one.role"
          class="committee-seats__role"
          label="Role"
          :testid="`committee-edit-role-${one.userId}`"
        />
        <icon-button
          danger
          :label="`Take ${shownName(one.userId)} off the committee`"
          :testid="`committee-edit-unseat-${one.userId}`"
          @click="unseat(one.userId)"
        >
          <svg
            aria-hidden="true"
            fill="none"
            stroke="currentColor"
            stroke-linecap="round"
            stroke-width="1.6"
            viewBox="0 0 24 24"
          ><path d="M6 6l12 12M18 6 6 18" /></svg>
        </icon-button>
      </li>
    </ul>
    <form-field
      label="Add somebody"
      testid="committee-edit-member"
    >
      <template #default="{controlId, labelId}">
        <search-picker
          :control-id="controlId"
          :empty-note="emptyNote"
          :labelled-by="labelId"
          :loading="searching"
          :options="options"
          placeholder="Type a name, username or Discord handle"
          remote
          testid-prefix="committee-edit-member"
          @pick="seat"
          @search="onSearch"
        />
      </template>
    </form-field>
  </div>
</template>

<style scoped>
.committee-seats {
  display: grid;
  gap: 1rem;
}

.committee-seats__none {
  margin: 0;
  font-size: 0.9rem;
  color: var(--color-ash);
}

.committee-seats__list {
  display: grid;
  gap: 0.6rem;
  margin: 0;
  padding: 0;
  list-style: none;
}

.committee-seats__seat {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1.4fr) auto;
  gap: 0.9rem;
  align-items: center;
}

.committee-seats__who {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (max-width: 639px) {
  .committee-seats__seat {
    grid-template-columns: minmax(0, 1fr) auto;
  }

  .committee-seats__role {
    grid-column: 1 / -1;
    grid-row: 2;
  }
}
</style>
