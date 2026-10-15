package net.blueshell.api.declaration

import org.springframework.modulith.ApplicationModule
import org.springframework.modulith.PackageInfo

/**
 * Declarations: what a person asks to be paid back, its expenses and receipts, and every change
 * of its state (api ADR-041).
 *
 * Receipts are rows in `files` of their own private kind; this module decides who opens one.
 */
@PackageInfo
@ApplicationModule(
    id = "declaration",
    allowedDependencies = [
        // An expense names a committee, checked and named through CommitteeService.
        "committee :: api",
        // CommitteeService answers the Committee row it was asked for.
        "committee :: entities",
        // An expense names an event, checked and named through EventService.
        "event :: api",
        // EventService answers the Event row it was asked for.
        "event :: entities",
        // Receipts are stored, read and deleted through FileService.
        "file :: api",
        // FileService answers the File row of a receipt.
        "file :: entities",
        // Open kernel.
        "security",
        // Open kernel.
        "shared",
        // Names are read through UserService, and the bank account through BankAccountLookup.
        "user :: api",
        // UserService answers User rows.
        "user :: entities",
    ],
)
class ModuleMetadata
