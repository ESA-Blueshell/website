import {beforeEach, describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"
import EventForm from "@/components/form/EventForm.vue"
import {settle} from "../../helpers/testUtils"

const {
  mockStore,
  mockFindCommittees,
  mockFindCommitteesByUserId,
  mockCreateEvent,
  mockUpdateEvent,
  mockUploadEventBanner,
  mockDownloadEventBanner,
} = vi.hoisted(() => ({
  mockCreateEvent: vi.fn(),
  mockUpdateEvent: vi.fn(),
  mockUploadEventBanner: vi.fn(),
  mockDownloadEventBanner: vi.fn(),
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
  createEvent: mockCreateEvent,
  updateEvent: mockUpdateEvent,
  uploadEventBanner: mockUploadEventBanner,
  downloadEventBanner: mockDownloadEventBanner,
  findCommittees: mockFindCommittees,
  findCommitteesByUserId: mockFindCommitteesByUserId,
}))

const vvFieldStub = {
  name: "VvField",
  props: ["name", "rules", "modelValue"],
  template: "<div class='vv-field-stub' :data-name='name' :data-rules='rules' :data-model-value='String(modelValue ?? \"\")' />",
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

function rulesByName(wrapper: ReturnType<typeof mount>) {
  return Object.fromEntries(
    wrapper
      .findAll(".vv-field-stub")
      .map((field) => [String(field.attributes("data-name")), String(field.attributes("data-rules") ?? "")]),
  )
}

function modelValuesByName(wrapper: ReturnType<typeof mount>) {
  return Object.fromEntries(
    wrapper
      .findAll(".vv-field-stub")
      .map((field) => [String(field.attributes("data-name")), field.attributes("data-model-value")]),
  )
}

describe("EventForm", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockStore.getters.isBoard = false
    mockFindCommittees.mockResolvedValue({status: 200, data: []})
    mockFindCommitteesByUserId.mockResolvedValue({status: 200, data: []})
    mockCreateEvent.mockResolvedValue({data: {id: 70, title: "LAN"}})
    mockUpdateEvent.mockResolvedValue({data: {id: 33, title: "LAN", version: 2}})
    mockUploadEventBanner.mockResolvedValue({data: {id: 9}})
    mockDownloadEventBanner.mockResolvedValue({data: new Blob(["art"], {type: "image/webp"})})
  })

  it("declares key validation rules for event creation fields", async () => {
    const wrapper = mount(EventForm, {
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
    const wrapper = mount(EventForm, {
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
    const wrapper = mount(EventForm, {
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
    const wrapper = mount(EventForm, {
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

  it("signUpDeadline follows startTime when it equals the previous startTime", async () => {
    const wrapper = mount(EventForm, {
      props: {
        modelValue: baseEvent({
          signUp: true,
          startTime: "2099-01-01T10:00:00",
          signUpDeadline: "2099-01-01T10:00:00",
        }),
      },
      global: {stubs: {Form: formStub, VvField: vvFieldStub}},
    })
    await settle()

    const startTimeField = wrapper.findAllComponents(vvFieldStub).find((c) => c.props("name") === "startTime")
    await startTimeField?.vm.$emit("update:modelValue", "2099-06-01T10:00:00")
    await settle()

    expect(modelValuesByName(wrapper).signUpDeadline).toBe("2099-06-01T10:00:00")
  })

  it("signUpDeadline does not follow startTime when it has a custom value", async () => {
    const wrapper = mount(EventForm, {
      props: {
        modelValue: baseEvent({
          signUp: true,
          startTime: "2099-01-01T10:00:00",
          signUpDeadline: "2099-01-01T08:00:00",
        }),
      },
      global: {stubs: {Form: formStub, VvField: vvFieldStub}},
    })
    await settle()

    const startTimeField = wrapper.findAllComponents(vvFieldStub).find((c) => c.props("name") === "startTime")
    await startTimeField?.vm.$emit("update:modelValue", "2099-06-01T10:00:00")
    await settle()

    expect(modelValuesByName(wrapper).signUpDeadline).toBe("2099-01-01T08:00:00")
  })

  it("endTime date updates when startTime date changes", async () => {
    const wrapper = mount(EventForm, {
      props: {
        modelValue: baseEvent({
          startTime: "2099-01-01T10:00:00",
          endTime: "2099-01-01T12:00:00",
        }),
      },
      global: {stubs: {Form: formStub, VvField: vvFieldStub}},
    })
    await settle()

    const startTimeField = wrapper.findAllComponents(vvFieldStub).find((c) => c.props("name") === "startTime")
    await startTimeField?.vm.$emit("update:modelValue", "2099-03-15T10:00:00")
    await settle()

    expect(modelValuesByName(wrapper).endTime).toBe("2099-03-15T12:00:00")
  })

  it("endTime date does not update when only startTime time changes", async () => {
    const wrapper = mount(EventForm, {
      props: {
        modelValue: baseEvent({
          startTime: "2099-01-01T10:00:00",
          endTime: "2099-01-01T12:00:00",
        }),
      },
      global: {stubs: {Form: formStub, VvField: vvFieldStub}},
    })
    await settle()

    const startTimeField = wrapper.findAllComponents(vvFieldStub).find((c) => c.props("name") === "startTime")
    await startTimeField?.vm.$emit("update:modelValue", "2099-01-01T11:00:00")
    await settle()

    expect(modelValuesByName(wrapper).endTime).toBe("2099-01-01T12:00:00")
  })

  it("loads committees once via the role-appropriate query", async () => {
    mount(EventForm, {
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

  const mountForm = (modelValue: Record<string, unknown>) => mount(EventForm, {
    props: {modelValue},
    global: {stubs: {Form: formStub, VvField: vvFieldStub}},
  })

  const acceptValidation = (wrapper: ReturnType<typeof mount>) => {
    (wrapper.vm as any).formRef = {validate: vi.fn().mockResolvedValue({valid: true})}
  }

  it("reads the art an existing event already carries back into the field", async () => {
    const wrapper = mountForm(baseEvent({id: 33, version: 1, banner: {fileId: 4, version: 0}}))
    await settle()

    expect(mockDownloadEventBanner).toHaveBeenCalledWith({
      path: {eventId: 33},
      throwOnError: true,
      responseType: "blob",
    })
    expect((wrapper.vm as any).bannerFile?.name).toBe("event-banner-33")
    expect((wrapper.vm as any).bannerDirty).toBe(false)
  })

  it("asks for every committee where the reader is board", async () => {
    mockStore.getters.isBoard = true
    mockFindCommittees.mockResolvedValue({status: 200, data: [{id: 1, name: "Board"}]})

    const wrapper = mountForm(baseEvent())
    await settle()

    expect(mockFindCommittees).toHaveBeenCalled()
    expect(mockFindCommitteesByUserId).not.toHaveBeenCalled()
    expect((wrapper.vm as any).committees).toEqual([{id: 1, name: "Board"}])
  })

  it("keeps an unnamed committee out of the choice", async () => {
    mockFindCommitteesByUserId.mockResolvedValue({status: 200, data: [{id: 2}, {id: 3, name: "Events"}]})

    const wrapper = mountForm(baseEvent())
    await settle()

    expect((wrapper.vm as any).committees).toEqual([{id: 3, name: "Events"}])
  })

  it("reports a committee read the api refused", async () => {
    mockFindCommitteesByUserId.mockRejectedValue(new Error("refused"))

    const wrapper = mountForm(baseEvent())
    await settle()

    expect((wrapper.vm as any).committees).toEqual([])
  })

  it("stores a newly chosen banner and records the event with the file it became", async () => {
    const wrapper = mountForm(baseEvent({committeeId: 1, title: "LAN"}))
    await settle()
    acceptValidation(wrapper)

    ;(wrapper.vm as any).bannerFile = new File(["bytes"], "banner.webp")
    ;(wrapper.vm as any).bannerDirty = true

    await (wrapper.vm as any).save()

    expect(mockUploadEventBanner).toHaveBeenCalled()
    expect(mockCreateEvent).toHaveBeenCalledWith(expect.objectContaining({
      body: expect.objectContaining({banner: {fileId: 9, version: undefined}}),
      throwOnError: true,
    }))
    expect(wrapper.emitted("submitted")?.at(-1)).toEqual([true])
  })

  it("leaves the stored banner alone where the file it became has not changed", async () => {
    const wrapper = mountForm(baseEvent({id: 33, version: 1, committeeId: 1, banner: {fileId: 9, version: 3}}))
    await settle()
    acceptValidation(wrapper)

    ;(wrapper.vm as any).bannerFile = new File(["bytes"], "banner.webp")
    ;(wrapper.vm as any).bannerDirty = true

    await (wrapper.vm as any).save()

    expect(mockUpdateEvent).toHaveBeenCalledWith(expect.objectContaining({
      path: {id: 33},
      body: expect.objectContaining({banner: {fileId: 9, version: 3}, version: 1}),
      throwOnError: true,
    }))
  })

  it("takes the banner off the event where the reader cleared the field", async () => {
    const wrapper = mountForm(baseEvent({committeeId: 1, banner: {fileId: 9, version: 0}}))
    await settle()
    acceptValidation(wrapper)

    ;(wrapper.vm as any).bannerFile = null
    ;(wrapper.vm as any).bannerDirty = true

    await (wrapper.vm as any).save()

    expect(mockUploadEventBanner).not.toHaveBeenCalled()
    expect(mockCreateEvent).toHaveBeenCalledWith(expect.objectContaining({
      body: expect.objectContaining({banner: undefined}),
      throwOnError: true,
    }))
  })

  it("reports a save the api refused", async () => {
    mockCreateEvent.mockRejectedValue(new Error("refused"))
    const wrapper = mountForm(baseEvent({committeeId: 1}))
    await settle()
    acceptValidation(wrapper)

    await (wrapper.vm as any).save()

    expect(wrapper.emitted("submitted")?.at(-1)).toEqual([false])
  })
})
