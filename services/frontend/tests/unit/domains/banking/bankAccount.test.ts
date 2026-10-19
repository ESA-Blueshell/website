import {describe, expect, it} from "vitest"
import {
  bicGroups,
  bicProblem,
  bicSample,
  fits,
  IBAN_LENGTHS,
  ibanCountry,
  ibanSample,
  ibanShape,
  ibanShapeFor,
  ibanState,
  ibanValid,
  inFours,
  needsBic,
  plain,
  tidied,
  wrongIn,
} from "@/domains/banking/bankAccount"

describe("an IBAN as it is typed", () => {
  it("checks the check digits, so a typo is caught before the bank refuses it", () => {
    expect(ibanValid("NL91 ABNA 0417 1643 00")).toBe(true)
    expect(ibanValid("NL92ABNA0417164300")).toBe(false)
    expect(ibanValid("not an iban")).toBe(false)
  })

  it("knows each country's layout and length, and shows the Dutch one until a country is typed", () => {
    expect(ibanShape("NL")).toHaveLength(18)
    expect(ibanShape("XX")).toBeNull()
    expect(IBAN_LENGTHS.DE).toBe(22)
    expect(ibanShapeFor("")).toEqual(ibanShape("NL"))
    expect(ibanShapeFor("DE")).toEqual(ibanShape("DE"))
    expect(ibanShapeFor("XX")).toHaveLength(34)
    expect(ibanShapeFor("1X")).toHaveLength(34)
    expect(ibanSample("", ibanShape("NL")!)).toBe("NL00ABCD0123456789")
    expect(inFours("NL00ABCD0123456789")).toBe("NL00 ABCD 0123 4567 89")
  })

  it("lets each position take only what belongs there", () => {
    expect(fits("A", "a")).toBe(true)
    expect(fits("1", "a")).toBe(false)
    expect(fits("1", "n")).toBe(true)
    expect(fits("Z", "c")).toBe(true)
    expect(plain(" nl91-abna ")).toBe("NL91ABNA")
    expect(ibanCountry("nl")).toBe("NL")
    expect(ibanCountry("1")).toBeNull()
  })

  it("says how far it has got, and its mistakes once they are certain", () => {
    expect(ibanState("")).toEqual({complete: false, problem: null, hint: ""})
    expect(ibanState("12").problem).toContain("starts with two letters")
    expect(ibanState("XX12").problem).toContain("start with XX")
    expect(ibanState("NL91ABNA041716430").hint).toBe("1 more character to go.")
    expect(ibanState("NL91ABNA04171643").hint).toBe("2 more characters to go.")
    expect(ibanState("NL91ABNA0417164300")).toEqual({complete: true, problem: null, hint: "This IBAN is valid."})
    expect(ibanState("NL92ABNA0417164300").problem).toContain("mistake")
  })
})

describe("a BIC", () => {
  it("is needed outside the Netherlands only, and is drawn as it is built", () => {
    expect(needsBic("DE89370400440532013000")).toBe(true)
    expect(needsBic("NL91ABNA0417164300")).toBe(false)
    expect(needsBic("N")).toBe(false)
    expect(bicSample("DE")).toBe("BANKDE2AXXX")
    expect(bicSample(null)).toBe("BANKNL2AXXX")
    expect(bicGroups("COBADEFFXXX")).toBe("COBA DE FF XXX")
  })

  it("has 8 or 11 characters and the IBAN's country", () => {
    expect(bicProblem("", "DE89370400440532013000")).toBeNull()
    expect(bicProblem("NOPE", "DE89370400440532013000")).toContain("8 or 11")
    expect(bicProblem("INGBNL2A", "DE89370400440532013000")).toContain("bank in NL")
    expect(bicProblem("COBADEFFXXX", "DE89370400440532013000")).toBeNull()
  })
})

describe("the account as typed", () => {
  it("names each mistake in the form's words, and is tidied before it is sent", () => {
    expect(wrongIn({holder: " ", iban: "", bic: ""})).toEqual({holder: "Add the name on the account.", iban: "Add your IBAN."})
    expect(wrongIn({holder: "Ann", iban: "NL91ABNA", bic: ""}).iban).toBe("This IBAN is not complete yet.")
    expect(wrongIn({holder: "Ann", iban: "DE89370400440532013000", bic: ""})).toEqual({bic: "Add the BIC of your bank."})
    expect(wrongIn({holder: "Ann", iban: "DE89370400440532013000", bic: "INGBNL2A"}).bic).toContain("bank in NL")
    expect(tidied({holder: " Ann ", iban: "nl91 abna 0417 1643 00", bic: "x"})).toEqual({holder: "Ann", iban: "NL91ABNA0417164300", bic: ""})
    expect(tidied({holder: "Ann", iban: "DE89370400440532013000", bic: "coba de ff"})).toEqual({holder: "Ann", iban: "DE89370400440532013000", bic: "COBADEFF"})
  })
})
