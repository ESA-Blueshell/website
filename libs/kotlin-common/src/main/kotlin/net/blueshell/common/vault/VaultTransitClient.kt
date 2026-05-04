package net.blueshell.common.vault

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import org.springframework.vault.core.VaultTemplate

interface VaultTransitClient {
    fun readPublicKeys(keyName: String): List<VaultPublicKey>
    fun sign(keyName: String, input: String): String
}

data class VaultPublicKey(val keyVersion: Int, val publicKeyPem: String)

@Component
@ConditionalOnProperty("auth.transit.enabled", havingValue = "true")
class SpringVaultTransitClient(
    private val vaultTemplate: VaultTemplate,
) : VaultTransitClient {

    override fun readPublicKeys(keyName: String): List<VaultPublicKey> {
        val response = vaultTemplate.read("transit/keys/$keyName")
            ?: error("No transit key found at transit/keys/$keyName")

        @Suppress("UNCHECKED_CAST")
        val keys = response.data?.get("keys") as? Map<String, Map<String, Any?>>
            ?: error("Transit key '$keyName' does not expose key versions")

        return keys.entries
            .sortedBy { it.key.toIntOrNull() ?: 0 }
            .mapNotNull { (version, info) ->
                val publicKeyPem = info["public_key"]?.toString() ?: return@mapNotNull null
                VaultPublicKey(
                    keyVersion = version.toInt(),
                    publicKeyPem = publicKeyPem,
                )
            }
            .ifEmpty { error("Transit key '$keyName' does not expose RSA public keys") }
    }

    override fun sign(keyName: String, input: String): String {
        val body = mapOf(
            "input" to input,
            "hash_algorithm" to "sha2-256",
            "signature_algorithm" to "pkcs1v15",
            "prehashed" to false,
        )

        val response = vaultTemplate.write("transit/sign/$keyName", body)
            ?: error("Empty response signing with key $keyName")

        val signature = response.data?.get("signature")?.toString()
            ?: error("Transit signing response did not include a signature for key '$keyName'")

        return signature.substringAfterLast(":")
    }
}
