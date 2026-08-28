package net.blueshell.api.domain.blog

import org.springframework.modulith.ApplicationModule
import org.springframework.modulith.PackageInfo

/**
 * Blog posts and the rules around publishing one: titles are unique, and the body is sanitised
 * against a jsoup safelist on the way in.
 *
 * Sanitising on write means reading a post is a plain read — nothing escapes or cleans the HTML
 * a second time on the response side.
 */
@PackageInfo
@ApplicationModule(
    id = "blog",
    allowedDependencies = [
        // Open kernel: BlogPermission extends the base evaluator.
        "security",
        // Open kernel.
        "shared",
    ],
)
class ModuleMetadata
