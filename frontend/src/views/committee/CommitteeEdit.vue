<script lang="ts" setup>
import {ref, watch} from "vue"
import {defineRule, Field, Form, type FormContext} from "vee-validate"
import {useBackendValidation} from "@/plugins/serverValidation.ts"
import {$handleNetworkError} from "@/plugins/handleNetworkError.ts"
import MarkdownField from "@/components/MarkdownField.vue"
import UserSelect from "@/components/select/UserSelect.vue"
import {
  type AdvancedCommittee,
  type AdvancedUser,
  type CommitteeMember,
  createCommittee,
  Role,
  updateCommittee,
} from "@/lib"

type Model = AdvancedCommittee

const props = defineProps<{
  modelValue: Model
  users: AdvancedUser[]
}>()

const emit = defineEmits<{
  (e: "update:modelValue", value: Model): void
  (e: "submitting", value: boolean): void
  (e: "saved", value: Model): void
}>()

defineRule("committeeUserIsMember", (userId: number | string, _params, _ctx) => {
  if (!userId && userId !== 0) return "Select a user"
  const u = props.users.find(u => Number(u.id) === Number(userId))
  if (!u) return "Select a user"
  return u.roles?.includes?.(Role.MEMBER) || "Committee members must be members of the association"
})

defineRule("uniqueCommitteeMember", (userId: number, [idx]: string[], ctx) => {
  if (!userId && userId !== 0) return true
  const i = Number(idx)
  const members = (ctx?.form as any)?.members ?? []
  const dup = members.some((m: any, pos: number) => pos !== i && Number(m?.userId) === Number(userId))
  return !dup || "Member already in this committee"
})

const formRef = ref<FormContext>()
const submitting = ref(false)
const {apply} = useBackendValidation()

const committee = ref<Model>(
  props.modelValue ?? ({name: "", description: "", members: []} as Model),
)

watch(
  () => props.modelValue,
  v => {
    committee.value = v ?? ({name: "", description: "", members: []} as Model)
  },
  {deep: true},
)

function addMember() {
  committee.value.members ??= []
  committee.value.members.push({role: "", userId: 1, committeeId: committee.value.id})
}

function removeMember(id) {
  committee.value.members ??= []
  committee.value.members = committee.value.members.filter((m: CommitteeMember) => id !== m.userId)
}

async function submit() {
  const result = await formRef.value?.validate()
  if (!result?.valid) return

  submitting.value = true
  emit("submitting", true)

  try {
    if (!committee.value.id) {
      const resp = await createCommittee({body: committee.value})
      if (resp.status === 201) {
        emit("update:modelValue", resp.data!)
        emit("saved", resp.data!)
        return
      }
      if (!apply(formRef.value!, resp)) $handleNetworkError(resp)
    } else {
      const resp = await updateCommittee({
        path: {id: committee.value.id!},
        body: committee.value,
      })
      if (resp.status === 200) {
        emit("update:modelValue", resp.data!)
        emit("saved", resp.data!)
        return
      }
      if (!apply(formRef.value!, resp)) $handleNetworkError(resp)
    }
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
