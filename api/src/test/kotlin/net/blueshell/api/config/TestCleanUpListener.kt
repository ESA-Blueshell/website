package net.blueshell.api.config

import net.blueshell.api.platform.config.JobQueueProperties
import org.flywaydb.core.Flyway
import org.springframework.amqp.core.AmqpAdmin
import org.springframework.amqp.core.Queue
import org.springframework.amqp.rabbit.listener.AbstractMessageListenerContainer
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry
import org.springframework.beans.factory.getBean
import org.springframework.beans.factory.getBeanProvider
import org.springframework.beans.factory.getBeansOfType
import org.springframework.context.ApplicationContext
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.test.context.TestContext
import org.springframework.test.context.TestExecutionListener
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.sql.DataSource

/**
 * Test listener that truncates all test-schema tables between tests (excluding Flyway history).
 * Hard guards against accidental non-test schema truncation.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
class TestCleanUpListener : TestExecutionListener {

    override fun beforeTestMethod(testContext: TestContext) {
        val context = testContext.applicationContext
        cleanRabbitState(context)

        val dataSource = context.getBean<DataSource>()
        ensureTestSchemaInitialized(context, dataSource)
        truncateAllUserTables(dataSource)
    }

    override fun afterTestMethod(testContext: TestContext) {
        val context = testContext.applicationContext
        cleanRabbitState(context)
    }

    private fun cleanRabbitState(context: ApplicationContext) {
        stopRabbitListeners(context)
        purgeKnownQueues(context)
    }

    private fun purgeKnownQueues(context: ApplicationContext) {
        val amqpAdmin = context.getBeanProvider<AmqpAdmin>().ifAvailable ?: return
        resolveQueueNames(context).forEach { queueName ->
            runCatching {
                amqpAdmin.purgeQueue(queueName, false)
            }
        }
    }

    private fun resolveQueueNames(context: ApplicationContext): Set<String> {
        val queueNames = linkedSetOf<String>()
        context.getBeanProvider<JobQueueProperties>().ifAvailable?.queueName?.let(queueNames::add)
        context.getBeansOfType<Queue>().values.mapTo(queueNames) { it.name }
        context.getBeanProvider<RabbitListenerEndpointRegistry>().ifAvailable
            ?.listenerContainers
            ?.forEach { container ->
                if (container is AbstractMessageListenerContainer) {
                    container.queueNames.forEach(queueNames::add)
                }
            }

        return queueNames
            .asSequence()
            .map(String::trim)
            .filter { it.isNotEmpty() && !it.startsWith("amq.") }
            .toSet()
    }

    private fun stopRabbitListeners(context: ApplicationContext) {
        val registry = context.getBeanProvider<RabbitListenerEndpointRegistry>().ifAvailable ?: return
        val containers = registry.listenerContainers.toList()
        if (containers.isEmpty()) return

        val stopLatch = CountDownLatch(containers.size)
        containers.forEach { container ->
            if (!container.isRunning) {
                stopLatch.countDown()
            } else {
                runCatching {
                    container.stop {
                        stopLatch.countDown()
                    }
                }.onFailure {
                    stopLatch.countDown()
                }
            }
        }
        stopLatch.await(RABBIT_STOP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    }

    private fun ensureTestSchemaInitialized(context: ApplicationContext, dataSource: DataSource) {
        val hasTables = withConnection(dataSource) { conn ->
            requireTestSchema(conn)
            loadUserTables(conn).isNotEmpty()
        }
        if (hasTables) {
            return
        }

        val flyway = context.getBeanProvider<Flyway>().ifAvailable
            ?: error("Flyway bean not found in test context; cannot initialize '$TEST_SCHEMA' schema.")
        flyway.migrate()

        val initialized = withConnection(dataSource) { conn ->
            requireTestSchema(conn)
            loadUserTables(conn).isNotEmpty()
        }
        check(initialized) {
            "Flyway migration finished but '$TEST_SCHEMA' still has no application tables."
        }
    }

    private fun truncateAllUserTables(dataSource: DataSource) {
        withConnection(dataSource) { conn ->
            requireTestSchema(conn)
            val tables = loadUserTables(conn)
            if (tables.isEmpty()) {
                return@withConnection
            }

            conn.createStatement().use { st ->
                st.execute("SET FOREIGN_KEY_CHECKS = 0")
                for (table in tables) {
                    st.execute("TRUNCATE TABLE `$TEST_SCHEMA`.`$table`")
                }
                st.execute("SET FOREIGN_KEY_CHECKS = 1")
            }
        }
    }

    private fun requireTestSchema(conn: java.sql.Connection) {
        val currentDb = conn.createStatement().use { st ->
            st.executeQuery("SELECT DATABASE()").use { rs ->
                rs.next()
                rs.getString(1)
            }
        }
        check(currentDb == TEST_SCHEMA) {
            "Refusing to wipe non-test database. Connected to '$currentDb', expected '$TEST_SCHEMA'."
        }
    }

    private fun loadUserTables(conn: java.sql.Connection): List<String> {
        return conn.prepareStatement(
            "SELECT TABLE_NAME " +
                    "FROM INFORMATION_SCHEMA.TABLES " +
                    "WHERE TABLE_SCHEMA = ? AND TABLE_TYPE = 'BASE TABLE' " +
                    "AND TABLE_NAME NOT IN (?, ?)"
        ).use { ps ->
            ps.setString(1, TEST_SCHEMA)
            ps.setString(2, FLYWAY_V5_TABLE)
            ps.setString(3, FLYWAY_V3_TABLE)
            ps.executeQuery().use { rs ->
                val names = mutableListOf<String>()
                while (rs.next()) {
                    names.add(rs.getString(1))
                }
                names
            }
        }
    }

    private fun <T> withConnection(dataSource: DataSource, block: (java.sql.Connection) -> T): T {
        return dataSource.connection.use { conn ->
            conn.autoCommit = true
            block(conn)
        }
    }

    private companion object {
        const val TEST_SCHEMA = "blueshell-test"
        const val FLYWAY_V5_TABLE = "flyway_schema_history"
        const val FLYWAY_V3_TABLE = "schema_version"
        const val RABBIT_STOP_TIMEOUT_SECONDS = 15L
    }
}
