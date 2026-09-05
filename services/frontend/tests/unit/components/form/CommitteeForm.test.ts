import {beforeEach, describe, expect, it, vi} from "vitest"
import {nextTick} from "vue"
import {mount} from "@vue/test-utils"
import CommitteeForm from "@/components/form/CommitteeForm.vue"
import {settle} from "../../helpers/testUtils"

const {mockStore, mockUpdateCommittee} = vi.hoisted(() => ({
  mockStore: {
    getters: {
      isLoggedIn: false,
      isBoard: false,
    },
  },
  mockUpdateCommittee: vi.fn(),
}))

vi.mock("@/services/api", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/services/api")>()
  return {...actual, updateCommittee: mockUpdateCommittee}
})

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

function rulesByName(wrapper: ReturnType<typeof mount>) {
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
    const wrapper = mount(CommitteeForm, {
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
      "members[0].role": "maxChars:50",
      "members[0].userId": "committeeMemberPicked|uniqueCommitteeMember:0",
    })
  })

  it("re-indexes member validation rule suffixes when adding members", async () => {
    const wrapper = mount(CommitteeForm, {
      global: {
        stubs: {
          Form: formStub,
          VvField: vvFieldStub,
        },
      },
    })

    const addButton = wrapper
      .findAll("button")
      .find((button) => button.text().includes("Add member"))
    expect(addButton).toBeTruthy()

    await addButton!.trigger("click")
    await nextTick()

    const rules = rulesByName(wrapper)
    expect(rules).toMatchObject({
      "members[1].userId": "committeeMemberPicked|uniqueCommitteeMember:1",
    })
  })

  describe("saving an existing committee", () => {
    const committee = {
      id: 5,
      name: "Events",
      description: "A committee with a long enough description",
      version: 1,
      members: [{userId: 7, role: "Chair"}],
    }

    function mountForm() {
      return mount(CommitteeForm, {
        props: {
          modelValue: structuredClone(committee),
          showSubmit: true,
        },
        global: {
          stubs: {
            VTextField: {template: "<input />"},
            MarkdownField: {template: "<textarea />"},
            UserSelect: {template: "<input />"},
            SubmitButton: {template: "<button />"},
            VContainer: {template: "<div><slot /></div>"},
            VRow: {template: "<div><slot /></div>"},
            VCol: {template: "<div><slot /></div>"},
            VBtn: true,
          },
        },
      })
    }

    beforeEach(() => {
      mockUpdateCommittee.mockResolvedValue({data: {...committee, name: "Events"}})
    })

    it("saves a member the form holds nothing but an id for", async () => {
      // The form reads no list of users, so there is nothing here that could find a member
      // absent and refuse on it — whoever the picker wrote in is who gets saved.
      const wrapper = mountForm()
      await settle()

      await (wrapper.vm as any).save()

      expect(mockUpdateCommittee).toHaveBeenCalledTimes(1)
    })

    it("refuses a member row nobody has been picked into", async () => {
      // 0 is what an added row carries until somebody chooses, and `required` reads a
      // number as filled in, so this rule is the one that has to say no.
      const wrapper = mount(CommitteeForm, {
        props: {
          modelValue: {...structuredClone(committee), members: [{userId: 0, role: "Chair"}]},
          showSubmit: true,
        },
        global: {
          stubs: {
            VTextField: {template: "<input />"},
            MarkdownField: {template: "<textarea />"},
            UserSelect: {template: "<input />"},
            SubmitButton: {template: "<button />"},
            VContainer: {template: "<div><slot /></div>"},
            VRow: {template: "<div><slot /></div>"},
            VCol: {template: "<div><slot /></div>"},
            VBtn: true,
          },
        },
      })
      await settle()

      await (wrapper.vm as any).save()

      expect(mockUpdateCommittee).not.toHaveBeenCalled()
    })

    it("saves a member whose roles the browser never read", async () => {
      // Whether somebody is an association member is the api's to judge: it takes the member
      // and gives the seat up when they stop being one.
      const wrapper = mountForm()
      await settle()

      await (wrapper.vm as any).save()

      expect(mockUpdateCommittee).toHaveBeenCalledTimes(1)
    })
  })
})
