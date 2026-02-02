package net.blueshell.api.base;

/**
 * Marker interface for all jobs.
 * Job classes should implement this interface and be annotated with @Component.
 */
public interface Job {
    /**
     * Execute the job
     */
    void execute() throws Exception;
}