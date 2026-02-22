//package net.blueshell.api.platform.config
//
//import io.jsonwebtoken.io.Decoders
//import jakarta.annotation.PostConstruct
//import org.springframework.beans.factory.annotation.Value
//import org.springframework.context.annotation.Profile
//import org.springframework.stereotype.Component
//
///**
// * Fail-fast guardrails for production-like environments.
// * Prevents booting with weak JWT secrets or insecure exposure toggles.
// */
//@Component
//@Profile("!test & !dev & !prod")
//class ProductionSecurityHardeningGuard(
//    @param:Value($$"${app.jwt.secret:}") private val jwtSecret: String,
//    @param:Value($$"${app.security.require-https:true}") private val requireHttps: Boolean,
//    @param:Value($$"${security.openapi.public.enabled:false}") private val openApiPublicEnabled: Boolean,
//) {
//    @PostConstruct
//    fun validate() {
//        require(requireHttps) {
//            "app.security.require-https must be true outside dev/test profiles"
//        }
//        require(!openApiPublicEnabled) {
//            "security.openapi.public.enabled must be false outside dev/test profiles"
//        }
//        require(jwtSecret.isNotBlank()) {
//            "app.jwt.secret must be configured"
//        }
//
//        val decoded = try {
//            Decoders.BASE64.decode(jwtSecret)
//        } catch (ex: Exception) {
//            throw IllegalStateException("app.jwt.secret must be Base64 encoded", ex)
//        }
//
//        require(decoded.size >= 64) {
//            "app.jwt.secret must decode to at least 64 bytes for HS512"
//        }
//    }
//}
