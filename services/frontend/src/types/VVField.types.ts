export type HandleChange<T> = (v: T) => void
export type UpdateFn<T> = (incoming: T, handleChange: HandleChange<T>) => void
export type DisplayFn<T> = (value: T) => T
