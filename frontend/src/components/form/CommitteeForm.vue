<script lang="ts" setup>
import {ref, watch} from "vue"
import {defineRule, Field, Form, type FormContext} from "vee-validate"
import {useBackendValidation} from "@/plugins/serverValidation.ts"
import {$handleNetworkError} from "@/plugins/handleNetworkError.ts"
import MarkdownField from "@/components/form/fields/MarkdownField.vue"
import UserSelect from "@/components/form/fields/UserSelect.vue"
import {
  type AdvancedCommittee,
  type AdvancedUser,
  type CommitteeMember,
  createCommittee,
  Role,
  updateCommittee,
} from "@/services/api"

const props = defineProps<{
  modelValue: AdvancedCommittee
  users: AdvancedUser[]
}>()

const emit = defineEmits<{
  (e: "update:modelValue", value: AdvancedCommittee): void
  (e: "submitting", value: boolean): void
  (e: "saved", value: AdvancedCommittee): void
}>()

defineRule("committeeUserIsMember", (userId: number | string) => {
  if (!userId && userId !== 0) return "Select a user"
  const u = props.users.find(u => Number(u.id) === Number(userId))
  if (!u) return "Select a user"
  return u.roles?.includes?.(Role.MEMBER) || "Committee members must be members of the association"
})

defineRule("uniqueCommitteeMember", (userId: number, [idx]: string[]) => {
  if (!userId && userId !== 0) return true
  const i = Number(idx)
  committee.value.members ??= []
  const dup = committee.value.members.some((m: CommitteeMember, pos: number) => pos !== i && Number(m?.userId) === Number(userId))
  return !dup || "Member already in this committee"
})

const formRef = ref<FormContext>()
const submitting = ref(false)
const {apply} = useBackendValidation()

const committee = ref<AdvancedCommittee>(
  props.modelValue ?? ({name: "", description: "", members: []} as AdvancedCommittee),
)

watch(
  () => props.modelValue,
  v => {
    committee.value = v ?? ({name: "", description: "", members: []} as AdvancedCommittee)
  },
  {deep: true},
)

function addMember() {
  committee.value.members ??= []
  committee.value.members.push({role: "", userId: 1, committeeId: committee.value.id})
}

function removeMember(id: number) {
  committee.value.members ??= []
  committee.value.members = committee.value.members.filter((m: CommitteeMember) => id !== m.userId)
}

async function submit() {
  const result = await formRef.value?.validate()
  if (!result?.valid) return

  submitting.value = true
  emit("submitting", true)

  try {
    let newCommittee: AdvancedCommittee
    if (committee.value.id) {
      const resp = await updateCommittee({path: {id: committee.value.id!}, body: committee.value, throwOnError: true})
      newCommittee = resp.data!
    } else {
      const resp = await createCommittee({body: committee.value, throwOnError: true})
      newCommittee = resp.data!
    }
    emit("submitting", false)
    emit("update:modelValue", newCommittee)
    emit("saved", newCommittee)
  } catch (e: unknown) {
    if (!formRef.value || !apply(formRef.value!, e)) $handleNetworkError(e)
  } finally {
    submitting.value = false
    emit("submitting", false)
  }
}
</script>

<template>
  <Form
    ref="formRef"
    as="div"
  >
    <v-container>
      <v-row class="mt-1">
        <v-col cols="12">
          <Field
            v-slot="{ value, errors, handleChange, handleBlur }"
            v-model="committee.name"
            name="name"
            rules="required|minChars:3|maxChars:100"
          >
            <v-text-field
              :error-messages="errors"
              :model-value="value"
              label="Committee name"
              required
              @blur="handleBlur"
              @update:model-value="handleChange"
            />
          </Field>
        </v-col>
      </v-row>

      <v-row>
        <v-col>
          <Field
            v-slot="{ value, errors, handleChange, handleBlur }"
            v-model="committee.description"
            name="description"
            rules="required|minChars:10|maxChars:10000"
          >
            <markdown-field
              :error-messages="errors"
              :model-value="value"
              label="Description"
              @blur="handleBlur"
              @update:model-value="handleChange"
            />
          </Field>
        </v-col>
      </v-row>

      <v-container>
        <v-row
          v-for="(member, i) in committee.members ?? []"
          :key="member.id ?? i"
          v-model="committee.members[i]"
          class="mt-4"
          dense
        >
          <v-col cols="4">
            <Field
              v-slot="{ value, errors, handleChange, handleBlur }"
              v-model="committee.members[i].role"
              :name="`members[${i}].role`"
              rules="maxChars:120"
            >
              <v-text-field
                :error-messages="errors"
                :model-value="value"
                hide-details="auto"
                label="Role"
                @blur="handleBlur"
                @update:model-value="handleChange"
              />
            </Field>
          </v-col>

          <v-col cols="7">
            <Field
              v-slot="{ value, errors, handleChange, handleBlur }"
              v-model="committee.members[i].userId"
              :name="`members[${i}].userId`"
              :rules="`required|committeeUserIsMember|uniqueCommitteeMember:${i}`"
            >
              <user-select
                :error-messages="errors"
                :model-value="value"
                :users="props.users"
                label="Member name"
                @blur="handleBlur"
                @update:model-value="handleChange"
              />
            </Field>
          </v-col>

          <v-col cols="1">
            <v-btn
              icon="mdi-close"
              variant="plain"
              @click="removeMember(committee.members[i].userId)"
            />
          </v-col>
        </v-row>

        <v-btn
          block
          class="mt-4"
          variant="outlined"
          @click.prevent="addMember()"
        >
          Add member
        </v-btn>
      </v-container>
    </v-container>
    <v-row class="mb-1">
      <v-col cols="12">
        <v-btn
          :loading="submitting"
          block
          class="mx-auto"
          color="primary"
          @click="submit"
        >
          Save committee
        </v-btn>
      </v-col>
    </v-row>
  </Form>
</template>

<style lang="scss" scoped>
.v-col:first-child {
  padding-left: 0;
}

.v-col:last-child {
  padding-right: 0;
}

.v-col {
  padding-bottom: 0;
  padding-top: 0;
}

.text-error {
  color: rgb(var(--v-theme-error));
}
</style>
