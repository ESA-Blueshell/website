<script lang="ts" setup>
import {computed, ref, watch} from "vue"
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

type CommitteeFormMember = {
  id?: number
  userId: number | null
  role: string
}

type CommitteeForm = {
  id?: number
  name: string
  description: string
  members: CommitteeFormMember[]
}

const toForm = (ac?: AdvancedCommittee | undefined): CommitteeForm => ({
  id: ac?.id,
  name: ac?.name ?? "",
  description: ac?.description ?? "",
  members: (ac?.members ?? []).map(m => ({
    id: m.id,
    userId: typeof m.userId === "number" ? m.userId : null,
    role: m.role ?? "",
  })),
})

interface Props {
  initialCommittee?: AdvancedCommittee
  users: AdvancedUser[]
}

const props = withDefaults(defineProps<Props>(), {
  initialCommittee: () => ({
    name: "",
    description: "",
    members: [],
  }) as AdvancedCommittee,
})

const emit = defineEmits<{
  (e: "submitting"): void
  (e: "success", ok: boolean): void
}>()

defineRule("committeeUserIsMember", (userId: number | string) => {
  if (!userId && userId !== 0) return "Select a user"
  const u = props.users.find(u => Number(u.id) === Number(userId))
  if (!u) return "Select a user"
  return u.roles?.includes?.(Role.MEMBER) || "Committee members must be members of the association"
})

defineRule("uniqueCommitteeMember", (userId: number | string, [idx]: string[], ctx) => {
  if (!userId && userId !== 0) return true
  const i = Number(idx)
  const arr = ((ctx.form as AdvancedCommittee)?.members ?? []) as Array<CommitteeFormMember>
  const dup = arr.some((m, pos) => pos !== i && Number(m?.userId) === Number(userId))
  return !dup || "Member already in this committee"
})

const formRef = ref<FormContext<CommitteeForm>>()
const submitting = ref(false)
const {apply} = useBackendValidation()

const initialValues = computed<CommitteeForm>(() => toForm(props.initialCommittee))

watch(
  () => props.initialCommittee,
  next => {
    formRef.value?.resetForm({values: toForm(next)})
  },
  {deep: true},
)

function addMember() {
  const values = formRef.value?.values as unknown as CommitteeForm | undefined
  if (!values) return
  values.members.push({role: "", userId: null})
}

function removeMember(index: number) {
  const values = formRef.value?.values as unknown as CommitteeForm | undefined
  if (!values) return
  values.members.splice(index, 1)
}

async function submit() {
  const result = await formRef.value?.validate()
  if (!result?.valid) return

  submitting.value = true
  emit("submitting")
  try {
    const values = formRef.value?.values as unknown as CommitteeForm
    const mappedMembers = (values.members ?? []).map(m => ({
      id: m.id,
      userId: Number(m.userId),
      role: (m.role ?? "").trim(),
      ...(values.id ? {committeeId: values.id as number} : {}),
    }))

    const payload: AdvancedCommittee = {
      ...(values.id ? {id: values.id} : {}),
      name: (values.name ?? "").trim(),
      description: values.description ?? "",
      members: mappedMembers as unknown as CommitteeMember[],
    }

    if (!values.id) {
      const resp = await createCommittee({body: payload})
      if (resp.status === 201) {
        emit("success", true)
        return
      }
      if (!apply(formRef.value!, resp)) $handleNetworkError(resp)
    } else {
      const resp = await updateCommittee({path: {id: values.id}, body: payload})
      if (resp.status === 200) {
        emit("success", true)
        return
      }
      if (!apply(formRef.value!, resp)) $handleNetworkError(resp)
    }
  } catch (err) {
    console.error(err)
    $handleNetworkError(err)
    emit("success", false)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <Form
    ref="formRef"
    v-slot="{ values }"
    as="div"
    :initial-values="initialValues"
  >
    <v-container style="padding: 0;">
      <v-row>
        <v-col cols="12">
          <Field
            v-slot="{ value, errors, handleChange, handleBlur }"
            name="name"
            rules="required|minChars:3|maxChars:100"
          >
            <v-text-field
              :model-value="value"
              label="Committee name"
              :error-messages="errors"
              required
              @update:model-value="handleChange"
              @blur="handleBlur"
            />
          </Field>
        </v-col>
      </v-row>

      <v-row class="mb-8">
        <v-col>
          <Field
            v-slot="{ value, errors, handleChange, handleBlur }"
            name="description"
            rules="required|minChars:10|maxChars:10000"
          >
            <markdown-field
              :model-value="value"
              label="Description"
              :error-messages="errors"
              @update:model-value="handleChange"
              @blur="handleBlur"
            />
          </Field>
        </v-col>
      </v-row>

      <v-container>
        <v-row
          v-for="(member, i) in values.members"
          :key="member.id ?? i"
          dense
        >
          <v-col cols="4">
            <Field
              v-slot="{ value, errors, handleChange, handleBlur }"
              :name="`members[${i}].role`"
              rules="maxChars:120"
            >
              <v-text-field
                label="Role"
                hide-details="auto"
                :model-value="value"
                :error-messages="errors"
                @update:model-value="handleChange"
                @blur="handleBlur"
              />
            </Field>
          </v-col>

          <v-col cols="7">
            <Field
              v-slot="{ value, errors, handleChange, handleBlur }"
              :name="`members[${i}].userId`"
              :rules="`required|committeeUserIsMember|uniqueCommitteeMember:${i}`"
            >
              <user-select
                :users="props.users"
                label="Member name"
                :model-value="value"
                :error-messages="errors"
                @update:model-value="handleChange"
                @blur="handleBlur"
              />
            </Field>
          </v-col>

          <v-col cols="1">
            <v-btn
              icon="mdi-close"
              variant="plain"
              @click="removeMember(i)"
            />
          </v-col>
        </v-row>
      </v-container>

      <v-btn
        block
        class="mb-4"
        variant="outlined"
        @click.prevent="addMember"
      >
        Add member
      </v-btn>
    </v-container>

    <v-row>
      <v-col cols="12">
        <v-btn
          :loading="submitting"
          block
          class="mt-8 mx-auto"
          color="primary"
          @click="submit"
        >
          Save committee
        </v-btn>
      </v-col>
    </v-row>
  </Form>
</template>

<style lang="scss">
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
