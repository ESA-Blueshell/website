/**
 * Wording the esports pages compose, shared by whatever needs to say the same thing twice.
 *
 * Apart from `refusals.ts`, which says what a refused write means: the two are separate because
 * a refusal is a contract with the api and this is not. Both are display strings, which is why
 * they live in the frontend at all.
 */

/**
 * How many of a thing, named singly or in the plural.
 *
 * Here rather than beside any one caller: a game holding one team read "1 teams" for as long as
 * this was the api's job, and the branch is the whole reason the helper exists.
 */
export const countOf = (n: number, one: string, many: string) => `${n} ${n === 1 ? one : many}`
