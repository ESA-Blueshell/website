<script lang="ts" setup>
/**
 * The Discord field as a picker over the server's members: each shown with their avatar, their
 * name in the server and their username. Picking one hands back their name and their user ID.
 *
 * Until the api says its bot is there, and whenever it is not, the field is the text field it
 * replaces, so an account never waits on Discord. With nothing picked, the name already typed is
 * the first search. Opened with nothing typed, it lists everybody in the server no account has
 * linked yet. A search that finds nobody says so in the list, with the way into the server, and an
 * emptied box left behind takes the choice away.
 */
import {computed, onBeforeUnmount, onMounted, ref} from "vue"
import {useFieldName} from "@/components/form/fields/fieldName"
import {firstSaid} from "@/components/form/fields/saidWrong"
import FormControl from "@/components/island/FormControl.vue"
import FormField from "@/components/island/FormField.vue"
import SearchPicker from "@/components/island/SearchPicker.vue"
import {DISCORD_INVITE} from "@/components/island/socialGlyphs"
import {type DiscordMemberResponse, listUnclaimedMembers, searchServerMembers} from "../index"

const props = withDefaults(defineProps<{
  modelValue?: string | null
  discordId?: string | null
  label?: string
  errorMessages?: string | string[]
  disabled?: boolean
  testid?: string
}>(), {
  modelValue: "",
  discordId: null,
  label: "Discord",
  errorMessages: () => [],
  disabled: false,
  testid: undefined,
})

const emit = defineEmits<{
  "update:modelValue": [name: string]
  "update:discordId": [id: string | null]
}>()

const named = useFieldName(props.testid)
const SETTLE_MS = 250
/** What the api needs before it asks Discord. */
const MIN_QUERY = 2

const available = ref(false)
const found = ref<DiscordMemberResponse[]>([])
/* Read once, the first time the list opens: the whole server, which a page view need not load. */
const unclaimed = ref<DiscordMemberResponse[] | null>(null)
const loading = ref(false)
/* Only a search Discord answered can say somebody is not there; one character asks nothing. */
const nobodyFound = ref(false)
let settling: ReturnType<typeof setTimeout> | undefined
let latest = 0

onMounted(async () => {
  available.value = (await searchServerMembers("")) !== null
})
onBeforeUnmount(() => clearTimeout(settling))

const ask = async (term: string): Promise<void> => {
  const mine = ++latest
  loading.value = true
  try {
    const answer = await searchServerMembers(term)
    // An older answer must not overwrite a newer one: the reader has typed since.
    if (mine !== latest) return
    if (answer === null) available.value = false
    else found.value = answer
    nobodyFound.value = answer !== null && answer.length === 0
  } finally {
    if (mine === latest) loading.value = false
  }
}

/* Short of what the api searches, the unclaimed list narrows as the reader types. */
const fromUnclaimed = (term: string): DiscordMemberResponse[] => {
  const asked = term.toLowerCase()
  return (unclaimed.value ?? []).filter(one =>
    one.name.toLowerCase().includes(asked) || one.username.toLowerCase().includes(asked))
}

const onSearch = (term: string) => {
  clearTimeout(settling)
  latest++
  loading.value = false
  nobodyFound.value = false
  if (term.length < MIN_QUERY) {
    found.value = fromUnclaimed(term)
    return
  }
  settling = setTimeout(() => void ask(term), SETTLE_MS)
}

const onOpened = async () => {
  if (unclaimed.value !== null) return
  const listed = await listUnclaimedMembers()
  if (listed === null) return
  unclaimed.value = listed
  if (found.value.length === 0 && !nobodyFound.value) found.value = listed
}

const onClear = () => {
  found.value = unclaimed.value ?? []
  nobodyFound.value = false
  emit("update:modelValue", "")
  emit("update:discordId", null)
}

/* The member already linked stays a row, or the field would draw empty before any search. */
const options = computed(() => {
  const rows = found.value.map(one => ({key: one.id, label: one.name, note: `@${one.username}`, avatar: one.avatar}))
  if (!props.discordId || rows.some(row => row.key === props.discordId)) return rows
  return [{key: props.discordId, label: props.modelValue ?? ""}, ...rows]
})

const onPick = (key: string) => {
  const one = found.value.find(member => member.id === key)
  if (!one) return
  emit("update:modelValue", one.name)
  emit("update:discordId", one.id)
}

/* A name typed by hand links nobody. */
const onType = (name: string | null) => {
  emit("update:modelValue", name ?? "")
  emit("update:discordId", null)
}

const error = computed<string>(() => firstSaid(props.errorMessages))
/* A trailing star is how a form says required, which the field draws as its own mark. */
const required = computed<boolean>(() => props.label.trimEnd().endsWith("*"))
const said = computed<string>(() => props.label.trimEnd().replace(/\*$/u, "").trimEnd())
</script>

<template>
  <div class="discord-picker">
    <form-field
      v-if="available"
      :error="error"
      :filled="Boolean(discordId)"
      :label="said"
      :required="required"
      :testid="named"
      variant="inside"
    >
      <template #default="{controlId, labelId}">
        <search-picker
          :control-id="controlId"
          :disabled="disabled"
          empty-note=""
          :first-search="discordId ? undefined : modelValue || undefined"
          :labelled-by="labelId"
          :loading="loading"
          :options="options"
          placeholder="Search the Discord server"
          remote
          :selected-key="discordId"
          :testid-prefix="named ?? 'discord-picker'"
          @clear="onClear"
          @opened="onOpened"
          @pick="onPick"
          @search="onSearch"
        >
          <template
            v-if="nobodyFound"
            #missing
          >
            It seems you're not in our Discord server yet.
            <a
              :href="DISCORD_INVITE"
              rel="noopener"
              target="_blank"
            >Join it here</a>, then try again.
          </template>
        </search-picker>
      </template>
    </form-field>
    <form-control
      v-else
      :disabled="disabled"
      :error-messages="errorMessages"
      :label="label"
      :model-value="modelValue"
      :testid="named"
      @update:model-value="onType"
    />
  </div>
</template>
