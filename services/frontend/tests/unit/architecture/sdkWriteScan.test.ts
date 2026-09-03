import {describe, expect, it} from "vitest"
import {findUncheckedWrites, mutatingSdkFunctions} from "./sdkWriteScan"

const MUTATORS = new Set(["createBoard", "deleteBoard"])

const offendersIn = (source: string) =>
  findUncheckedWrites(source, MUTATORS).map(one => `${one.fn}:${one.line}`)

describe("mutatingSdkFunctions", () => {
  it("names the functions the generated sdk sends with a mutating method", () => {
    const sdk = [
      "export const findAllBoards = <ThrowOnError extends boolean = false>(options?: Options)",
      ": RequestResult<A, B, ThrowOnError> => (options.client ?? client).get<A, B, ThrowOnError>({",
      "    url: '/boards'",
      "});",
      "export const createBoard = <ThrowOnError extends boolean = false>(options: Options)",
      ": RequestResult<A, B, ThrowOnError> => (options.client ?? client).post<A, B, ThrowOnError>({",
      "    url: '/boards'",
      "});",
      "export const deleteBoard = <ThrowOnError extends boolean = false>(options: Options)",
      ": RequestResult<A, B, ThrowOnError> => (options.client ?? client).delete<A, B, ThrowOnError>",
      "({ url: '/boards/{id}' });",
    ].join("\n")

    expect(mutatingSdkFunctions(sdk)).toEqual(new Set(["createBoard", "deleteBoard"]))
  })
})

describe("findUncheckedWrites", () => {
  it("reports a write whose result is thrown away", () => {
    expect(offendersIn("async function drop() {\n  await deleteBoard({path: {id: 1}})\n}\n"))
      .toEqual(["deleteBoard:2"])
  })

  it("accepts a write that asks the sdk to throw", () => {
    expect(offendersIn("await deleteBoard({path: {id: 1}, throwOnError: true})")).toEqual([])
  })

  it("accepts a write whose bound result is inspected further down the block", () => {
    const source = [
      "async function drop() {",
      "  const res = await deleteBoard({path: {id: 1}})",
      "  if (res.error) return false",
      "  return true",
      "}",
    ].join("\n")

    expect(offendersIn(source)).toEqual([])
  })

  it("reports a write whose result is bound and never read", () => {
    const source = [
      "async function drop() {",
      "  const res = await deleteBoard({path: {id: 1}})",
      "  return res != null",
      "}",
    ].join("\n")

    expect(offendersIn(source)).toEqual(["deleteBoard:2"])
  })

  it("stops looking once the binding leaves the block it was made in", () => {
    const source = [
      "async function drop() {",
      "  if (ready) {",
      "    const res = await deleteBoard({path: {id: 1}})",
      "  }",
      "  if (res.error) return false",
      "}",
    ].join("\n")

    expect(offendersIn(source)).toEqual(["deleteBoard:3"])
  })

  it("accepts a destructured error or data", () => {
    expect(offendersIn("const {error} = await deleteBoard({path: {id: 1}})")).toEqual([])
    expect(offendersIn("const {data} = await createBoard({body})")).toEqual([])
  })

  it("reports a destructuring that keeps neither error nor data", () => {
    expect(offendersIn("const {request} = await createBoard({body})")).toEqual(["createBoard:1"])
  })

  it("accepts a result read straight off the call", () => {
    expect(offendersIn("if ((await createBoard({body})).error) return")).toEqual([])
  })

  it("accepts a result handed to the caller", () => {
    expect(offendersIn("const save = () => createBoard({body})")).toEqual([])
    expect(offendersIn("async function save() {\n  return await createBoard({body})\n}")).toEqual([])
  })

  it("ignores a same-named method on some other object", () => {
    expect(offendersIn("await api.deleteBoard({path: {id: 1}})")).toEqual([])
  })

  it("ignores the import that brings the function in", () => {
    const source = [
      "import {createBoard, deleteBoard} from \"@/services/api\"",
      "export function wrap(createBoard: () => void) {",
      "  return createBoard",
      "}",
    ].join("\n")

    expect(offendersIn(source)).toEqual([])
  })

  it("ignores a call that only appears in a comment", () => {
    const source = [
      "/**",
      " * Callers do `await deleteBoard({path: {id: 1}})` and check the answer.",
      " */",
      "// await createBoard({body})",
      "export const nothing = 1",
    ].join("\n")

    expect(offendersIn(source)).toEqual([])
  })

  it("counts each write separately when a block holds several", () => {
    const source = [
      "async function both() {",
      "  await createBoard({body})",
      "  await deleteBoard({path: {id: 1}})",
      "}",
    ].join("\n")

    expect(offendersIn(source)).toEqual(["createBoard:2", "deleteBoard:3"])
  })

  it("does not let one checked binding cover a later write reusing its name", () => {
    const source = [
      "async function both() {",
      "  const res = await createBoard({body})",
      "  if (res.error) return",
      "  const res2 = await deleteBoard({path: {id: 1}})",
      "  console.log(res.data)",
      "}",
    ].join("\n")

    expect(offendersIn(source)).toEqual(["deleteBoard:4"])
  })
})
