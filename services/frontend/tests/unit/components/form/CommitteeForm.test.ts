import {beforeEach, describe, expect, it, vi} from "vitest"
import {nextTick} from "vue"
import {shallowMount} from "@vue/test-utils"
import CommitteeForm from "@/components/form/CommitteeForm.vue"

const {mockStore} = vi.hoisted(() => ({
  mockStore: {
    getters: {
      isLoggedIn: false,
      isBoard: false,
    },
  },
}))

vi.mock("vuex", async (importOriginal) => {
  const actual = await importOriginal<typeof import("vuex")>()
  return {
    ...actual,
    useStore: () => mockStore,
  }
})

const vvFieldStub = {
  name: "VvField",
  props: ["name", "rules"],
  template: "<div class='vv-field-stub' :data-name='name' :data-rules='rules' />",
}
const formStub = {template: "<div><slot /></div>"}

function rulesByName(wrapper: ReturnType<typeof shallowMount>) {
  return Object.fromEntries(
    wrapper
      .findAll(".vv-field-stub")
      .map((field) => [String(field.attributes("data-name")), String(field.attributes("data-rules") ?? "")]),
  )
}

describe("CommitteeForm", () => {
  beforeEach(() => {
    mockStore.getters.isLoggedIn = false
    mockStore.getters.isBoard = false
  })

  it("declares validation rules for committee fields and first member row", () => {
    const wrapper = shallowMount(CommitteeForm, {
      props: {
        users: [{id: 1, roles: ["MEMBER"], fullName: "A"}],
      },
      global: {
        stubs: {
          Form: formStub,
          VvField: vvFieldStub,
        },
      },
    })
    const rules = rulesByName(wrapper)

    expect(rules).toMatchObject({
      name: "required|minChars:3|maxChars:100",
      description: "required|minChars:10|maxChars:10000",
      "members[0].role": "maxChars:120",
      "members[0].userId": "required|committeeUserIsMember|uniqueCommitteeMember:0",
    })
  })

  it("re-indexes member validation rule suffixes when adding members", async () => {
    const wrapper = shallowMount(CommitteeForm, {
      props: {
        users: [{id: 1, roles: ["MEMBER"], fullName: "A"}],
      },
      global: {
        stubs: {
          Form: formStub,
          VvField: vvFieldStub,
        },
      },
    })

    const addButton = wrapper
      .findAll("v-btn")
      .find((button) => String(button.text()).includes("Add member"))
    expect(addButton).toBeTruthy()

    await addButton!.trigger("click")
    await nextTick()

    const rules = rulesByName(wrapper)
    expect(rules).toMatchObject({
      "members[1].userId": "required|committeeUserIsMember|uniqueCommitteeMember:1",
    })
  })
})
