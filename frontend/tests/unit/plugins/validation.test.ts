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
    const ok = apply(
      {setFieldError} as never,
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

    expect(ok).toBe(true)
    expect(setFieldError).toHaveBeenCalledWith("username", ["Required"])
  })

  it("returns false for non-validation errors", () => {
    const setFieldError = vi.fn()
    expect(apply({setFieldError} as never, {response: {status: 500, data: {}}})).toBe(false)
    expect(apply({setFieldError} as never, {foo: "bar"})).toBe(false)
    expect(setFieldError).not.toHaveBeenCalled()
  })
})
