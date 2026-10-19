/**
 * The declarations domain's public API (frontend ADR-001). Re-exported by name, because the list
 * of names is the promise being made.
 */
export {
  approveDeclaration,
  deleteDraft,
  listDeclarations,
  listOwnDeclarations,
  readDeclaration,
  readOwnDeclaration,
  rejectDeclaration,
  saveDraft,
  sendBackDeclaration,
  submitDeclaration,
  withdrawDeclaration,
} from "./adapters/declarations"
export {
  BookingKind,
  type Choice,
  day,
  type Declaration,
  type DeclarationDraft,
  DeclarationState,
  euros,
  type Expense,
  forsOf,
  metaOf,
  type Receipt,
  receiptsOf,
  sentBack,
  STATE_MARKS,
  stateOf,
  stepAt,
  stepLabel,
  titleOf,
  totalOf,
  withTheBoard,
} from "./declarations"
export {default as DeclarationGlyph} from "./island/DeclarationGlyph.vue"
export {default as DeclarationSheet} from "./island/DeclarationSheet.vue"
export {default as ExpenseEditor} from "./island/ExpenseEditor.vue"
export {default as ReceiptPicker} from "./island/ReceiptPicker.vue"
export {default as ReceiptStrip} from "./island/ReceiptStrip.vue"
export {useBookingChoices} from "./island/useBookingChoices"
