package net.blueshell.api.user.domain.sealing

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import org.springframework.vault.core.VaultTemplate
import java.util.Base64

/**
 * Seals through Vault Transit, so the key never reaches the api and Vault's audit log records
 * every opening. Each key is a derived key: the context of each value goes along with it.
 */
@Component
@ConditionalOnProperty("privacy.sealing", havingValue = "vault")
class VaultTransitSealer(
    private val vault: VaultTemplate,
) : Sealer {
    override fun seal(
        key: String,
        values: List<Sealed>,
    ): List<String> =
        batch("transit/encrypt/$key", values, "ciphertext") { mapOf("plaintext" to encode(it.value), "context" to encode(it.context)) }

    override fun open(
        key: String,
        values: List<Sealed>,
    ): List<String> =
        batch("transit/decrypt/$key", values, "plaintext") { mapOf("ciphertext" to it.value, "context" to encode(it.context)) }
            .map { String(Base64.getDecoder().decode(it)) }

    // Transit's own rewrap: Vault opens and seals again inside itself, so no plaintext is answered.
    override fun rewrap(
        key: String,
        values: List<Sealed>,
    ): List<String?> =
        results("transit/rewrap/$key", values) { mapOf("ciphertext" to it.value, "context" to encode(it.context)) }
            .map { result -> result["ciphertext"]?.toString()?.takeIf { result["error"] == null } }

    private fun batch(
        path: String,
        values: List<Sealed>,
        field: String,
        item: (Sealed) -> Map<String, String>,
    ): List<String> = results(path, values, item).map { answerOf(it, field) }

    private fun results(
        path: String,
        values: List<Sealed>,
        item: (Sealed) -> Map<String, String>,
    ): List<Map<String, Any?>> {
        if (values.isEmpty()) return emptyList()
        val response =
            try {
                vault.write(path, mapOf("batch_input" to values.map(item)))
            } catch (refused: RuntimeException) {
                throw SealingUnavailable(refused)
            }

        @Suppress("UNCHECKED_CAST")
        return response?.data?.get("batch_results") as? List<Map<String, Any?>> ?: throw SealingUnavailable()
    }

    private fun answerOf(
        result: Map<String, Any?>,
        field: String,
    ): String {
        if (result["error"] != null) throw SealedValueUnopenable(IllegalStateException(result["error"].toString()))
        return result[field]?.toString() ?: throw SealingUnavailable()
    }

    private fun encode(text: String): String = Base64.getEncoder().encodeToString(text.toByteArray())
}
