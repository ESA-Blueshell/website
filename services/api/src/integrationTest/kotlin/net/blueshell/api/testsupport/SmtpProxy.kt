package net.blueshell.api.testsupport

import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * A local SMTP relay in front of a real server, so a test can count connections, cap them per
 * client address, and break them without taking the server down. It relays one line at a time:
 * that is enough for SMTP, and it lets a drop wait until a message's acknowledgement has reached
 * the client.
 */
class SmtpProxy(
    private val targetHost: String,
    private val targetPort: Int,
) : AutoCloseable {
    private val server = ServerSocket(0, BACKLOG, InetAddress.getByName(LOOPBACK))
    private val relays = ConcurrentHashMap<Socket, Relay>()
    private val connectionsPerIp = ConcurrentHashMap<String, AtomicInteger>()
    private val acceptedConnections = AtomicInteger()
    private val delivered = AtomicInteger()
    private val limit = AtomicInteger(Int.MAX_VALUE)
    private val dropAt = AtomicInteger(0)

    private val acceptor =
        Thread(::acceptConnections, "smtp-proxy-acceptor").apply {
            isDaemon = true
            start()
        }

    val port: Int get() = server.localPort

    fun acceptedConnections(): Int = acceptedConnections.get()

    /** Caps the connections accepted from one client address until [reset] or a larger cap. */
    fun limitPerIp(count: Int) {
        limit.set(count.coerceAtLeast(1))
    }

    /**
     * Schedules a drop once [count] messages have been acknowledged. The next command after that
     * is cut instead of relayed, so breaking a connection cannot swallow an acknowledgement the
     * client already has and turn one delivered message into two.
     */
    fun dropAfterMessages(count: Int) {
        dropAt.set(count)
    }

    fun reset() {
        closeRelays()
        connectionsPerIp.clear()
        acceptedConnections.set(0)
        delivered.set(0)
        limit.set(Int.MAX_VALUE)
        dropAt.set(0)
    }

    fun dropAll() = closeRelays()

    override fun close() {
        runCatching { server.close() }
        closeRelays()
        runCatching { acceptor.join(JOIN_MILLIS) }
    }

    private fun acceptConnections() {
        while (!server.isClosed) {
            val client = runCatching(server::accept).getOrNull() ?: break
            relay(client)
        }
    }

    private fun relay(client: Socket) {
        val address = client.inetAddress.hostAddress ?: LOOPBACK
        val fromThisIp = connectionsPerIp.computeIfAbsent(address) { AtomicInteger() }
        if (fromThisIp.incrementAndGet() > limit.get()) {
            refuse(client)
            return
        }
        val upstream = runCatching { Socket(targetHost, targetPort) }.getOrNull()
        if (upstream == null) {
            runCatching(client::close)
            return
        }
        val connection = Relay(client, upstream)
        relays[client] = connection
        acceptedConnections.incrementAndGet()
        connection.start()
    }

    private fun refuse(client: Socket) {
        runCatching {
            client.getOutputStream().use { out ->
                out.write(REFUSAL.toByteArray(StandardCharsets.US_ASCII))
                out.flush()
            }
        }
        runCatching(client::close)
    }

    private fun closeRelays() {
        relays.values.toList().forEach { it.close() }
        relays.clear()
    }

    private inner class Relay(
        private val client: Socket,
        private val upstream: Socket,
    ) {
        @Volatile
        private var dropPending = false

        @Volatile
        private var inData = false

        @Volatile
        private var awaitingAck = false

        private val toServer = Thread(::clientToServer, "smtp-proxy-upstream").apply { isDaemon = true }
        private val toClient = Thread(::serverToClient, "smtp-proxy-downstream").apply { isDaemon = true }

        fun start() {
            toServer.start()
            toClient.start()
        }

        fun close() {
            runCatching(client::close)
            runCatching(upstream::close)
        }

        private fun clientToServer() {
            client.getInputStream().bufferedReader(StandardCharsets.US_ASCII).use { input ->
                upstream.getOutputStream().bufferedWriter(StandardCharsets.US_ASCII).use { output ->
                    runCatching {
                        var line = input.readLine()
                        while (line != null && !dropPending) {
                            output.write(line)
                            output.write(CRLF)
                            output.flush()
                            when {
                                line.equals("DATA", ignoreCase = true) -> inData = true
                                inData && line == "." -> {
                                    inData = false
                                    awaitingAck = true
                                }
                            }
                            line = input.readLine()
                        }
                        if (dropPending) closeRelays()
                    }.onFailure { close() }
                }
            }
        }

        private fun serverToClient() {
            upstream.getInputStream().bufferedReader(StandardCharsets.US_ASCII).use { input ->
                client.getOutputStream().bufferedWriter(StandardCharsets.US_ASCII).use { output ->
                    runCatching {
                        while (true) {
                            val line = input.readLine() ?: break
                            output.write(line)
                            output.write(CRLF)
                            output.flush()
                            if (awaitingAck && line.startsWith("250")) {
                                awaitingAck = false
                                val at = dropAt.get()
                                if (at > 0 && delivered.incrementAndGet() >= at && dropAt.compareAndSet(at, 0)) {
                                    dropPending = true
                                }
                            }
                        }
                    }.onFailure { close() }
                }
            }
        }
    }

    private companion object {
        const val LOOPBACK = "127.0.0.1"
        const val BACKLOG = 50
        const val JOIN_MILLIS = 1000L
        const val CRLF = "\r\n"
        const val REFUSAL = "421 4.7.0 Too many connections, try again later\r\n"
    }
}
