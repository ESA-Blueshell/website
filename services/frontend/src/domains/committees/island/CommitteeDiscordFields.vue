<script lang="ts" setup>
/* A committee's Discord on its form: the role its seats hold and the channels that role opens. A new
   committee gets a new role and a private channel by default; an existing role and channels can be
   linked instead, on adding and on editing. Hidden where the api has no bot in the server. */
import {computed, onMounted, ref, watch} from "vue"
import CheckBox from "@/components/island/CheckBox.vue"
import ChipPicker from "@/components/island/ChipPicker.vue"
import FormField from "@/components/island/FormField.vue"
import FormSection from "@/components/island/FormSection.vue"
import SearchPicker from "@/components/island/SearchPicker.vue"
import {type KeptChannel, type KeptRole, listKeepableChannels, listKeepableRoles} from "@/domains/discord"
import {type CommitteeDiscordRequest, type CommitteeDiscordState, readCommitteeDiscord} from "../adapters/committees"

defineOptions({name: "CommitteeDiscordFields"})

const NEW_ROLE = "__new__"

const {committeeId, name, slug} = defineProps<{
  committeeId: number | null
  name: string
  slug: string
}>()

/** What the form will ask of Discord once the committee is saved, or nothing where Discord is away. */
const choice = defineModel<CommitteeDiscordRequest | null>({default: null})

const state = ref<CommitteeDiscordState | null>(null)
const roles = ref<KeptRole[]>([])
const channels = ref<KeptChannel[]>([])
const roleKey = ref<string | null>(committeeId == null ? NEW_ROLE : null)
const channelIds = ref<string[]>([])
const makeChannel = ref(committeeId == null)

const available = computed(() => (committeeId == null ? roles.value.length > 0 : state.value?.available === true))
const linkedRole = computed(() => state.value?.roleId ?? null)
const roleOptions = computed(() => [
  {key: NEW_ROLE, label: `A new role, @${name.trim() || "the committee"}`},
  ...roles.value.filter((one) => one.assignable).map((one) => ({key: one.id, label: `@${one.name}`})),
])
const asOption = (channel: KeptChannel) => ({key: channel.id, label: channel.name, note: channel.category ?? undefined})
const channelOptions = computed(() => channels.value.filter((one) => one.kind !== "CATEGORY").map(asOption))
const chosenChannels = computed(() => channelIds.value.map((id) => channelOptions.value.find((one) => one.key === id) ?? {key: id, label: id}))
const pickable = computed(() => channelOptions.value.filter((one) => !channelIds.value.includes(one.key)))
const hasRole = computed(() => linkedRole.value != null || roleKey.value != null)

watch([available, roleKey, channelIds, makeChannel, () => slug], () => {
  choice.value = available.value
    ? {
      roleId: linkedRole.value == null && roleKey.value !== NEW_ROLE ? roleKey.value : null,
      createRole: linkedRole.value == null && roleKey.value === NEW_ROLE,
      channelIds: channelIds.value,
      createChannel: hasRole.value && makeChannel.value ? slug.trim() || null : null,
    }
    : null
}, {deep: true, immediate: true})

onMounted(async () => {
  const [read, held, open] = await Promise.all([
    committeeId == null ? Promise.resolve(null) : readCommitteeDiscord(committeeId),
    listKeepableRoles(),
    listKeepableChannels(),
  ])
  state.value = read
  roles.value = held
  channels.value = open
  channelIds.value = (read?.channels ?? []).map((one) => one.id)
})
</script>

<template>
  <form-section
    v-if="available"
    title="Discord"
  >
    <div
      class="committee-discord"
      data-testid="committee-edit-discord"
    >
      <p
        v-if="linkedRole"
        class="committee-discord__role"
        data-testid="committee-edit-discord-role"
      >
        @{{ state?.roleName ?? linkedRole }}<span class="committee-discord__note">Everyone with a seat holds it.</span>
      </p>
      <form-field
        v-else
        label="Role"
        testid="committee-edit-discord-role-field"
      >
        <template #default="{controlId, labelId}">
          <search-picker
            :control-id="controlId"
            :labelled-by="labelId"
            :options="roleOptions"
            placeholder="Link a role"
            :selected-key="roleKey"
            testid-prefix="committee-edit-discord-role-picker"
            @pick="(key: string) => roleKey = key"
          />
        </template>
      </form-field>

      <template v-if="hasRole">
        <form-field
          label="Channels the role opens"
          testid="committee-edit-discord-channels"
        >
          <template #default="{controlId, labelId}">
            <chip-picker
              :chosen="chosenChannels"
              :control-id="controlId"
              empty-note="Every channel is opened already."
              :labelled-by="labelId"
              :options="pickable"
              placeholder="Open a channel to the role"
              :remove-label="(label: string) => `Stop opening #${label}`"
              sigil="#"
              testid-prefix="committee-edit-discord-channel-picker"
              @add="(keys: string[]) => channelIds = [...channelIds, ...keys]"
              @remove="(key: string) => channelIds = channelIds.filter((one) => one !== key)"
            />
          </template>
        </form-field>
        <check-box
          v-model="makeChannel"
          :label="`Make a private channel #${slug.trim() || 'for the committee'}`"
          hint="Under Committees, readable only by the role."
          testid="committee-edit-discord-make-channel"
        />
      </template>
    </div>
  </form-section>
</template>

<style scoped>
.committee-discord {
  display: flex;
  flex-direction: column;
  gap: 0.8rem;
}

.committee-discord__role {
  display: flex;
  flex-wrap: wrap;
  gap: 0.6rem;
  margin: 0;
  font-weight: 600;
}

.committee-discord__note {
  font-weight: 400;
  color: var(--color-ash);
}
</style>
