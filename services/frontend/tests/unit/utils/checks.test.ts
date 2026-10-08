import {describe, expect, it} from "vitest"
import {
  accepted, dateAfter, dateBefore, dateMax, dateMin, dateRequired, email, firstFailure, matches,
  maxChars, maxValue, minChars, minValue, momentAfter, momentNotAfter, notEmpty, phoneMobile, required,
  strongPassword,
} from "@/utils/checks"

describe("what a field must hold", () => {
  it("asks for something rather than nothing", () => {
    expect(notEmpty(null)).toBe("Must not be empty")
    expect(notEmpty(["one"])).toBeNull()
    expect(required("")).toBe("This field is required")
    expect(required("  ")).toBe("This field is required")
    expect(required(null)).toBe("This field is required")
    expect(required("said")).toBeNull()
    expect(dateRequired("")).toBe("Date is required")
    expect(dateRequired("2026-01-01")).toBeNull()
  })

  it("counts the characters and the number both ways, and leaves an empty field to required", () => {
    expect(minChars(3)("")).toBeNull()
    expect(minChars(3)("ab")).toBe("Must be at least 3 characters")
    expect(maxChars(3)("abc")).toBeNull()
    expect(maxChars(3)("abcd")).toBe("Must be at most 3 characters")
    expect(minValue(3)("")).toBeNull()
    expect(minValue(3)("2")).toBe("Must be at least 3")
    expect(maxValue(3)("4")).toBe("May be at most 3")
    expect(maxValue(3)(3)).toBeNull()
  })

  it("knows an address from a word with an at sign in it", () => {
    expect(email("")).toBeNull()
    expect(email("s1234@student.utwente.nl")).toBeNull()
    expect(email("joris@blueshell")).toBe("Enter a valid e-mail address")
  })

  it("asks a password for each of the four things, naming the first it lacks", () => {
    expect(strongPassword("")).toBeNull()
    expect(strongPassword("Aa1!")).toBeNull()
    expect(strongPassword("AA1!")).toBe("Include a lowercase letter")
    expect(strongPassword("aa1!")).toBe("Include an uppercase letter")
    expect(strongPassword("Aaa!")).toBe("Include a number")
    expect(strongPassword("Aaa1")).toBe("Include a special character")
  })

  it("matches one field against another as it is now", () => {
    let other = "a"
    const check = matches(() => other)
    expect(check("")).toBeNull()
    expect(check("a")).toBeNull()
    other = "b"
    expect(check("a")).toBe("Values do not match")
  })

  it("says a tick box must be ticked in the words it is given", () => {
    const check = accepted("Accept the conditions.")
    expect(check(true)).toBeNull()
    expect(check(false)).toBe("Accept the conditions.")
  })
})

describe("one date against another", () => {
  const cases = [
    {check: dateBefore, pass: "2026-01-01", fail: "2026-03-01", says: "Date must be before 2026-02-01"},
    {check: dateAfter, pass: "2026-03-01", fail: "2026-01-01", says: "Date must be after 2026-02-01"},
    {check: dateMax, pass: "2026-02-01", fail: "2026-03-01", says: "Date must be at most 2026-02-01"},
    {check: dateMin, pass: "2026-02-01", fail: "2026-01-01", says: "Date must be at least 2026-02-01"},
  ]

  it.each(cases)("$says reads the date it is given when it runs", (one) => {
    const check = one.check(() => "2026-02-01")
    expect(check("")).toBeNull()
    expect(check(one.pass)).toBeNull()
    expect(check(one.fail)).toBe(one.says)
    expect(check("what")).toBe("Enter a valid date")
    expect(one.check(() => "whenever")(one.pass)).toBeNull()
    expect(one.check(() => null)(one.pass)).toBeNull()
  })
})

describe("one moment against another", () => {
  const at = () => "2026-02-01T12:00"

  it("takes a moment after the one it is given", () => {
    expect(momentAfter(at)("")).toBeNull()
    expect(momentAfter(at)("2026-02-01T13:00")).toBeNull()
    expect(momentAfter(at)("2026-02-01T11:00")).toBe("Must be after 01/02/2026 12:00")
    expect(momentAfter(at)("what")).toBe("Enter a valid date")
    expect(momentAfter(() => "whenever")("2026-02-01T13:00")).toBeNull()
  })

  it("takes a moment on or before the one it is given", () => {
    expect(momentNotAfter(at)("2026-02-01T11:00")).toBeNull()
    expect(momentNotAfter(at)("2026-02-01T13:00")).toBe("Must be before or on 01/02/2026 12:00")
  })
})

describe("a phone number", () => {
  it("asks for a mobile one that can be called, in the country it is read in", () => {
    let country = "NL"
    const check = phoneMobile(() => country)
    expect(check("")).toBeNull()
    expect(check("0612345678")).toBeNull()
    expect(check("0201234567")).toBe("Enter a mobile phone number")
    expect(check("12")).toBe("Enter a valid phone number")
    country = "ZZ"
    expect(check("0612345678")).toBe("Enter a valid phone number")
  })
})

describe("a field's checks together", () => {
  it("answer with the first that fails, in the order given", () => {
    expect(firstFailure("", [required, minChars(3)])).toBe("This field is required")
    expect(firstFailure("ab", [required, minChars(3)])).toBe("Must be at least 3 characters")
    expect(firstFailure("abc", [required, minChars(3)])).toBeNull()
    expect(firstFailure("", [])).toBeNull()
  })
})
