<script lang="ts" setup>
/* A committee's or a team's Discord on its form: the role its people hold and the channels that role
   opens. A new one gets a new role and a private channel by default; an existing role and channels can
   be linked instead, on adding and on editing. Hidden where the api has no bot in the server. */
import RoleMark from "@/components/island/RoleMark.vue"
import {computed, onMounted, ref, watch} from "vue"
import CheckBox from "@/components/island/CheckBox.vue"
import ChipPicker from "@/components/island/ChipPicker.vue"
import FormField from "@/components/island/FormField.vue"
import FormSection from "@/components/island/FormSection.vue"
import SearchPicker from "@/components/island/SearchPicker.vue"
import type {DiscordPlace, DiscordPlaceRequest} from "@/services/api"
import {type CataloguedChannel, listCatalogue} from "../adapters/catalogue"
import {type KeptChannel, type KeptRole, listKeepableRoles} from "../adapters/keeping"
import ChannelMark from "./ChannelMark.vue"
import {readOpenings} from "../adapters/roleOpenings"
import {isArchive} from "../catalogue"

defineOptions({name: "DiscordPlaceFields"})

const NEW_ROLE = "__new__"

const {read, name, slug, holders = "Everyone with a seat holds it.", category = "Committees", testid = "committee-edit-discord"} = defineProps<{
  /** Reads the place as it stands, or nothing where the record is being added. */
  read: (() => Promise<DiscordPlace | null>) | null
  name: string
  slug: string
  /** Who holds the role, as the form says it. */
  holders?: string
  /** The category a channel the site makes goes under. */
  category?: string
  testid?: string
}>()

/** What the form will ask of Discord once the record is saved, or nothing where Discord is away. */
const choice = defineModel<DiscordPlaceRequest | null>({default: null})
const emit = defineEmits<{(event: "loaded", place: DiscordPlace | null): void}>()

const adding = read == null
const state = ref<DiscordPlace | null>(null)
const roles = ref<KeptRole[]>([])
// The catalogue rather than the bare list, since it says which channels are private.
const channels = ref<CataloguedChannel[]>([])
const roleKey = ref<string | null>(adding ? NEW_ROLE : null)
const channelIds = ref<string[]>([])
const makeChannel = ref(adding)

const available = computed(() => (adding ? roles.value.length > 0 : state.value?.available === true))
const linkedRole = computed(() => state.value?.roleId ?? null)
const roleOptions = computed(() => [
  {key: NEW_ROLE, label: `A new role, @${name.trim() || "named after it"}`},
  ...roles.value.filter((one) => one.assignable).map((one) => ({key: one.id, label: `@${one.name}`})),
])
const asOption = (channel: CataloguedChannel) => ({key: channel.id, label: channel.name, note: channel.category ?? undefined})
// An archived channel is kept for history, so it is not offered to link.
const channelOptions = computed(() => channels.value.filter((one: CataloguedChannel) => one.kind !== "CATEGORY" && !isArchive(one.category)).map(asOption))
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

/* A role picked to be linked may have access to channels already. They are filled in and the new
   channel is unticked, so the form shows them and none is linked or made a second time. */
const alreadyOpen = ref<KeptChannel[]>([])
const channelOf = (id: string) => channels.value.find((one) => one.id === id)
watch(roleKey, async (key) => {
  alreadyOpen.value = []
  if (!key || key === NEW_ROLE) return
  const openings = await readOpenings(key)
  if (roleKey.value !== key || !openings) return
  const held = openings.filter((one) => (one.actual ?? one.kept) != null && one.channel.kind !== "CATEGORY").map((one) => one.channel)
  alreadyOpen.value = held
  channelIds.value = [...new Set([...channelIds.value, ...held.map((one) => one.id)])]
  if (held.length > 0) makeChannel.value = false
})

onMounted(async () => {
  const [found, held, open] = await Promise.all([
    read ? read() : Promise.resolve(null),
    listKeepableRoles(),
    listCatalogue(),
  ])
  state.value = found
  emit("loaded", found)
  roles.value = held
  channels.value = open
  channelIds.value = (found?.channels ?? []).map((one) => one.id)
})
</script>

<template>
  <form-section
    v-if="available"
    title="Discord"
  >
    <div
      class="discord-place"
      :data-testid="testid"
    >
      <p
        v-if="linkedRole"
        class="discord-place__role"
        :data-testid="`${testid}-role`"
      >
        <role-mark :role="state?.roleName ?? linkedRole" /><span class="discord-place__note">{{ holders }}</span>
      </p>
      <form-field
        v-else
        label="Role"
        :testid="`${testid}-role-field`"
      >
        <template #default="{controlId, labelId}">
          <search-picker
            :control-id="controlId"
            :labelled-by="labelId"
            :options="roleOptions"
            placeholder="Link a role"
            :selected-key="roleKey"
            :testid-prefix="`${testid}-role-picker`"
            @pick="(key: string) => roleKey = key"
          />
        </template>
      </form-field>

      <template v-if="hasRole">
        <form-field
          label="Channels the role has access to"
          :testid="`${testid}-channels`"
        >
          <template #default="{controlId, labelId}">
            <chip-picker
              :chosen="chosenChannels"
              :control-id="controlId"
              empty-note="The role has access to every channel already."
              :labelled-by="labelId"
              :options="pickable"
              placeholder="Link an existing channel"
              :remove-label="(label: string) => `Remove access to #${label}`"
              sigil="#"
              :testid-prefix="`${testid}-channel-picker`"
              @add="(keys: string[]) => channelIds = [...channelIds, ...keys]"
              @remove="(key: string) => channelIds = channelIds.filter((one) => one !== key)"
            >
              <template #chip="{option}">
                <channel-mark
                  :locked="channelOf(option.key)?.private"
                  :name="option.label"
                  :voice="channelOf(option.key)?.kind === 'VOICE'"
                />
              </template>
              <template #option="{option}">
                <channel-mark
                  :locked="channelOf(option.key)?.private"
                  :name="option.label"
                  :voice="channelOf(option.key)?.kind === 'VOICE'"
                />
              </template>
            </chip-picker>
          </template>
        </form-field>
        <p
          v-if="alreadyOpen.length"
          class="discord-place__already"
          :data-testid="`${testid}-already`"
        >
          This role already has access to <span class="mg-marks discord-place__marks"><channel-mark
            v-for="one in alreadyOpen"
            :key="one.id"
            :locked="channelOf(one.id)?.private"
            :name="one.name"
            :voice="one.kind === 'VOICE'"
          /></span>. These channels are filled in above.
        </p>
        <check-box
          v-model="makeChannel"
          :hint="`Under ${category}, accessible only by the role.`"
          :label="`Create a new private channel #${slug.trim() || 'named after it'}`"
          :testid="`${testid}-make-channel`"
        />
      </template>
    </div>
  </form-section>
</template>

<style scoped>
.discord-place {
  display: flex;
  flex-direction: column;
  gap: 0.8rem;
}

.discord-place__role {
  display: flex;
  flex-wrap: wrap;
  gap: 0.6rem;
  margin: 0;
  font-weight: 600;
}

.discord-place__already {
  font-size: 0.86rem;
  color: var(--color-ash);
}

.discord-place__marks {
  flex-direction: row;
  flex-wrap: wrap;
  gap: 0.3rem;
}

.discord-place__note {
  font-weight: 400;
  color: var(--color-ash);
}
</style>
