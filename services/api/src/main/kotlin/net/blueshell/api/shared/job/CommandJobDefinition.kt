package net.blueshell.api.shared.job

import net.blueshell.api.shared.command.Command

/**
 * A job definition whose payload is a Command object.
 *
 * The command is serialized to JSON when the job is enqueued and deserialized
 * back to its original type when the job runs. This allows any Command to gain
 * full job lifecycle tracking (QUEUED → RUNNING → SUCCESS / FAILED / DEAD),
 * retry semantics, deduplication, and metrics at zero per-command boilerplate.
 *
 * Implement by declaring an object:
 *
 *   object MyCommandJob : CommandJobDefinition<MyCommand> {
 *       override val type = "my-domain.my-command"
 *       override val payloadType = MyCommand::class.java
 *   }
 *
 * Then create a handler that extends AbstractCommandJobHandler.
 */
interface CommandJobDefinition<C : Command<*>> : JobDefinition<C>
