package net.blueshell.api.contact

import org.springframework.modulith.ApplicationModule
import org.springframework.modulith.PackageInfo

/**
 * The anti-corruption layer in front of the external contact book: `ContactAdapter` and
 * `ContactListAdapter` as ports, the Brevo implementations behind them.
 *
 * The module owns no table. The id each external system knows a person by lives in
 * `external_id_mapping`, written by `sync`. The `contacts` and `contact_list*` tables that
 * predate that mapping are still in the schema but are read and written by nothing.
 */
@PackageInfo
@ApplicationModule(
    id = "contact",
    allowedDependencies = [
        // Open kernel.
        "shared",
        // DEBT. ContactData.toContactData maps a User into the shape pushed to an
        // external system. This wants a contact projection published through
        // user :: api.
        "user :: entities",
    ],
)
class ModuleMetadata
