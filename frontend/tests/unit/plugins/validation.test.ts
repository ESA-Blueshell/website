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
          errors: [
            {objectName: "UserRequest", field: "email", message: "Invalid e-mail"},
            {objectName: "UserRequest", field: "email", message: "Already taken"},
          ],
        },
      },
    })

    expect(parsed?.objectName).toBe("UserRequest")
    expect(parsed?.fieldErrors.email).toEqual(["Invalid e-mail", "Already taken"])
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
    const ok = apply({setFieldError: vi.fn()} as never, {response: {status: 500, data: {}}})
    expect(ok).toBe(false)
  })
})
