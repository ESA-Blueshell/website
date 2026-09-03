import {describe, expect, it, vi} from "vitest"
import {apply, isEmpty, parseApiValidation} from "@/plugins/validation"

describe("validation plugin helpers", () => {
  it("detects empty values", () => {
    expect(isEmpty(undefined)).toBe(true)
    expect(isEmpty(null)).toBe(true)
    expect(isEmpty("  ")).toBe(true)
    expect(isEmpty("value")).toBe(false)
  })

  it("parses API validation payloads", () => {
    const parsed = parseApiValidation({
      response: {
        status: 400,
        data: {
          status: 400,
          detail: "validation failed",
          traceId: "trace-123",
          errors: [
            {objectName: "UserRequest", field: "email", message: "Invalid e-mail"},
            {objectName: "UserRequest", field: "email", message: "Already taken"},
            {objectName: "UserRequest", field: "", message: "Ignore empty field key"},
          ],
        },
      },
    })

    expect(parsed?.objectName).toBe("UserRequest")
    expect(parsed?.fieldErrors.email).toEqual(["Invalid e-mail", "Already taken"])
    expect(parsed?.detail).toBe("validation failed")
    expect(parsed?.status).toBe(400)
    expect(parsed?.traceId).toBe("trace-123")
    expect(Object.keys(parsed?.fieldErrors ?? {})).toEqual(["email"])
  })

  it("returns null when payload does not contain actionable validation errors", () => {
    expect(parseApiValidation({response: {status: 400}})).toBeNull()
    expect(parseApiValidation({response: {status: 409, data: {status: 409, errors: []}}})).toBeNull()
    expect(parseApiValidation({response: {status: 400, data: {status: 400, errors: []}}})).toBeNull()
    expect(parseApiValidation({foo: "bar"})).toBeNull()
  })

  it("applies field errors to vee form context", () => {
    const setFieldError = vi.fn()
    const unattached = apply(
      {setFieldError, values: {username: ""}} as never,
      {
        response: {
          status: 400,
          data: {
            status: 400,
            errors: [{field: "username", message: "Required"}],
          },
        },
      },
    )

    expect(unattached?.messages).toEqual([])
    expect(setFieldError).toHaveBeenCalledWith("username", ["Required"])
  })

  it("returns null for non-validation errors", () => {
    const setFieldError = vi.fn()
    expect(apply({setFieldError, values: {}} as never, {response: {status: 500, data: {}}})).toBeNull()
    expect(apply({setFieldError, values: {}} as never, {foo: "bar"})).toBeNull()
    expect(setFieldError).not.toHaveBeenCalled()
  })

  it("attaches a nested backend path to the flat field its map names", () => {
    const setFieldError = vi.fn()
    const unattached = apply(
      {setFieldError, values: {nationality: ""}} as never,
      {
        response: {
          status: 400,
          data: {
            status: 400,
            errors: [{field: "memberProfile.nationality", message: "must not be blank"}],
          },
        },
      },
      {"memberProfile.nationality": "nationality"},
    )

    expect(setFieldError).toHaveBeenCalledWith("nationality", ["must not be blank"])
    expect(unattached?.messages).toEqual([])
  })

  // Two objects can hold a field of the same name, so blaming the input that
  // happens to share the last segment would point at the wrong one.
  it("never guesses a field from the last segment of a path", () => {
    const setFieldError = vi.fn()
    const unattached = apply(
      {setFieldError, values: {country: "NL"}} as never,
      {
        response: {
          status: 400,
          data: {
            status: 400,
            errors: [{field: "birthplace.country", message: "must be a valid code"}],
          },
        },
      },
    )

    expect(setFieldError).not.toHaveBeenCalled()
    expect(unattached?.messages).toEqual(["must be a valid code"])
  })

  // The form renders one set of fields per mode, so a map is a claim about where
  // the error goes rather than proof the field is on screen.
  it("reports a mapped target the form is not rendering", () => {
    const setFieldError = vi.fn()
    const unattached = apply(
      {setFieldError, values: {street: ""}} as never,
      {
        response: {
          status: 400,
          data: {
            status: 400,
            errors: [{field: "memberProfile.dateOfBirth", message: "must not be null"}],
          },
        },
      },
      {"memberProfile.dateOfBirth": "dateOfBirth"},
    )

    expect(setFieldError).not.toHaveBeenCalled()
    expect(unattached?.messages).toEqual(["must not be null"])
  })

  it("reports an error for a field this form does not render rather than parking it", () => {
    const setFieldError = vi.fn()
    const unattached = apply(
      {setFieldError, values: {street: ""}} as never,
      {
        response: {
          status: 400,
          data: {
            status: 400,
            errors: [{field: "userId", message: "must be greater than 0"}],
          },
        },
      },
    )

    expect(setFieldError).not.toHaveBeenCalled()
    expect(unattached?.messages).toEqual(["must be greater than 0"])
  })

  it("applies errors to a remapped field name when fieldMap provides a string target", () => {
    const setFieldError = vi.fn()
    apply(
      {setFieldError, values: {banner: null}} as never,
      {
        response: {
          status: 400,
          data: {status: 400, errors: [{field: "banner.fileId", message: "Required"}]},
        },
      },
      {"banner.fileId": "banner"},
    )

    expect(setFieldError).toHaveBeenCalledOnce()
    expect(setFieldError).toHaveBeenCalledWith("banner", ["Required"])
  })

  it("fans out to multiple frontend fields when fieldMap provides an array target", () => {
    const setFieldError = vi.fn()
    apply(
      {setFieldError, values: {startDate: "", startTime: ""}} as never,
      {
        response: {
          status: 400,
          data: {
            status: 400,
            errors: [{field: "startTime", message: "Must not be null"}],
          },
        },
      },
      {startTime: ["startDate", "startTime"]},
    )

    expect(setFieldError).toHaveBeenCalledTimes(2)
    expect(setFieldError).toHaveBeenCalledWith("startDate", ["Must not be null"])
    expect(setFieldError).toHaveBeenCalledWith("startTime", ["Must not be null"])
  })

  it("falls back to original field name for unmapped fields even when fieldMap is provided", () => {
    const setFieldError = vi.fn()
    apply(
      {setFieldError, values: {title: "", startDate: "", startTime: ""}} as never,
      {
        response: {
          status: 400,
          data: {
            status: 400,
            errors: [
              {field: "title", message: "Required"},
              {field: "startTime", message: "Must not be null"},
            ],
          },
        },
      },
      {startTime: ["startDate", "startTime"]},
    )

    // title: no mapping → direct
    expect(setFieldError).toHaveBeenCalledWith("title", ["Required"])
    // startTime: mapped → two calls
    expect(setFieldError).toHaveBeenCalledWith("startDate", ["Must not be null"])
    expect(setFieldError).toHaveBeenCalledWith("startTime", ["Must not be null"])
    expect(setFieldError).toHaveBeenCalledTimes(3)
  })
})
