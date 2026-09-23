import {describe, expect, it} from "vitest"
import {validate} from "vee-validate"
import "@/plugins/validation"

const said = async (value: unknown, rule: string, values: Record<string, unknown> = {}) =>
  (await validate(value, rule, {values})).errors[0]

describe("what a field must hold", () => {
  it("asks for something rather than nothing", async () => {
    expect(await said(null, "notEmpty")).toBe("Must not be empty")
    expect(await said(["one"], "notEmpty")).toBeUndefined()
    expect(await said("", "required")).toBe("This field is required")
    expect(await said("  ", "required")).toBe("This field is required")
    expect(await said(null, "required")).toBe("This field is required")
    expect(await said("said", "required")).toBeUndefined()
    expect(await said("", "dateRequired")).toBe("Date is required")
    expect(await said("2026-01-01", "dateRequired")).toBeUndefined()
  })

  it("takes letters and numbers and nothing else", async () => {
    expect(await said("", "alphaNum")).toBeUndefined()
    expect(await said("abc123", "alphaNum")).toBeUndefined()
    expect(await said("a b", "alphaNum")).toBe("Use only letters and numbers")
  })

  it("counts the characters both ways", async () => {
    expect(await said("", "minChars:3")).toBeUndefined()
    expect(await said("abc", "minChars:3")).toBeUndefined()
    expect(await said("ab", "minChars:3")).toBe("Must be at least 3 characters")
    expect(await said("ab", "minChars")).toBeUndefined()
    expect(await said("", "maxChars:3")).toBeUndefined()
    expect(await said("abc", "maxChars:3")).toBeUndefined()
    expect(await said("abcd", "maxChars:3")).toBe("Must be at most 3 characters")
    expect(await said("abcd", "maxChars")).toBeUndefined()
  })

  it("counts the number both ways", async () => {
    expect(await said("", "minValue:3")).toBeUndefined()
    expect(await said("3", "minValue:3")).toBeUndefined()
    expect(await said("2", "minValue:3")).toBe("Must be at least 3")
    expect(await said("-1", "minValue")).toBe("Must be at least 0")
    expect(await said("", "maxValue:3")).toBeUndefined()
    expect(await said("3", "maxValue:3")).toBeUndefined()
    expect(await said("4", "maxValue:3")).toBe("May be at most 3")
    expect(await said("1", "maxValue")).toBe("May be at most 0")
  })

  it("knows an address from a word with an at sign in it", async () => {
    expect(await said("", "email")).toBeUndefined()
    expect(await said("joris@blueshell.nl", "email")).toBeUndefined()
    expect(await said("joris@blueshell", "email")).toBe("Enter a valid e-mail address")
    expect(await said("", "noStudentEmail")).toBeUndefined()
    expect(await said("joris@blueshell.nl", "noStudentEmail")).toBeUndefined()
    expect(await said("s1234@student.ru.nl", "noStudentEmail"))
      .toBe("You may not use your student email to sign up")
  })

  it("asks a password for each of the four things", async () => {
    expect(await said("", "hasLower|hasUpper|hasNumber|hasSpecial")).toBeUndefined()
    expect(await said("Aa1!", "hasLower|hasUpper|hasNumber|hasSpecial")).toBeUndefined()
    expect(await said("AA1!", "hasLower")).toBe("Include a lowercase letter")
    expect(await said("aa1!", "hasUpper")).toBe("Include an uppercase letter")
    expect(await said("Aaa!", "hasNumber")).toBe("Include a number")
    expect(await said("Aaa1", "hasSpecial")).toBe("Include a special character")
  })

  it("matches one field against another, or against a word", async () => {
    expect(await said("", "match:@password")).toBeUndefined()
    expect(await said("a", "match:@password", {password: "a"})).toBeUndefined()
    expect(await said("a", "match:@password", {password: "b"})).toBe("Values do not match")
    expect(await said("a", "match:a")).toBeUndefined()
    expect(await said("a", "match:b")).toBe("Values do not match")
  })
})

