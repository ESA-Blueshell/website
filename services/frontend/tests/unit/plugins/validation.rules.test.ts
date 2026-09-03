import {describe, expect, it} from "vitest"
import {validate} from "vee-validate"
import "@/plugins/validation"

describe("validation rules messages", () => {
  it("returns required/alphanumeric/length messages", async () => {
    const required = await validate("", "required")
    expect(required.valid).toBe(false)
    expect(required.errors[0]).toBe("This field is required")

    const alphaNum = await validate("abc-1", "alphaNum")
    expect(alphaNum.valid).toBe(false)
    expect(alphaNum.errors[0]).toBe("Use only letters and numbers")

    const minChars = await validate("ab", "minChars:3")
    expect(minChars.valid).toBe(false)
    expect(minChars.errors[0]).toBe("Must be at least 3 characters")

    const maxChars = await validate("abcd", "maxChars:3")
    expect(maxChars.valid).toBe(false)
    expect(maxChars.errors[0]).toBe("Must be at most 3 characters")
  })

  it("returns numeric bound messages", async () => {
    const minValue = await validate("-1", "minValue:0")
    expect(minValue.valid).toBe(false)
    expect(minValue.errors[0]).toBe("Must be at least 0")

    const maxValue = await validate("6", "maxValue:5")
    expect(maxValue.valid).toBe(false)
    expect(maxValue.errors[0]).toBe("May be at most 5")
  })

  it("returns e-mail and password policy messages", async () => {
    const email = await validate("bad@", "email")
    expect(email.valid).toBe(false)
    expect(email.errors[0]).toBe("Enter a valid e-mail address")

    const studentEmail = await validate("foo@student.utwente.nl", "noStudentEmail")
    expect(studentEmail.valid).toBe(false)
    expect(studentEmail.errors[0]).toBe("You may not use your student email to sign up")

    const lower = await validate("PASSWORD1!", "hasLower")
    expect(lower.valid).toBe(false)
    expect(lower.errors[0]).toBe("Include a lowercase letter")

    const upper = await validate("password1!", "hasUpper")
    expect(upper.valid).toBe(false)
    expect(upper.errors[0]).toBe("Include an uppercase letter")

    const number = await validate("Password!", "hasNumber")
    expect(number.valid).toBe(false)
    expect(number.errors[0]).toBe("Include a number")

    const special = await validate("Password1", "hasSpecial")
    expect(special.valid).toBe(false)
    expect(special.errors[0]).toBe("Include a special character")
  })

  // The api accepts any non-alphanumeric, so a symbol outside the old allowlist
  // has to pass here too: rejecting it was refusing a password the api wanted.
  it.each(["Password1#", "Password1-", "Password1 ", "Password1é", "Password1@"])(
    "accepts %s as complex enough",
    async (candidate) => {
      const special = await validate(candidate, "hasSpecial")
      expect(special.valid).toBe(true)
    },
  )

  it("returns cross-field mismatch message", async () => {
    const mismatch = await validate("Secret#123", "match:@password", {
      values: {password: "Other#123"},
    } as never)

    expect(mismatch.valid).toBe(false)
    expect(mismatch.errors[0]).toBe("Values do not match")
  })

  it("returns date validation messages", async () => {
    const invalidDate = await validate("not-a-date", "dateAfter:2025-01-01")
    expect(invalidDate.valid).toBe(false)
    expect(invalidDate.errors[0]).toBe("Enter a valid date")

    const before = await validate("2025-01-02", "dateBefore:2025-01-01")
    expect(before.valid).toBe(false)
    expect(before.errors[0]).toBe("Date must be before 2025-01-01")

    const after = await validate("2025-01-01", "dateAfter:2025-01-02")
    expect(after.valid).toBe(false)
    expect(after.errors[0]).toBe("Date must be after 2025-01-02")

    const min = await validate("2025-01-01", "dateMin:2025-01-02")
    expect(min.valid).toBe(false)
    expect(min.errors[0]).toBe("Date must be at least 2025-01-02")

    const max = await validate("2025-01-03", "dateMax:2025-01-02")
    expect(max.valid).toBe(false)
    expect(max.errors[0]).toBe("Date must be at most 2025-01-02")
  })

  it("returns date-required, date-time and phone messages", async () => {
    const dateRequired = await validate("", "dateRequired")
    expect(dateRequired.valid).toBe(false)
    expect(dateRequired.errors[0]).toBe("Date is required")

    const dateTimeAfter = await validate("2025-01-01T09:00", "dateTimeAfter:2025-01-01T10:00")
    expect(dateTimeAfter.valid).toBe(false)
    expect(dateTimeAfter.errors[0]).toContain("Must be after")

    const invalidPhone = await validate("123", "phoneMobile:NL")
    expect(invalidPhone.valid).toBe(false)
    expect(invalidPhone.errors[0]).toBe("Enter a valid phone number")
  })
})
