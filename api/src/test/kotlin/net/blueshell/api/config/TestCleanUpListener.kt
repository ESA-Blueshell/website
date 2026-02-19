package net.blueshell.api.config

import net.blueshell.api.platform.config.JobQueueProperties
import org.springframework.amqp.core.AmqpAdmin
import org.springframework.beans.factory.getBean
import org.springframework.beans.factory.getBeanProvider
import org.springframework.context.ApplicationContext
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.jdbc.datasource.DataSourceUtils
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry
import org.springframework.beans.factory.getBean
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
        stopRabbitListeners(context)
        purgeJobQueue(context)

        val dataSource = context.getBean<DataSource>()
        val conn = DataSourceUtils.getConnection(dataSource)
        try {
            conn.createStatement().use { st ->
                conn.autoCommit = true

                val currentDb = st.executeQuery("SELECT DATABASE()").use { rs ->
                    rs.next()
                    rs.getString(1)
                }
                check(currentDb == TEST_SCHEMA) {
                    "Refusing to wipe non-test database. Connected to '$currentDb', expected '$TEST_SCHEMA'."
                }

                val tables = conn.prepareStatement(
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

                st.execute("SET FOREIGN_KEY_CHECKS = 0")
                for (table in tables) {
                    st.execute("TRUNCATE TABLE `$TEST_SCHEMA`.`$table`")
                }
                st.execute("SET FOREIGN_KEY_CHECKS = 1")
            }
        } finally {
            DataSourceUtils.releaseConnection(conn, dataSource)
        }

        startRabbitListeners(context)
    }

    override fun afterTestMethod(testContext: TestContext) {
        val context = testContext.applicationContext
        stopRabbitListeners(context)
        purgeJobQueue(context)
    }

    private fun purgeJobQueue(context: ApplicationContext) {
        val amqpAdmin = context.getBeanProvider<AmqpAdmin>().ifAvailable ?: return
        val queueName = context.getBean<JobQueueProperties>().queueName
        amqpAdmin.purgeQueue(queueName, false)
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

    private fun startRabbitListeners(context: ApplicationContext) {
        val registry = context.getBeanProvider<RabbitListenerEndpointRegistry>().ifAvailable ?: return
        registry.listenerContainers.forEach { container ->
            if (!container.isRunning) {
                runCatching {
                    container.start()
                }
            }
        }
    }

    private companion object {
        const val TEST_SCHEMA = "blueshell-test"
        const val FLYWAY_V5_TABLE = "flyway_schema_history"
        const val FLYWAY_V3_TABLE = "schema_version"
        const val RABBIT_STOP_TIMEOUT_SECONDS = 15L
    }
}
