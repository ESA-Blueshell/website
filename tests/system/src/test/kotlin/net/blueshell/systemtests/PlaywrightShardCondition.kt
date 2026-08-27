package net.blueshell.systemtests

import org.junit.jupiter.api.extension.ConditionEvaluationResult
import org.junit.jupiter.api.extension.ExecutionCondition
import org.junit.jupiter.api.extension.ExtensionContext

/**
 * Skips test classes that don't belong to this shard. Replaces the
 * SHARD_TOTAL/SHARD_INDEX env-var + Gradle-doFirst filter the website
 * carried before — now sharding is per-class via system properties
 * `-Dtest.shard.index=N -Dtest.shard.count=M`, matching personal-stack-2.
 *
 * Partition is stable: a class with a given FQCN always lands on the
 * same shard, so failure triage is straightforward.
 */
class PlaywrightShardCondition : ExecutionCondition {
    override fun evaluateExecutionCondition(context: ExtensionContext): ConditionEvaluationResult {
        val total = TestEnvironment.shardCount ?: return ConditionEvaluationResult.enabled(NO_SHARDING)
        val index = TestEnvironment.shardIndex
            ?: return ConditionEvaluationResult.disabled("test.shard.index unset (count=$total)")
        if (index !in 1..total) {
            return ConditionEvaluationResult.disabled("test.shard.index=$index outside 1..$total")
        }
        val fqcn = context.testClass.map { it.name }.orElse(null)
            ?: return ConditionEvaluationResult.enabled("no test class — skipping shard check")
        val mine = Math.floorMod(fqcn.hashCode(), total) == index - 1
        return if (mine) {
            ConditionEvaluationResult.enabled("shard $index/$total owns $fqcn")
        } else {
            ConditionEvaluationResult.disabled("shard $index/$total does not own $fqcn")
        }
    }

    companion object {
        private const val NO_SHARDING = "sharding disabled (test.shard.count unset)"
    }
}
