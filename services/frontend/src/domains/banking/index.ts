/**
 * The banking domain's public API (frontend ADR-001): a person's one bank account, which
 * declarations are paid to and a mandate collects from. Re-exported by name, because the list of
 * names is the promise.
 */
export {readOwnBankAccount, removeOwnBankAccount, saveOwnBankAccount} from "./adapters/bankAccounts"
export {type BankAccount, type BankAccountPreview} from "./bankAccount"
export {default as BankAccountForm} from "./island/BankAccountForm.vue"
export {default as BankGlyph} from "./island/BankGlyph.vue"
