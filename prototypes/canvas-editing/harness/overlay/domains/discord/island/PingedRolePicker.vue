<script lang="ts" setup>
/**
 * The roles an event pings when the bot posts it, picked from the server's own roles. Each chosen
 * role is kept with its name, so while the bot is away the field still says who is pinged, and
 * says it cannot change them.
 */
import {computed, onMounted, ref} from "vue"
import ChipPicker from "@/components/island/ChipPicker.vue"
import FormField from "@/components/island/FormField.vue"
import {type DiscordRoleResponse, listServerRoles, type PingedRoleRequest} from "../index"

const props = withDefaults(defineProps<{
  modelValue?: PingedRoleRequest[] | null
  label?: string
  testid?: string
}>(), {
  modelValue: () => [],
  label: "Roles to ping",
  testid: "pinged-roles",
})

const emit = defineEmits<{"update:modelValue": [roles: PingedRoleRequest[]]}>()

const roles = ref<DiscordRoleResponse[] | null>(null)
const loaded = ref(false)
onMounted(async () => {
  roles.value = await listServerRoles()
  loaded.value = true
})

const chosen = computed<PingedRoleRequest[]>(() => props.modelValue ?? [])
const unavailable = computed<boolean>(() => loaded.value && roles.value === null)

const options = computed(() => (roles.value ?? []).map(role => ({key: role.id, label: role.name})))
const chips = computed(() => chosen.value.map(role => ({key: role.id, label: role.name})))

const add = (ids: string[]) => {
  const picked = ids
    .map(id => roles.value?.find(one => one.id === id))
    .filter(role => role !== undefined)
    .map(role => ({id: role.id, name: role.name}))
  if (picked.length > 0) emit("update:modelValue", [...chosen.value, ...picked])
}

const remove = (id: string) => emit("update:modelValue", chosen.value.filter(one => one.id !== id))
</script>

<template>
  <form-field
    :hint="unavailable ? 'The Discord role list is unavailable right now, so these cannot change.' : undefined"
    :label="label"
    :testid="testid"
  >
    <template #default="{controlId, labelId}">
      <chip-picker
        :chip-testid="(id: string) => `${testid}-${id}`"
        :chosen="chips"
        :control-id="controlId"
        :disabled="!loaded || unavailable"
        empty-note="Every role is pinged already."
        :labelled-by="labelId"
        :options="options"
        placeholder="Add a role"
        :remove-label="(name: string) => `Stop pinging ${name}`"
        sigil="@"
        :testid-prefix="`${testid}-picker`"
        @add="add"
        @remove="remove"
      />
    </template>
  </form-field>
</template>
