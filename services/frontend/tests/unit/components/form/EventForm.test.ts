import {beforeEach, describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"
import EventForm from "@/components/form/EventForm.vue"
import PingedRolePicker from "@/domains/discord/island/PingedRolePicker.vue"
import EventGamesPicker from "@/domains/games/island/EventGamesPicker.vue"
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
  // A plain function, so resetting the mocks between tests leaves its answer alone.
  listDiscordRoles: async () => ({data: [{id: "901", name: "Gamers"}]}),
  findCasualGames: async () => ({data: []}),
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

  it("keeps what each field is given", async () => {
    mockStore.getters.isBoard = true
    const wrapper = mountForm(baseEvent())
    await settle()

    const given: Record<string, unknown> = {
      title: "LAN",
      location: "The Hangar",
      endTime: "2099-01-01T13:00:00",
      committeeId: 3,
      description: "Bring a cable.",
      memberPrice: 5,
      publicPrice: 7.5,
      membersOnly: true,
      approved: true,
      signUp: true,
      enableSignUpForm: true,
    }
    for (const field of wrapper.findAllComponents({name: "VvField"})) {
      const name = String(field.props("name"))
      if (name in given) field.vm.$emit("update:modelValue", given[name])
    }
    const poster = new File(["art"], "poster.png", {type: "image/png"})
    wrapper.findAllComponents({name: "VvField"}).find(field => field.props("name") === "banner")!
      .vm.$emit("update:modelValue", poster)
    await settle()

    const held = modelValuesByName(wrapper)
    for (const [name, value] of Object.entries(given)) expect(held[name], name).toBe(String(value))
    expect((wrapper.vm as any).bannerFile).toBe(poster)
  })

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

  it("offers no archived committee, but keeps the archived one an event already runs under", async () => {
    mockFindCommitteesByUserId.mockResolvedValue({status: 200, data: [{id: 3, name: "Events"}, {id: 4, name: "OldCie", archived: true}, {id: 5, name: "GoneCie", archived: true}]})

    const wrapper = mountForm(baseEvent({committeeId: 5}))
    await settle()

    expect((wrapper.vm as any).committees).toEqual([{id: 3, name: "Events"}, {id: 5, name: "GoneCie"}])
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

  it("sends the roles the event pings, as the picker last chose them", async () => {
    const wrapper = mountForm(baseEvent({committeeId: 1, title: "LAN", pingedRoles: [{id: "901", name: "Gamers"}]}))
    await settle()
    acceptValidation(wrapper)

    await wrapper.findComponent(PingedRolePicker).vm.$emit("update:modelValue", [{id: "902", name: "Racers"}])
    await (wrapper.vm as any).save()

    expect(mockCreateEvent).toHaveBeenCalledWith(expect.objectContaining({
      body: expect.objectContaining({pingedRoles: [{id: "902", name: "Racers"}]}),
    }))
  })

  it("sends the games the event names, as the picker last chose them", async () => {
    const wrapper = mountForm(baseEvent({committeeId: 1, title: "LAN"}))
    await settle()
    acceptValidation(wrapper)

    await wrapper.findComponent(EventGamesPicker).vm.$emit("update:modelValue", ["CHESS"])
    await (wrapper.vm as any).save()

    expect(mockCreateEvent).toHaveBeenCalledWith(expect.objectContaining({body: expect.objectContaining({gameCodes: ["CHESS"]})}))
  })

  it("starts a new event on the committee it was handed", async () => {
    const wrapper = mount(EventForm, {props: {committeeId: 7}, global: {stubs: {VvField: vvFieldStub, Form: formStub, PingedRolePicker: true, EventGamesPicker: true}}})
    await settle()

    expect(wrapper.get(".vv-field-stub[data-name=committeeId]").attributes("data-model-value")).toBe("7")
  })

  it("sends no games for an event the form was handed without them", async () => {
    const wrapper = mountForm(baseEvent({committeeId: 1, title: "LAN", gameCodes: undefined}))
    await settle()
    acceptValidation(wrapper)

    await (wrapper.vm as any).save()

    expect(mockCreateEvent).toHaveBeenCalledWith(expect.objectContaining({body: expect.objectContaining({gameCodes: []})}))
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

  describe("on the island", () => {
    // A form whose fields all pass, so what the island parts are handed is what is under test.
    const checkingFormStub = {
      template: "<div><slot /></div>",
      methods: {validate: () => ({valid: true}), validateField: () => ({valid: true})},
    }

    const mountForm = (props: Record<string, unknown> = {}) => mount(EventForm, {
      props: {modelValue: baseEvent({id: 33, version: 1, signUp: true, signUpCount: 4, signUpDeadline: "2099-01-01T09:00:00", signUpForm: {questions: []}}), ...props},
      global: {stubs: {Form: checkingFormStub, VvField: vvFieldStub, EventPreview: true}},
    })

    it("writes each value into its field and reads each field back into the event", async () => {
      const wrapper = mountForm()
      await settle()
      const handled: unknown[] = []
      const handle = (value: unknown) => handled.push(value)

      for (const field of wrapper.findAllComponents({name: "VvField"})) {
        const {display, update} = (field.vm as any).$attrs as {display?: (v: unknown) => unknown, update?: (v: unknown, h: typeof handle) => unknown}
        display?.(null)
        display?.("2099-01-01T10:00:00")
        await update?.("2099-01-02T10:00", handle)
      }

      expect(handled).toEqual(expect.arrayContaining(["2099-01-02T10:00"]))
    })

    it("says on its save button what it will do, and how the last press went", async () => {
      const created = mountForm({modelValue: undefined})
      await settle()
      const save = () => created.findAllComponents({name: "CutButton"}).find(one => one.attributes("data-testid") === "event-form-submit-btn")!

      expect(save().text()).toBe("Add event")
      expect(save().attributes("data-submit-mode")).toBe("create")
      const vm = created.vm as any
      vm.setSubmitResult(false)
      await settle()
      expect(save().text()).toBe("Check the form")
      vm.setSubmitResult(true)
      await settle()
      expect(save().text()).toBe("Saved")
      vm.isSaving = true
      await settle()
      expect(save().text()).toBe("Saving")

      const edited = mountForm()
      await settle()
      expect(edited.findAllComponents({name: "CutButton"}).find(one => one.attributes("data-testid") === "event-form-submit-btn")!.text()).toBe("Save changes")
    })

    it("tells a committee member their save hides the event until the board approves it", async () => {
      const created = mountForm({modelValue: undefined})
      await settle()
      expect(created.get("[data-testid=event-form-approval-note]").text()).toBe("The event will be hidden until the board approves it")

      const edited = mountForm()
      await settle()
      expect(edited.find("[data-testid=event-form-approval-note]").exists()).toBe(false)
      ;(edited.vm as any).event.title = "Renamed"
      await settle()
      expect(edited.get("[data-testid=event-form-approval-note]").text()).toBe("The event will be hidden until the board re-approves it")
    })

    it("keeps or drops the existing sign-ups on the choice made in the notice", async () => {
      const wrapper = mountForm()
      await settle()
      ;(wrapper.vm as any).event.signUpForm = {questions: [{idx: 0, type: "OPEN", label: "New"}]}
      await settle()

      const choice = wrapper.getComponent({name: "RadioGroup"})
      expect(choice.props("modelValue")).toBe("retain")
      expect(wrapper.text()).toContain("Existing sign-ups will be retained")
      choice.vm.$emit("update:modelValue", "delete")
      await settle()
      expect(wrapper.text()).toContain("Existing sign-ups will be deleted")
      expect((wrapper.vm as any).removeExistingSignUps).toBe(true)
    })

    it("says it is cancelled", async () => {
      const wrapper = mountForm()
      await settle()

      await wrapper.findAllComponents({name: "CutButton"}).find(one => one.attributes("data-testid") === "event-form-cancel-btn")!.trigger("click")

      expect(wrapper.emitted("cancel")).toHaveLength(1)
    })
  })
})
