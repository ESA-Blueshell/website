import {describe, expect, it, vi} from "vitest"
import {apply, parseApiValidation} from "@/plugins/validation"

/**
 * The body the api actually sends: Spring's ProblemDetail with the binding result attached,
 * which is what `ValidationProblemDetailsAdvice` builds out of Jakarta's field errors.
 */
const refusal = (errors: Array<Record<string, unknown>>, extra: Record<string, unknown> = {}) => ({
  response: {
    status: 400,
    data: {
      type: "about:blank",
      title: "Bad Request",
      status: 400,
      detail: "Validation failed for request.",
      instance: "/api/users",
      errors,
      ...extra,
    },
  },
})

const formOn = (values: Record<string, unknown>) => ({
  values,
  setFieldError: vi.fn(),
})

describe("a Jakarta refusal", () => {
  it("is read field by field, keeping what the api said about each", () => {
    const parsed = parseApiValidation(refusal([
      {objectName: "createUserRequest", field: "email", message: "must be a well-formed email address", code: "Email"},
      {objectName: "createUserRequest", field: "initials", message: "must not be blank", code: "NotBlank"},
    ]))

    expect(parsed?.fieldErrors).toEqual({
      email: ["must be a well-formed email address"],
      initials: ["must not be blank"],
    })
    expect(parsed?.objectName).toBe("createUserRequest")
    expect(parsed?.detail).toBe("Validation failed for request.")
    expect(parsed?.status).toBe(400)
  })

  it("keeps two sentences about one field, since Jakarta reports each constraint", () => {
    const parsed = parseApiValidation(refusal([
      {field: "password", message: "must be at least 8 characters", code: "Size"},
      {field: "password", message: "must contain a digit", code: "Pattern"},
    ]))

    expect(parsed?.fieldErrors.password).toEqual([
      "must be at least 8 characters",
      "must contain a digit",
    ])
  })

  it("carries the trace id, which is what a report of it is chased by", () => {
    const parsed = parseApiValidation(refusal(
      [{field: "email", message: "must not be blank", code: "NotBlank"}],
      {traceId: "0af7651916cd43dd"},
    ))

    expect(parsed?.traceId).toBe("0af7651916cd43dd")
  })

  it("puts a nested path on the field that renders it", () => {
    const form = formOn({members: [{userId: 3, role: "Chair"}]})

    const left = apply(form as never, refusal([
      {objectName: "committeeRequest", field: "members[0].userId", message: "must not be null", code: "NotNull"},
    ]))

    expect(form.setFieldError).toHaveBeenCalledWith("members[0].userId", ["must not be null"])
    expect(left?.messages).toEqual([])
  })

  it("says out loud what names no field of this form, rather than parking it", () => {
    const form = formOn({email: ""})

    const left = apply(form as never, refusal([
      {objectName: "createUserRequest", field: "memberProfile.studyProgramme", message: "must not be blank", code: "NotBlank"},
    ]))

    expect(form.setFieldError).not.toHaveBeenCalled()
    expect(left?.messages).toEqual(["must not be blank"])
  })

  it("says a global refusal out loud, since it names no field at all", () => {
    const form = formOn({startDate: "", endDate: ""})

    const left = apply(form as never, refusal([
      {objectName: "contributionPeriodRequest", field: null, message: "The end cannot precede the start.", code: "ValidPeriod"},
    ]))

    expect(form.setFieldError).not.toHaveBeenCalled()
    expect(left?.messages).toEqual(["The end cannot precede the start."])
  })

  it("is not read as a refusal when the body carries no errors at all", () => {
    expect(parseApiValidation({response: {status: 400, data: {detail: "Nope."}}})).toBeNull()
    expect(parseApiValidation({response: {status: 500, data: {}}})).toBeNull()
    expect(parseApiValidation(new Error("offline"))).toBeNull()
  })

  it("puts a field the form maps elsewhere on the field it maps to", () => {
    const form = formOn({startDate: "", startTime: ""})

    apply(form as never, refusal([
      {objectName: "eventRequest", field: "startTime", message: "must be in the future", code: "Future"},
    ]), {startTime: ["startDate", "startTime"]})

    expect(form.setFieldError).toHaveBeenCalledWith("startDate", ["must be in the future"])
    expect(form.setFieldError).toHaveBeenCalledWith("startTime", ["must be in the future"])
  })
  it("drops a refusal that names neither a field nor a reason", () => {
    const form = formOn({startDate: ""})

    const left = apply(form as never, refusal([
      {objectName: "contributionPeriodRequest", field: null, message: undefined, code: "ValidPeriod"},
    ]))

    expect(form.setFieldError).not.toHaveBeenCalled()
    expect(left?.messages).toEqual([])
  })
})
