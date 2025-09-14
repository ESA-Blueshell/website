package net.blueshell.api.controller;

import lombok.RequiredArgsConstructor;
import net.blueshell.api.model.JobExecution;
import net.blueshell.api.repository.JobExecutionRepository;
import net.blueshell.api.service.job.JobSchedulerService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/admin/jobs")
@RequiredArgsConstructor
public class JobMonitoringController {
    
    private final JobExecutionRepository repository;

    /**
     * Get job executions
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<JobExecution>> getJobExecutions(
            @RequestParam(required = false) String jobClass,
            @RequestParam(required = false) JobExecution.ExecutionStatus status,
            Pageable pageable) {
        
        Page<JobExecution> executions;
        
        if (jobClass != null) {
            executions = repository.findByJobClassOrderByCreatedAtDesc(jobClass, pageable);
        } else if (status != null) {
            executions = repository.findByStatusOrderByCreatedAtDesc(status, pageable);
        } else {
            executions = repository.findAll(pageable);
        }
        
        return ResponseEntity.ok(executions);
    }
    
    /**
     * Get recent job executions
     */
    @GetMapping("/recent")
    @PreAuthorize("hasRole('BOARD')")
    public ResponseEntity<List<JobExecution>> getRecentExecutions(
            @RequestParam(defaultValue = "24") int hours) {
        
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        List<JobExecution> executions = repository.findRecentExecutions(since);
        
        return ResponseEntity.ok(executions);
    }
    
    /**
     * Get job statistics
     */
    @GetMapping("/statistics")
    @PreAuthorize("hasRole('BOARD')")
    public ResponseEntity<List<Object[]>> getJobStatistics(
            @RequestParam(defaultValue = "24") int hours) {
        
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        List<Object[]> stats = repository.getExecutionStatistics(since);
        
        return ResponseEntity.ok(stats);
    }
}