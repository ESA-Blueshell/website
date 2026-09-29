export type HandleChange<T> = (v: T) => void
/** Turns what the control emits into the field's value. The control has its own type: a number input emits text. */
export type UpdateFn<T, Shown = T> = (incoming: Shown, handleChange: HandleChange<T>) => void
export type DisplayFn<T, Shown = T> = (value: T) => Shown
