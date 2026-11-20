package dev.monitorcidadao.core.network.security

/** Pinning por hash SHA-256 (base64) da chave pública (SPKI). */
sealed interface PinningPolicy {
    data object None : PinningPolicy
    data class Spki(val host: String, val hashes: Set<String>) : PinningPolicy {
        init {
            require(hashes.isNotEmpty()) { "Pinning SPKI exige ao menos um hash" }
        }
    }
}

/** Identidade de cliente para mTLS. */
sealed interface ClientIdentity {
    data object None : ClientIdentity

    /** PKCS12 importado no Android Keystore sob [keystoreAlias] (ver [AndroidKeystoreIdentity]). */
    data class Pkcs12(val keystoreAlias: String) : ClientIdentity
}

/**
 * Segurança de transporte. **O MVP usa [Default] (tudo desligado).** Ligar é trocar este valor na
 * raiz de composição — e só com certificado sob nosso controle (não pinar Universal SSL da Cloudflare).
 */
data class TransportSecurity(
    val pinning: PinningPolicy = PinningPolicy.None,
    val clientIdentity: ClientIdentity = ClientIdentity.None,
) {
    val isEnabled: Boolean get() = pinning != PinningPolicy.None || clientIdentity != ClientIdentity.None

    companion object {
        val Default = TransportSecurity()
    }
}
