package net.blueshell.api.service.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.blueshell.api.base.Job;
import net.blueshell.api.model.JobExecution;
import net.blueshell.api.repository.JobExecutionRepository;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class JobExecutionService {
    
    private final JobExecutionRepository repository;
    private final ObjectMapper objectMapper;
    
    /**
     * Execute a job asynchronously with automatic retry and auditing
     */
    @Async
    @Retryable(
        retryFor = {Exception.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 5000, multiplier = 2) // 5s, 10s, 20s
    )
    public void executeJob(Job job, Map<String, Object> parameters) {
        String jobClass = job.getClass().getSimpleName();
        LocalDateTime startTime = LocalDateTime.now();
        
        JobExecution execution = createJobExecution(jobClass, parameters, startTime);
        
        try {
            log.info("Starting job execution: {} with parameters: {}", jobClass, parameters);
            
            job.execute();
            
            LocalDateTime endTime = LocalDateTime.now();
            long executionTimeMs = ChronoUnit.MILLIS.between(startTime, endTime);
            
            execution.setStatus(JobExecution.ExecutionStatus.COMPLETED);
            execution.setCompletedAt(endTime);
            execution.setExecutionTimeMs(executionTimeMs);
            
            repository.save(execution);
            
            log.info("Job execution completed: {} in {}ms", jobClass, executionTimeMs);
            
        } catch (Exception e) {
            handleJobFailure(execution, e, startTime);

            // Re-throw as RuntimeException to trigger retry without exposing checked exceptions
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            } else {
                throw new RuntimeException("Job execution failed: " + e.getMessage(), e);
            }
        }
    }
    
    /**
     * Recovery method called after all retry attempts are exhausted
     */
    @Recover
    public void recoverJobExecution(Exception ex, Job job, Map<String, Object> parameters) {
        String jobClass = job.getClass().getSimpleName();
        
        log.error("Job execution permanently failed after all retries: {} - {}", 
                jobClass, ex.getMessage(), ex);
        
        // Find the last execution record and mark it as permanently failed
        JobExecution lastExecution = repository.findTopByJobClassOrderByCreatedAtDesc(jobClass);
        if (lastExecution != null && lastExecution.getStatus() == JobExecution.ExecutionStatus.RETRYING) {
            LocalDateTime endTime = LocalDateTime.now();
            long executionTimeMs = ChronoUnit.MILLIS.between(lastExecution.getStartedAt(), endTime);
            
            lastExecution.setStatus(JobExecution.ExecutionStatus.FAILED);
            lastExecution.setCompletedAt(endTime);
            lastExecution.setExecutionTimeMs(executionTimeMs);
            lastExecution.setErrorMessage("Permanently failed after all retry attempts: " + ex.getMessage());
            
            repository.save(lastExecution);
        }
    }
    
    private JobExecution createJobExecution(String jobClass, Map<String, Object> parameters, LocalDateTime startTime) {
        JobExecution execution = new JobExecution();
        execution.setJobClass(jobClass);
        execution.setStartedAt(startTime);
        execution.setStatus(JobExecution.ExecutionStatus.STARTED);
        
        try {
            execution.setParameters(objectMapper.writeValueAsString(parameters));
        } catch (Exception e) {
            log.warn("Failed to serialize job parameters: {}", e.getMessage());
            execution.setParameters("{}");
        }
        
        return repository.save(execution);
    }
    
    private void handleJobFailure(JobExecution execution, Exception e, LocalDateTime startTime) {
        LocalDateTime endTime = LocalDateTime.now();
        long executionTimeMs = ChronoUnit.MILLIS.between(startTime, endTime);
        
        execution.setStatus(JobExecution.ExecutionStatus.RETRYING);
        execution.setCompletedAt(endTime);
        execution.setExecutionTimeMs(executionTimeMs);
        execution.setErrorMessage(e.getMessage());
        execution.setStackTrace(getStackTrace(e));
        execution.setRetryAttempt(execution.getRetryAttempt() + 1);
        
        repository.save(execution);
        
        log.warn("Job execution failed (attempt {}): {} - {}", 
                execution.getRetryAttempt(), execution.getJobClass(), e.getMessage());
    }
    
    private String getStackTrace(Exception e) {
        java.io.StringWriter sw = new java.io.StringWriter();
        java.io.PrintWriter pw = new java.io.PrintWriter(sw);
        e.printStackTrace(pw);
        return sw.toString();
    }
}