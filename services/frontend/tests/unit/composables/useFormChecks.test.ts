import {beforeEach, describe, expect, it, vi} from "vitest"
import {nextTick, reactive} from "vue"
import {parseApiValidation, reportRefusal, useFormChecks} from "@/composables/useFormChecks"
import {minChars, required} from "@/utils/checks"

const {mockNetworkError, mockStatus} = vi.hoisted(() => ({mockNetworkError: vi.fn(), mockStatus: vi.fn()}))
vi.mock("@/plugins/handleNetworkError", () => ({$handleNetworkError: mockNetworkError, $showStatusMessage: mockStatus}))

/** The body the api sends for a Jakarta refusal. */
const refusal = (errors: Array<Record<string, unknown>>, extra: Record<string, unknown> = {}) => ({
  response: {status: 400, data: {title: "Bad Request", status: 400, detail: "Validation failed for request.", errors, ...extra}},
})

const profile = () => {
  const model = reactive({name: "", email: "", start: ""})
  const form = useFormChecks(() => ({
    name: {value: () => model.name, checks: [required, minChars(2)]},
    email: {value: () => model.email, checks: []},
    start: {value: () => model.start, checks: []},
  }))
  return {model, form}
}

describe("a form's checks", () => {
  beforeEach(() => vi.clearAllMocks())

  it("keep a field's failure to themselves until it is left or a save is tried", () => {
    const {model, form} = profile()

    expect(form.valid.value).toBe(false)
    expect(form.errorsOf("name")).toEqual([])
    form.touch("name")
    expect(form.errorsOf("name")).toEqual(["This field is required"])
    model.name = "A"
    expect(form.errorsOf("name")).toEqual(["Must be at least 2 characters"])
    model.name = "Ann"
    expect(form.errorsOf("name")).toEqual([])
    expect(form.valid.value).toBe(true)
  })

  it("show every failure once a save is tried, and answer whether it may go", () => {
    const {model, form} = profile()

    expect(form.attempt()).toBe(false)
    expect(form.errorsOf("name")).toEqual(["This field is required"])
    model.name = "Ann"
    expect(form.attempt()).toBe(true)
  })

  it("put the api's refusal on the field it names, until that field is changed or a save is tried again", async () => {
    const {model, form} = profile()

    const left = form.refuse(refusal([{field: "email", message: "is taken"}]))

    expect(left).toEqual({messages: []})
    expect(form.errorsOf("email")).toEqual(["is taken"])
    expect(form.valid.value).toBe(false)
    model.email = "ann@example.com"
    await nextTick()
    expect(form.errorsOf("email")).toEqual([])

    form.refuse(refusal([{field: "email", message: "is taken"}]))
    form.attempt()
    expect(form.errorsOf("email")).toEqual([])
  })

  it("put a refusal on the fields the form maps it to, and say what lands on no field", () => {
    const {form} = profile()

    const left = form.refuse(refusal([
      {field: "startTime", message: "must be in the future"},
      {field: "memberProfile.country", message: "must not be blank"},
      {field: null, message: "The end cannot precede the start."},
      {field: null},
    ]), {startTime: ["start", "missing"], "memberProfile.country": "country"})

    expect(form.errorsOf("start")).toEqual(["must be in the future"])
    expect(left?.messages).toEqual(["The end cannot precede the start.", "must not be blank"])
  })

  it("leave anything that is not a field refusal alone", () => {
    const {form} = profile()

    expect(form.refuse(new Error("offline"))).toBeNull()
    expect(form.refuse({response: {status: 400, data: {detail: "Nope."}}})).toBeNull()
  })

  it("hold nothing once a save was accepted", () => {
    const {form} = profile()
    form.attempt()
    form.refuse(refusal([{field: "email", message: "is taken"}]))

    form.settle()

    expect(form.errorsOf("name")).toEqual([])
    expect(form.errorsOf("email")).toEqual([])
  })
})

describe("a refused save", () => {
  beforeEach(() => vi.clearAllMocks())

  it("is shown on the form, what lands on no field said out loud, and anything else handed to the network handler", () => {
    const {form} = profile()

    expect(reportRefusal(form, refusal([{field: "email", message: "is taken"}]))).toBe(true)
    expect(mockStatus).not.toHaveBeenCalled()
    expect(reportRefusal(form, refusal([{field: "other", message: "must not be blank"}]))).toBe(true)
    expect(mockStatus).toHaveBeenCalledWith("must not be blank")
    expect(reportRefusal(form, new Error("offline"))).toBe(false)
    expect(mockNetworkError).toHaveBeenCalled()
  })

  it("is read with each field's sentences, the detail and the trace id", () => {
    const parsed = parseApiValidation(refusal([
      {objectName: "createUserRequest", field: "password", message: "must be at least 8 characters"},
      {field: "password", message: "must contain a digit"},
    ], {traceId: "0af7651916cd43dd"}))

    expect(parsed?.fieldErrors.password).toEqual(["must be at least 8 characters", "must contain a digit"])
    expect(parsed).toMatchObject({objectName: "createUserRequest", detail: "Validation failed for request.", status: 400, traceId: "0af7651916cd43dd"})
    expect(parseApiValidation({response: {status: 500, data: {}}})).toBeNull()
  })
})
