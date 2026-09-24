<script lang="ts" setup>
/**
 * The roles an event pings when the bot posts it, picked from the server's own roles. Each chosen
 * role is kept with its name, so while the bot is away the field still says who is pinged, and
 * says it cannot change them.
 */
import {computed, onMounted, ref} from "vue"
import FormField from "@/components/island/FormField.vue"
import SearchPicker from "@/components/island/SearchPicker.vue"
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

const options = computed(() => (roles.value ?? [])
  .filter(role => !chosen.value.some(one => one.id === role.id))
  .map(role => ({key: role.id, label: role.name})))

const add = (id: string) => {
  const role = roles.value?.find(one => one.id === id)
  if (role) emit("update:modelValue", [...chosen.value, {id: role.id, name: role.name}])
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
      <search-picker
        v-if="!unavailable"
        :control-id="controlId"
        :disabled="!loaded"
        empty-note="The server has no roles to ping."
        :labelled-by="labelId"
        :options="options"
        placeholder="Add a role"
        :testid-prefix="`${testid}-picker`"
        @pick="add"
      />
      <ul
        v-if="chosen.length > 0"
        class="pinged-roles"
      >
        <li
          v-for="role in chosen"
          :key="role.id"
          class="pinged-roles__role"
          :data-testid="`${testid}-${role.id}`"
        >
          @{{ role.name }}
          <button
            v-if="!unavailable"
            :aria-label="`Stop pinging ${role.name}`"
            class="pinged-roles__remove"
            type="button"
            @click="remove(role.id)"
          >
            ×
          </button>
        </li>
      </ul>
    </template>
  </form-field>
</template>

<style scoped>
.pinged-roles {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem;
  margin: 0.5rem 0 0;
  padding: 0;
  list-style: none;
}

.pinged-roles__role {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  padding: 0.2rem 0.5rem;
  border-radius: 999px;
  background-color: color-mix(in oklab, var(--color-brand) 18%, transparent);
  font-size: 0.85rem;
}

.pinged-roles__remove {
  border: 0;
  background: none;
  color: inherit;
  cursor: pointer;
  font-size: 1rem;
  line-height: 1;
}
</style>
