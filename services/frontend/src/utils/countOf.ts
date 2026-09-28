/** A count with its noun, singular where there is one of it. */
export const countOf = (n: number, one: string, many: string) => `${n} ${n === 1 ? one : many}`
