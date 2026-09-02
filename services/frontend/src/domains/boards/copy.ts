/** A count with its noun, singular where there is one of it. The board copy's only pluraliser. */
export const countOf = (n: number, one: string, many: string) => `${n} ${n === 1 ? one : many}`
