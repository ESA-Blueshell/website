<script lang="ts" setup>
/**
 * The Discord field as a picker over the server's members: each shown with their avatar, their
 * name in the server and their username. Picking one hands back their name and their user ID.
 *
 * Until the api says its bot is there, and whenever it is not, the field is the text field it
 * replaces, so an account never waits on Discord. With nothing picked, the name already typed is
 * the first search.
 */
import {computed, onBeforeUnmount, onMounted, ref} from "vue"
import {useFieldName} from "@/components/form/fields/fieldName"
import {firstSaid} from "@/components/form/fields/saidWrong"
import FormControl from "@/components/island/FormControl.vue"
import FormField from "@/components/island/FormField.vue"
import SearchPicker from "@/components/island/SearchPicker.vue"
import {DISCORD_INVITE} from "@/components/island/socialGlyphs"
import {type DiscordMemberResponse, searchServerMembers} from "../index"

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

const available = ref(false)
const found = ref<DiscordMemberResponse[]>([])
const loading = ref(false)
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
  } finally {
    if (mine === latest) loading.value = false
  }
}

const onSearch = (term: string) => {
  clearTimeout(settling)
  settling = setTimeout(() => void ask(term), SETTLE_MS)
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
</script>

<template>
  <div class="discord-picker">
    <form-field
      v-if="available"
      :error="error"
      :filled="Boolean(discordId)"
      :label="label"
      :testid="named"
      variant="inside"
    >
      <template #default="{controlId, labelId}">
        <search-picker
          :control-id="controlId"
          :disabled="disabled"
          empty-note="Type your name in the server, or your username."
          :first-search="discordId ? undefined : modelValue || undefined"
          :labelled-by="labelId"
          :loading="loading"
          :options="options"
          placeholder="Search the Discord server"
          remote
          :selected-key="discordId"
          :testid-prefix="named ?? 'discord-picker'"
          @pick="onPick"
          @search="onSearch"
        />
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
    <p class="discord-picker__join">
      Don't see your account?
      <a
        :href="DISCORD_INVITE"
        rel="noopener"
        target="_blank"
      >Join our Discord</a>
    </p>
  </div>
</template>

<style scoped>
.discord-picker__join {
  margin: 0.25rem 0 0;
  font-size: 0.8rem;
  opacity: 0.8;
}
</style>