describe("one date against another", () => {
  const cases = [
    {rule: "dateBefore", pass: "2026-01-01", fail: "2026-03-01", says: "Date must be before 2026-02-01"},
    {rule: "dateAfter", pass: "2026-03-01", fail: "2026-01-01", says: "Date must be after 2026-02-01"},
    {rule: "dateMax", pass: "2026-02-01", fail: "2026-03-01", says: "Date must be at most 2026-02-01"},
    {rule: "dateMin", pass: "2026-02-01", fail: "2026-01-01", says: "Date must be at least 2026-02-01"},
  ]

  it.each(cases)("$rule reads both the field it names and the date it is given", async (one) => {
    expect(await said("", `${one.rule}:2026-02-01`)).toBeUndefined()
    expect(await said(one.pass, `${one.rule}:2026-02-01`)).toBeUndefined()
    expect(await said(one.fail, `${one.rule}:2026-02-01`)).toBe(one.says)
    expect(await said(one.pass, `${one.rule}:@other`, {other: "2026-02-01"})).toBeUndefined()
    expect(await said(one.fail, `${one.rule}:@other`, {other: "2026-02-01"})).toBe(one.says)
    expect(await said("what", `${one.rule}:2026-02-01`)).toBe("Enter a valid date")
    expect(await said(one.pass, `${one.rule}:whenever`)).toBeUndefined()
  })
})

describe("one moment against another", () => {
  const at = "2026-02-01T12:00"

  it("takes a moment after the one it is given", async () => {
    expect(await said("", `dateTimeAfter:${at}`)).toBeUndefined()
    expect(await said("2026-02-01T13:00", `dateTimeAfter:${at}`)).toBeUndefined()
    expect(await said("2026-02-01T11:00", `dateTimeAfter:${at}`))
      .toBe("Must be after 01/02/2026 12:00")
    expect(await said("2026-02-01T13:00", "dateTimeAfter:@other", {other: at})).toBeUndefined()
    expect(await said("2026-02-01T11:00", "dateTimeAfter:@other", {other: at}))
      .toBe("Must be after 01/02/2026 12:00")
    expect(await said("what", `dateTimeAfter:${at}`)).toBe("Enter a valid date")
    expect(await said("2026-02-01T13:00", "dateTimeAfter:whenever")).toBeUndefined()
  })

  it("takes a moment on or before the one it is given", async () => {
    expect(await said("", `dateTimeNotAfter:${at}`)).toBeUndefined()
    expect(await said("2026-02-01T11:00", `dateTimeNotAfter:${at}`)).toBeUndefined()
    expect(await said("2026-02-01T13:00", `dateTimeNotAfter:${at}`))
      .toBe("Must be before or on 01/02/2026 12:00")
    expect(await said("2026-02-01T11:00", "dateTimeNotAfter:@other", {other: at})).toBeUndefined()
    expect(await said("2026-02-01T13:00", "dateTimeNotAfter:@other", {other: at}))
      .toBe("Must be before or on 01/02/2026 12:00")
    expect(await said("what", `dateTimeNotAfter:${at}`)).toBe("Enter a valid date")
    expect(await said("2026-02-01T11:00", "dateTimeNotAfter:whenever")).toBeUndefined()
  })
})

describe("a phone number", () => {
  it("asks for a mobile one that can be called", async () => {
    expect(await said("", "phoneMobile:NL")).toBeUndefined()
    expect(await said("0612345678", "phoneMobile:NL")).toBeUndefined()
    expect(await said("0612345678", "phoneMobile")).toBeUndefined()
    expect(await said("0201234567", "phoneMobile:NL")).toBe("Enter a mobile phone number")
    expect(await said("12", "phoneMobile:NL")).toBe("Enter a valid phone number")
    expect(await said("0612345678", "phoneMobile:ZZ")).toBe("Enter a valid phone number")
  })
})
