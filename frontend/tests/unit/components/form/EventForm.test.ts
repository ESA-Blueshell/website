import {beforeEach, describe, expect, it, vi} from "vitest"
import {shallowMount} from "@vue/test-utils"
import EventForm from "@/components/form/EventForm.vue"
import {settle} from "../../helpers/testUtils"

const {
  mockStore,
  mockFindCommittees,
  mockFindCommitteesByUserId,
} = vi.hoisted(() => ({
  mockStore: {
    getters: {
      isBoard: false,
    },
  },
  mockFindCommittees: vi.fn(),
  mockFindCommitteesByUserId: vi.fn(),
}))

vi.mock("vuex", async (importOriginal) => {
  const actual = await importOriginal<typeof import("vuex")>()
  return {
    ...actual,
    useStore: () => mockStore,
  }
})

vi.mock("@/services/api", () => ({
  createEvent: vi.fn(),
  updateEvent: vi.fn(),
  uploadEventBanner: vi.fn(),
  downloadEventBanner: vi.fn(),
  findCommittees: mockFindCommittees,
  findCommitteesByUserId: mockFindCommitteesByUserId,
}))

const vvFieldStub = {
  name: "VvField",
  props: ["name", "rules"],
  template: "<div class='vv-field-stub' :data-name='name' :data-rules='rules' />",
}
const formStub = {template: "<div><slot /></div>"}

function baseEvent(overrides: Record<string, unknown> = {}) {
  return {
    title: "",
    location: "",
    description: "",
    startTime: "2099-01-01T10:00:00",
    endTime: "2099-01-01T12:00:00",
    memberPrice: 0,
    publicPrice: 0,
    approved: false,
    membersOnly: false,
    signUp: false,
    signUpDeadline: undefined,
    signUpLimit: undefined,
    committeeId: undefined,
    ...overrides,
  }
}

function rulesByName(wrapper: ReturnType<typeof shallowMount>) {
  return Object.fromEntries(
    wrapper
      .findAll(".vv-field-stub")
      .map((field) => [String(field.attributes("data-name")), String(field.attributes("data-rules") ?? "")]),
  )
}

describe("EventForm", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockStore.getters.isBoard = false
    mockFindCommittees.mockResolvedValue({status: 200, data: []})
    mockFindCommitteesByUserId.mockResolvedValue({status: 200, data: []})
  })

  it("declares key validation rules for event creation fields", async () => {
    const wrapper = shallowMount(EventForm, {
      props: {
        modelValue: baseEvent({
          signUp: true,
          signUpDeadline: "2099-01-01T09:00:00",
          signUpForm: {
            questions: [{idx: 0, type: "OPEN", label: "Q"}],
          },
        }),
      },
      global: {
        stubs: {
          Form: formStub,
          VvField: vvFieldStub,
        },
      },
    })
    await settle()
    const rules = rulesByName(wrapper)

    expect(rules).toMatchObject({
      title: "required",
      location: "required",
      description: "required",
      memberPrice: "minValue:0",
      publicPrice: "minValue:0",
      endTime: "required|dateTimeAfter:@startTime",
      committeeId: "required",
      banner: "fileSize",
      signUpForm: "required",
    })
    expect(String(rules.startTime)).toContain("required|dateTimeAfter:")
  })

  it("signUpDeadline and signUpLimit fields absent when signUp is false", async () => {
    const wrapper = shallowMount(EventForm, {
      props: {
        modelValue: baseEvent({signUp: false}),
      },
      global: {
        stubs: {
          Form: formStub,
          VvField: vvFieldStub,
        },
      },
    })
    await settle()
    const rules = rulesByName(wrapper)

    expect(rules.signUpDeadline).toBeUndefined()
    expect(rules.signUpLimit).toBeUndefined()
  })

  it("signUpDeadline and signUpLimit fields present when signUp is true", async () => {
    const wrapper = shallowMount(EventForm, {
      props: {
        modelValue: baseEvent({
          signUp: true,
          signUpDeadline: "2099-01-01T09:00:00",
        }),
      },
      global: {
        stubs: {
          Form: formStub,
          VvField: vvFieldStub,
        },
      },
    })
    await settle()
    const rules = rulesByName(wrapper)

    expect(rules.signUpDeadline).toBe("required|dateTimeNotAfter:@endTime")
    expect(rules.signUpLimit).toBe("minValue:1")
  })

  it("uses plain required start time rule for existing events", async () => {
    const wrapper = shallowMount(EventForm, {
      props: {
        modelValue: baseEvent({id: 33, version: 1}),
      },
      global: {
        stubs: {
          Form: formStub,
          VvField: vvFieldStub,
        },
      },
    })
    await settle()
    expect(rulesByName(wrapper).startTime).toBe("required")
  })

  it("loads committees once via the role-appropriate query", async () => {
    shallowMount(EventForm, {
      props: {
        modelValue: baseEvent(),
      },
      global: {
        stubs: {
          Form: formStub,
          VvField: vvFieldStub,
        },
      },
    })
    await settle()

    expect(mockFindCommitteesByUserId).toHaveBeenCalledTimes(1)
    expect(mockFindCommittees).toHaveBeenCalledTimes(0)
  })

})
