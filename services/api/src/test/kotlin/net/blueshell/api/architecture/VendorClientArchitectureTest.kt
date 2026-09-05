package net.blueshell.api.architecture

import net.blueshell.api.architecture.support.ArchJUnitTestBase
import net.blueshell.api.architecture.support.ArchModules
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * API ADR-019: an external model does not enter a domain. A generated vendor client under
 * `net.blueshell.clients` is that external model in its rawest form, so exactly one module owns
 * each vendor and wraps it behind an interface the rest of the application speaks. Anyone else
 * calls that interface.
 *
 * The record still describes the vendor wrappers as living in `platform/integration/{system}`,
 * packages the architecture ADR-003 flattening removed; ownership is by module now, which is what
 * [VENDOR_OWNERS] states. Modulith polices `net.blueshell.api` and stops at the module boundary,
 * so nothing but this rule looks at a third-party import.
 *
 * The reaches that exist are pinned in [PINNED] rather than fixed here, the way
 * [CrossModuleWebAccessArchitectureTest] pins its own. Each is a line in the file, so dropping one
 * is a visible diff. Pinned at six reaches, all made by one class.
 */
class VendorClientArchitectureTest : ArchJUnitTestBase(ArchitecturePackages.ROOT) {

    private companion object {
        const val VENDOR_ROOT = "net.blueshell.clients"

        /**
         * The module that owns each vendor client and wraps it. A vendor absent from this map has
         * no owner, which is itself a violation — adding a client means deciding who wraps it.
         */
        val VENDOR_OWNERS = mapOf(
            "brevo" to "contact",
            "discord" to "sync",
        )

        /**
         * Vendor reaches from outside the owning module that existed when this rule landed, as
         * `<reaching module> -> <vendor type>`.
         */
        val PINNED = setOf(
            // DEBT. BrevoTargetStrategy drives ContactsApi for the list catalog and folder names
            // while pushing membership through contact's ContactListAdapter — one integration
            // behind two ports, one of them raw. Removing these means publishing the catalog side
            // through contact :: api so cohort speaks only to the wrapper. Tracked separately.
            "cohort -> net.blueshell.clients.brevo.api.ContactsApi",
            "cohort -> net.blueshell.clients.brevo.model.GetContactsSortParameter",
            "cohort -> net.blueshell.clients.brevo.model.GetFolder",
            "cohort -> net.blueshell.clients.brevo.model.GetFolders200Response",
            "cohort -> net.blueshell.clients.brevo.model.GetLists200Response",
            "cohort -> net.blueshell.clients.brevo.model.GetLists200ResponseListsInner",
        )
    }

    @Test
    fun `only the owning module imports a vendor client`() {
        val offenders = measureReaches()
            .filterKeys { it !in PINNED }
            .flatMap { (reach, origins) -> origins.map { "$reach   from $it" } }
            .sorted()

        assertThat(offenders)
            .describedAs(
                "API ADR-019: a generated vendor client belongs to the module that wraps it. Call " +
                    "that module's published interface instead, or add the reach to PINNED if it " +
                    "is being cleaned up separately",
            )
            .isEmpty()
    }

    @Test
    fun `no pinned reach outlives the code that made it`() {
        val measured = measureReaches().keys

        val stale = PINNED.filterNot { it in measured }.sorted()

        assertThat(stale)
            .describedAs(
                "these reaches are gone — drop them from PINNED so the ratchet cannot slip back",
            )
            .isEmpty()
    }

    /**
     * Every `<module> -> <vendor type>` reach from outside the owning module, mapped to the classes
     * that make it. A type directly under the base package belongs to no module and owns no vendor,
     * so its reaches count too.
     */
    private fun measureReaches(): Map<String, Set<String>> {
        val reaches = mutableMapOf<String, MutableSet<String>>()

        importedClasses.forEach { origin ->
            val originModule = ArchModules.moduleOf(origin)
            origin.directDependenciesFromSelf.forEach { dependency ->
                val target = dependency.targetClass
                val vendor = vendorOf(target.packageName) ?: return@forEach
                if (VENDOR_OWNERS[vendor] == originModule) return@forEach
                reaches.getOrPut("${originModule ?: "<root>"} -> ${target.fullName}") { mutableSetOf() }
                    .add(origin.fullName)
            }
        }

        return reaches
    }

    private fun vendorOf(packageName: String): String? =
        packageName.removePrefix("$VENDOR_ROOT.")
            .takeIf { packageName.startsWith("$VENDOR_ROOT.") }
            ?.substringBefore('.')
            ?.takeIf { it.isNotEmpty() }
}
