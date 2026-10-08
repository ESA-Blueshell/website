import {beforeEach, describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"
import EventForm from "@/components/form/EventForm.vue"
import PingedRolePicker from "@/domains/discord/island/PingedRolePicker.vue"
import EventGamesPicker from "@/domains/games/island/EventGamesPicker.vue"
import {settle} from "../../helpers/testUtils"
import {clearEveryField, saidByLabel} from "../../helpers/fields"

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

vi.mock("@/services/api", async (importOriginal) => ({
  ...await importOriginal<typeof import("@/services/api")>(),
  createEvent: mockCreateEvent,
  updateEvent: mockUpdateEvent,
  uploadEventBanner: mockUploadEventBanner,
  downloadEventBanner: mockDownloadEventBanner,
  findCommittees: mockFindCommittees,
  findCommitteesByUserId: mockFindCommitteesByUserId,
  // A plain function, so resetting the mocks between tests leaves its answer alone.
  listDiscordRoles: async () => ({data: [{id: "901", name: "Gamers"}]}),
  findCasualGames: async () => ({data: []}),
  listDiscordEmojis: async () => ({data: []}),
}))

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

/** An event every check lets through, so a test is about what happens after them. */
const validEvent = (overrides: Record<string, unknown> = {}) =>
  baseEvent({title: "LAN", location: "The Hangar", description: "Bring a cable.", committeeId: 1, ...overrides})

const fieldLabelled = (wrapper: ReturnType<typeof mount>, label: string) =>
  wrapper.findAllComponents({name: "FormControl"}).find(field => field.props("label") === label)

const tickLabelled = (wrapper: ReturnType<typeof mount>, label: string) =>
  wrapper.findAllComponents({name: "CheckBox"}).find(box => box.props("label") === label)!

const errorsOf = (wrapper: ReturnType<typeof mount>, label: string) => fieldLabelled(wrapper, label)!.props("errorMessages")

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

  const mountForm = (modelValue: Record<string, unknown>) => mount(EventForm, {
    props: {modelValue},
    global: {stubs: {AnnounceDialog: {name: "AnnounceDialog", props: ["open", "later"], emits: ["answer"], template: "<div />"}}},
  })

  const tryToSave = async (wrapper: ReturnType<typeof mount>) => {
    expect(await (wrapper.vm as any).validate()).toBe(false)
    await settle()
  }

  it("asks for a name, a place, a committee and a description, and no price below zero", async () => {
    const wrapper = mountForm(baseEvent({memberPrice: -1}))
    await settle()

    await tryToSave(wrapper)

    expect(errorsOf(wrapper, "Event name*")).toEqual(["This field is required"])
    expect(errorsOf(wrapper, "Location*")).toEqual(["This field is required"])
    expect(errorsOf(wrapper, "Description*")).toEqual(["This field is required"])
    expect(wrapper.getComponent({name: "CommitteePicker"}).props("errorMessages")).toEqual(["This field is required"])
    expect(errorsOf(wrapper, "Price for members")).toEqual(["Must be at least 0"])
    expect(errorsOf(wrapper, "Price for non-members")).toEqual([])
  })

  it("starts a new event after now and ends it after it starts, but lets an existing one keep its start", async () => {
    const created = mountForm(validEvent({startTime: "2000-01-01T10:00:00", endTime: "2000-01-01T09:00:00"}))
    await settle()
    await tryToSave(created)

    expect(errorsOf(created, "Starts*")[0]).toMatch(/^Must be after /)
    expect(errorsOf(created, "Ends*")).toEqual(["Must be after 01/01/2000 10:00"])

    const existing = mountForm(validEvent({id: 33, version: 1, startTime: "2000-01-01T10:00:00", endTime: "2000-01-01T12:00:00"}))
    await settle()
    expect(await (existing.vm as any).validate()).toBe(true)
  })

  it("asks when sign-ups close only where sign-ups are allowed, never after the event ends, and a limit of one or more", async () => {
    const closed = mountForm(validEvent({signUp: false}))
    await settle()
    expect(fieldLabelled(closed, "Sign-ups close*")).toBeUndefined()
    expect(fieldLabelled(closed, "Sign-up limit")).toBeUndefined()

    const open = mountForm(validEvent({signUp: true, signUpDeadline: "2099-01-01T13:00:00", signUpLimit: 0}))
    await settle()
    await tryToSave(open)

    expect(errorsOf(open, "Sign-ups close*")).toEqual(["Must be before or on 01/01/2099 12:00"])
    expect(errorsOf(open, "Sign-up limit")).toEqual(["Must be at least 1"])
  })

  it("checks the questions of its sign-up form", async () => {
    const wrapper = mountForm(validEvent({signUp: true, signUpDeadline: "2099-01-01T09:00:00", signUpForm: {questions: [{idx: 0, type: "OPEN", label: ""}]}}))
    await settle()

    await tryToSave(wrapper)

    expect(wrapper.getComponent({name: "QuestionEditor"}).text()).toContain("This field is required")
  })

  it("puts the api's refusal on the field it names", async () => {
    mockCreateEvent.mockRejectedValue({response: {status: 400, data: {errors: [{field: "location", message: "is not a room we can book"}]}}})
    const wrapper = mountForm(validEvent())
    await settle()

    await (wrapper.vm as any).save()
    await settle()

    expect(errorsOf(wrapper, "Location*")).toEqual(["is not a room we can book"])
  })

  it("shows each moment as the calendar reads it, and writes what is typed back as a full moment", async () => {
    const wrapper = mountForm(validEvent())
    await settle()

    expect(fieldLabelled(wrapper, "Starts*")!.props("modelValue")).toBe("2099-01-01T10:00")
    fieldLabelled(wrapper, "Ends*")!.vm.$emit("update:modelValue", "2099-01-01T13:30")
    await settle()

    expect((wrapper.vm as any).event.endTime).toMatch(/^2099-01-01T13:30/)
  })

  it("signUpDeadline follows startTime when it equals the previous startTime", async () => {
    const wrapper = mountForm(baseEvent({signUp: true, startTime: "2099-01-01T10:00:00", signUpDeadline: "2099-01-01T10:00:00"}))
    await settle()

    fieldLabelled(wrapper, "Starts*")!.vm.$emit("update:modelValue", "2099-06-01T10:00")
    await settle()

    expect(fieldLabelled(wrapper, "Sign-ups close*")!.props("modelValue")).toBe("2099-06-01T10:00")
  })

  it("signUpDeadline does not follow startTime when it has a custom value", async () => {
    const wrapper = mountForm(baseEvent({signUp: true, startTime: "2099-01-01T10:00:00", signUpDeadline: "2099-01-01T08:00:00"}))
    await settle()

    fieldLabelled(wrapper, "Starts*")!.vm.$emit("update:modelValue", "2099-06-01T10:00")
    await settle()

    expect(fieldLabelled(wrapper, "Sign-ups close*")!.props("modelValue")).toBe("2099-01-01T08:00")
  })

  it("endTime date updates when startTime date changes", async () => {
    const wrapper = mountForm(baseEvent({startTime: "2099-01-01T10:00:00", endTime: "2099-01-01T12:00:00"}))
    await settle()

    fieldLabelled(wrapper, "Starts*")!.vm.$emit("update:modelValue", "2099-03-15T10:00")
    await settle()

    expect(fieldLabelled(wrapper, "Ends*")!.props("modelValue")).toBe("2099-03-15T12:00")
  })

  it("endTime date does not update when only startTime time changes", async () => {
    const wrapper = mountForm(baseEvent({startTime: "2099-01-01T10:00:00", endTime: "2099-01-01T12:00:00"}))
    await settle()

    fieldLabelled(wrapper, "Starts*")!.vm.$emit("update:modelValue", "2099-01-01T11:00")
    await settle()

    expect(fieldLabelled(wrapper, "Ends*")!.props("modelValue")).toBe("2099-01-01T12:00")
  })

  it("loads committees once via the role-appropriate query", async () => {
    mountForm(baseEvent())
    await settle()

    expect(mockFindCommitteesByUserId).toHaveBeenCalledTimes(1)
    expect(mockFindCommittees).toHaveBeenCalledTimes(0)
  })

  it("keeps what each field is given", async () => {
    mockStore.getters.isBoard = true
    const wrapper = mountForm(baseEvent())
    await settle()

    const typed: Record<string, string> = {
      "Event name*": "LAN",
      "Location*": "The Hangar",
      "Description*": "Bring a cable.",
      "Price for members": "5",
      "Price for non-members": "7.50",
    }
    for (const [label, value] of Object.entries(typed)) fieldLabelled(wrapper, label)!.vm.$emit("update:modelValue", value)
    wrapper.getComponent({name: "CommitteePicker"}).vm.$emit("update:modelValue", 3)
    for (const label of ["Members only", "Approved", "Allow sign-ups", "Add a sign-up form"]) tickLabelled(wrapper, label).vm.$emit("update:modelValue", true)
    await settle()

    expect((wrapper.vm as any).event).toMatchObject({
      title: "LAN",
      location: "The Hangar",
      description: "Bring a cable.",
      memberPrice: "5",
      publicPrice: "7.50",
      committeeId: 3,
      membersOnly: true,
      approved: true,
      signUp: true,
    })
    expect((wrapper.vm as any).enableSignUpForm).toBe(true)
  })

  it("holds a chosen poster, shown in the picture input, until the event is saved", async () => {
    const wrapper = mountForm(baseEvent())
    await settle()
    const input = wrapper.findComponent({name: "ImagePicker"})
    const poster = new File(["art"], "poster.gif", {type: "image/gif"})

    const stored = await input.props("store")(poster)
    input.vm.$emit("update:picture", stored.ok ? stored.saved : null)
    await settle()

    expect(mockUploadEventBanner).not.toHaveBeenCalled()
    expect((wrapper.vm as any).bannerFile).toBe(poster)
    expect((wrapper.vm as any).bannerDirty).toBe(true)
    expect(input.props("picture")?.url).toMatch(/^blob:/)
    expect(input.props("mayBeAnimated")).toBe(true)
    expect(input.props("shape")).toBe("poster")
  })

  it("refuses a poster over 10 MB and takes one off when it is cleared", async () => {
    const wrapper = mountForm(baseEvent())
    await settle()
    const input = wrapper.findComponent({name: "ImagePicker"})
    const big = new File(["x"], "big.png", {type: "image/png"})
    Object.defineProperty(big, "size", {value: 11 * 1024 * 1024})

    expect(await input.props("store")(big)).toEqual({ok: false, reason: "A poster is at most 10 MB."})

    ;(wrapper.vm as any).bannerFile = new File(["art"], "poster.png", {type: "image/png"})
    await settle()
    input.vm.$emit("update:picture", null)
    await settle()

    expect((wrapper.vm as any).bannerFile).toBeNull()
    expect(input.props("picture")).toBeNull()
    wrapper.unmount()
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
    expect(wrapper.findComponent({name: "ImagePicker"}).props("picture")?.url).toMatch(/^blob:/)
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
    const wrapper = mountForm(validEvent())
    await settle()

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
    const wrapper = mountForm(validEvent({pingedRoles: [{id: "901", name: "Gamers"}]}))
    await settle()

    await wrapper.findComponent(PingedRolePicker).vm.$emit("update:modelValue", [{id: "902", name: "Racers"}])
    await (wrapper.vm as any).save()

    expect(mockCreateEvent).toHaveBeenCalledWith(expect.objectContaining({
      body: expect.objectContaining({pingedRoles: [{id: "902", name: "Racers"}]}),
    }))
  })

  it("sends the games the event names, as the picker last chose them", async () => {
    const wrapper = mountForm(validEvent())
    await settle()

    await wrapper.findComponent(EventGamesPicker).vm.$emit("update:modelValue", ["CHESS"])
    await (wrapper.vm as any).save()

    expect(mockCreateEvent).toHaveBeenCalledWith(expect.objectContaining({body: expect.objectContaining({gameCodes: ["CHESS"]})}))
  })

  it("asks the board when the events-info post goes out on a save that approves, and saves nothing on cancel", async () => {
    mockStore.getters.isBoard = true
    const wrapper = mountForm(validEvent({approved: true}))
    await settle()
    const dialog = () => wrapper.getComponent({name: "AnnounceDialog"})

    const cancelled = (wrapper.vm as any).save()
    await settle()
    expect(dialog().props("open")).toBe(true)
    dialog().vm.$emit("answer", null)
    await cancelled
    expect(mockCreateEvent).not.toHaveBeenCalled()

    const saved = (wrapper.vm as any).save()
    await settle()
    dialog().vm.$emit("answer", "NEXT_MORNING")
    await saved
    expect(mockCreateEvent).toHaveBeenCalledWith(expect.objectContaining({body: expect.objectContaining({approved: true, announce: "NEXT_MORNING"})}))
  })

  it("asks nothing on a save that keeps an approved event approved, or where the post is out", async () => {
    mockStore.getters.isBoard = true
    const approved = mountForm(validEvent({id: 33, version: 1, approved: true}))
    await settle()
    await (approved.vm as any).save()
    expect(mockUpdateEvent.mock.lastCall?.[0].body.announce).toBeUndefined()

    const announced = mountForm(validEvent({id: 33, version: 1, approved: false, announced: true}))
    await settle()
    ;(announced.vm as any).event.approved = true
    await (announced.vm as any).save()
    expect(mockUpdateEvent.mock.lastCall?.[0].body).toEqual(expect.objectContaining({approved: true}))
    expect(mockUpdateEvent.mock.lastCall?.[0].body.announce).toBeUndefined()
  })

  it("starts a new event on the committee it was handed", async () => {
    const wrapper = mount(EventForm, {props: {committeeId: 7}, global: {stubs: {PingedRolePicker: true, EventGamesPicker: true}}})
    await settle()

    expect(wrapper.getComponent({name: "CommitteePicker"}).props("modelValue")).toBe(7)
  })

  it("sends no games for an event the form was handed without them", async () => {
    const wrapper = mountForm(validEvent({gameCodes: undefined}))
    await settle()

    await (wrapper.vm as any).save()

    expect(mockCreateEvent).toHaveBeenCalledWith(expect.objectContaining({body: expect.objectContaining({gameCodes: []})}))
  })

  it("leaves the stored banner alone where the file it became has not changed", async () => {
    const wrapper = mountForm(validEvent({id: 33, version: 1, banner: {fileId: 9, version: 3}}))
    await settle()

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
    const wrapper = mountForm(validEvent({banner: {fileId: 9, version: 0}}))
    await settle()

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
    const wrapper = mountForm(validEvent())
    await settle()

    await (wrapper.vm as any).save()

    expect(wrapper.emitted("submitted")?.at(-1)).toEqual([false])
  })

  describe("on the island", () => {
    const mountForm = (props: Record<string, unknown> = {}) => mount(EventForm, {
      props: {modelValue: baseEvent({id: 33, version: 1, signUp: true, signUpCount: 4, signUpDeadline: "2099-01-01T09:00:00", signUpForm: {questions: []}}), ...props},
      global: {stubs: {EventPreview: true}},
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

      const approved = mountForm({modelValue: baseEvent({id: 33, version: 1, approved: true})})
      await settle()
      ;(approved.vm as any).event.title = "Renamed"
      await settle()
      expect(approved.get("[data-testid=event-form-approval-note]").text())
        .toBe("The event will be hidden until the board re-approves it, and what the bot has out on Discord stays as it is until then")
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

  it("says a field left empty is required once it is left, and takes the sign-up form as it is built", async () => {
    const wrapper = mountForm(validEvent({signUp: true, signUpDeadline: "2099-01-01T09:00:00", signUpForm: {questions: [{idx: 0, type: "OPEN", label: "Q"}]}}))
    await settle()

    await clearEveryField(wrapper)

    expect(saidByLabel(wrapper)).toMatchObject({
      "Event name*": ["This field is required"],
      "Location*": ["This field is required"],
      "Starts*": ["This field is required"],
      "Ends*": ["This field is required"],
      "Description*": ["This field is required"],
      "Sign-ups close*": ["This field is required"],
      "Sign-up limit": [],
      "Question text*": ["This field is required"],
    })

    const built = {questions: [{idx: 0, type: "OPEN", label: "Anything else?"}]}
    wrapper.getComponent({name: "SurveyForm"}).vm.$emit("update:modelValue", built)
    await settle()
    expect((wrapper.vm as any).event.signUpForm).toEqual(built)
  })
})
