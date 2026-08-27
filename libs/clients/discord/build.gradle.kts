plugins {
    id("openapi-client-conventions")
}

// Discord's published OpenAPI spec ships untagged and covers the entire
// public API (~500 operations). Generating the whole surface would balloon
// compile time and expose endpoints we never intend to call. Pre-filter the
// spec to a small allow-list of paths the website actually uses, then tag
// the survivors so the generator produces a single `DiscordApi`.
private val operationAllowList: Map<String, Set<String>> = mapOf(
    "/users/@me" to setOf("get"),
    "/guilds/{guild_id}" to setOf("get"),
    "/guilds/{guild_id}/members" to setOf("get"),
    "/guilds/{guild_id}/members/search" to setOf("get"),
    "/guilds/{guild_id}/members/{user_id}" to setOf("get", "patch"),
    "/guilds/{guild_id}/roles" to setOf("get"),
)

private val INJECTED_TAG = "Discord"

private val sourceSpec = rootProject.layout.projectDirectory.file("libs/openapi-specs/discord.json")
private val filteredSpec = layout.buildDirectory.file("discord-filtered.json")

val filterDiscordSpec = tasks.register("filterDiscordSpec") {
    description = "Filters libs/openapi-specs/discord.json to the operations the website calls and tags them."
    group = "openapi"

    inputs.file(sourceSpec)
    inputs.property("allowList", operationAllowList.mapValues { it.value.toList().sorted() })
    inputs.property("tag", INJECTED_TAG)
    outputs.file(filteredSpec)

    doLast {
        val source = sourceSpec.asFile
        val target = filteredSpec.get().asFile
        target.parentFile.mkdirs()

        @Suppress("UNCHECKED_CAST")
        val spec = groovy.json.JsonSlurper().parse(source) as MutableMap<String, Any?>

        @Suppress("UNCHECKED_CAST")
        val paths = spec["paths"] as MutableMap<String, Any?>
        val httpMethods = setOf("get", "post", "put", "patch", "delete", "head", "options")

        paths.keys.toList().forEach { path ->
            val allowedMethods = operationAllowList[path]
            if (allowedMethods == null) {
                paths.remove(path)
                return@forEach
            }
            @Suppress("UNCHECKED_CAST")
            val ops = paths[path] as MutableMap<String, Any?>
            ops.keys.toList().forEach { key ->
                if (key !in httpMethods) return@forEach
                if (key !in allowedMethods) {
                    ops.remove(key)
                    return@forEach
                }
                @Suppress("UNCHECKED_CAST")
                val opMap = ops[key] as MutableMap<String, Any?>
                opMap["tags"] = listOf(INJECTED_TAG)
            }
            if (ops.keys.none { it in httpMethods }) {
                paths.remove(path)
            }
        }

        // Discord's spec defines ~600 schemas in components.schemas; the
        // openapi-generator emits Java for every one regardless of `apis`
        // filtering, and several carry `@Max` values larger than Java's int
        // range. Prune to only the schemas transitively reachable from the
        // kept paths so the generated client stays small and compilable.
        @Suppress("UNCHECKED_CAST")
        val components = spec["components"] as? MutableMap<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val schemas = components?.get("schemas") as? MutableMap<String, Any?>
        if (schemas != null) {
            val seed = mutableSetOf<String>()
            collectSchemaRefs(paths, seed)
            // Also seed from any sibling components subsections we kept
            // (parameters/responses/etc.) so their schema refs stay alive.
            components.forEach { (key, value) ->
                if (key != "schemas") collectSchemaRefs(value, seed)
            }
            val reachable = mutableSetOf<String>()
            val queue = ArrayDeque(seed)
            while (queue.isNotEmpty()) {
                val name = queue.removeFirst()
                if (!reachable.add(name)) continue
                val schema = schemas[name] ?: continue
                val nested = mutableSetOf<String>()
                collectSchemaRefs(schema, nested)
                queue.addAll(nested - reachable)
            }
            schemas.keys.toList().forEach { if (it !in reachable) schemas.remove(it) }

            // Discord's OpenAPI 3.1 spec uses `type: "null"` as a marker-flag
            // shape on a few role-tag fields. openapi-generator can't synthesise
            // a Java type for that and emits a missing `ModelNull` import.
            // Rewrite to `type: "boolean"` so the property still appears on the
            // generated DTO with the natural "present == true" semantics.
            rewriteNullTypeProperties(schemas)

            // A few properties (e.g. GuildStickerResponse.type) both $ref an
            // integer enum and re-declare a narrowing `enum`. openapi-generator
            // 7.22 emits a broken inline enum for these — String constants on a
            // field typed as the referenced enum. Collapse them to the bare
            // $ref so the generator reuses the referenced enum as-is.
            rewriteRedundantEnumAllOf(schemas)
        }

        target.writeText(groovy.json.JsonOutput.toJson(spec))
    }
}

/**
 * Collapse `{ allOf: [ { $ref } ], enum: [...], ... }` shapes to a bare `{ $ref }`.
 * The redundant inline `enum` alongside a single `$ref` makes openapi-generator emit
 * a malformed inline enum (String-valued constants on a field typed as the referenced
 * enum), which fails to compile.
 */
fun rewriteRedundantEnumAllOf(node: Any?) {
    when (node) {
        is MutableMap<*, *> -> {
            @Suppress("UNCHECKED_CAST")
            val map = node as MutableMap<String, Any?>
            val allOf = map["allOf"] as? List<*>
            val ref = (allOf?.singleOrNull() as? Map<*, *>)?.get("\$ref") as? String
            if (ref != null && map.containsKey("enum")) {
                map.clear()
                map["\$ref"] = ref
                return
            }
            map.values.forEach { rewriteRedundantEnumAllOf(it) }
        }
        is List<*> -> node.forEach { rewriteRedundantEnumAllOf(it) }
    }
}

/** In-place fixup: replace `type: "null"` properties with `type: "boolean"` everywhere in the tree. */
fun rewriteNullTypeProperties(node: Any?) {
    when (node) {
        is MutableMap<*, *> -> {
            @Suppress("UNCHECKED_CAST")
            val map = node as MutableMap<String, Any?>
            if (map["type"] == "null") map["type"] = "boolean"
            map.values.forEach { rewriteNullTypeProperties(it) }
        }
        is List<*> -> node.forEach { rewriteNullTypeProperties(it) }
    }
}

/** Walks an OpenAPI fragment and collects every `#/components/schemas/<name>` reference. */
fun collectSchemaRefs(node: Any?, out: MutableSet<String>) {
    when (node) {
        is Map<*, *> -> node.forEach { (k, v) ->
            if (k == "\$ref" && v is String && v.startsWith("#/components/schemas/")) {
                out.add(v.removePrefix("#/components/schemas/"))
            } else {
                collectSchemaRefs(v, out)
            }
        }
        is List<*> -> node.forEach { collectSchemaRefs(it, out) }
    }
}

tasks.named("generate").configure { dependsOn(filterDiscordSpec) }

openApiClient {
    // Path is resolved against the repo root by the convention plugin, so this
    // string must be a root-relative path.
    specPath.set("libs/clients/discord/build/discord-filtered.json")
    apiPackage.set("net.blueshell.clients.discord.api")
    modelPackage.set("net.blueshell.clients.discord.model")
    packageName.set("net.blueshell.clients.discord.invoker")
    apis.set(listOf(INJECTED_TAG))
}
