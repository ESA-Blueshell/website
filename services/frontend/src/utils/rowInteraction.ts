/**
 * Check if a click target is on an interactive control.
 * Returns true if the click should be ignored (not passed to row selection).
 * @param target The element that was clicked.
 * @returns true if the click is on an interactive element (button, input, etc.).
 */
export function isClickOnInteractiveTarget(target: HTMLElement): boolean {
  return !!target.closest('button, a, input, label, .v-selection-control, [role=button]')
}
