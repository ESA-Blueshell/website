import org.gradle.api.tasks.testing.Test
import java.io.File

/**
 * Runs only this CI shard's slice of the test classes, set by `SHARD_TOTAL` and the 1-based
 * `SHARD_INDEX`.
 *
 * A class runs on shard `floorMod(fqcn.hashCode(), SHARD_TOTAL) + 1`, so it always lands on the
 * same shard and a failure is easy to place. With either variable unset or out of range the task
 * runs every class.
 */
fun Test.shardByTestClass() {
    val shardTotal = System.getenv("SHARD_TOTAL")?.toIntOrNull()?.takeIf { it > 1 } ?: return
    val shardIndex = System.getenv("SHARD_INDEX")?.toIntOrNull()?.takeIf { it in 1..shardTotal } ?: return
    // The filter below is applied in doFirst, which Gradle does not hash, so
    // without these every shard has identical inputs and one shard's result is
    // served to the next FROM-CACHE. Declaring them makes each slice its own task.
    inputs.property("shardTotal", shardTotal)
    inputs.property("shardIndex", shardIndex)
    doFirst {
        val classes =
            testClassesDirs.asFileTree
                .matching { include("**/*Test.class", "**/*IT.class") }
                .files
                .mapNotNull { f ->
                    val root = testClassesDirs.firstOrNull { f.startsWith(it) } ?: return@mapNotNull null
                    f.relativeTo(root).path.removeSuffix(".class").replace(File.separatorChar, '.')
                }.filter { !it.contains('$') } // skip anonymous / nested $-classes
                .sorted()
        val mine = classes.filter { Math.floorMod(it.hashCode(), shardTotal) == shardIndex - 1 }
        logger.lifecycle("Shard $shardIndex/$shardTotal — ${mine.size}/${classes.size} test classes")
        filter {
            isFailOnNoMatchingTests = false
            if (mine.isEmpty()) {
                // An empty include list would match every class, so a shard with
                // none assigned includes a pattern that cannot match.
                includeTestsMatching("__no_match__shard_${shardIndex}__")
            } else {
                mine.forEach { includeTestsMatching(it) }
            }
        }
    }
}
