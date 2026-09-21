<script lang="ts" setup>
/**
 * The whole listing is fetched once and filtered here: a round trip per keystroke buys
 * nothing over a list the browser already holds.
 */
import {computed, ref} from "vue"
import {firstSaid} from "@/components/form/fields/saidWrong"
import IslandField from "@/components/island/IslandField.vue"
import IslandPicker from "@/components/island/IslandPicker.vue"
import {nameOf, termsFor} from "@/components/form/fields/userTerms"
import {$handleNetworkError} from "@/plugins/handleNetworkError"
import {listUsers, Role, type UserDetailResponse} from "@/domains/user"

const {
  modelValue = undefined,
  label = "User",
  required = false,
  disabled = false,
  membersOnly = false,
  errorMessages = undefined,
  testid = undefined,
} = defineProps<{
  modelValue?: number | undefined
  label?: string
  required?: boolean
  disabled?: boolean
  /** Only a member may be chosen; everybody else is drawn and said to be ineligible. */
  membersOnly?: boolean
  /** What the api or a rule found wrong, in the shape every other field is handed it. */
  errorMessages?: string | string[]
  testid?: string
}>()

const said = computed<string>(() => firstSaid(errorMessages))

const emit = defineEmits<{"update:modelValue": [value: number | undefined]}>()

const people = ref<UserDetailResponse[]>([])
const loading = ref(false)
const loaded = ref(false)

const load = async () => {
  if (loaded.value || loading.value) return
  loading.value = true
  try {
    // No size: this picker filters what it holds, so it wants the whole listing. The 500 it used
    // to name never bounded anything: the answer was everybody regardless (#1145).
    const content = await listUsers()
    people.value = content.slice()
      .sort((a, b) => nameOf(a).localeCompare(nameOf(b)))
    loaded.value = true
  } catch (error) {
    $handleNetworkError(error)
  } finally {
    loading.value = false
  }
}

// The api answers with inherited roles, so a board member carries MEMBER without holding it.
const eligible = (user: UserDetailResponse): boolean =>
  !membersOnly || (user.roles ?? []).includes(Role.MEMBER)

const options = computed(() => people.value.map(one => ({
  key: String(one.id),
  label: nameOf(one),
  note: eligible(one) ? one.email : "Not a member",
  disabled: !eligible(one),
  terms: termsFor(one),
})))

const chosen = computed<string | null>(() =>
  (modelValue == null ? null : String(modelValue)))
</script>

<template>
  <island-field
    :error="said"
    :filled="modelValue != null"
    :label="label"
    :required="required"
    :testid="testid"
    variant="inside"
  >
    <island-picker
      :disabled="disabled"
      empty-note="Type to search people."
      :loading="loading"
      :options="options"
      :selected-key="chosen"
      :testid-prefix="testid ?? 'user-picker'"
      @opened="load"
      @pick="emit('update:modelValue', Number($event))"
    />
  </island-field>
</template>
