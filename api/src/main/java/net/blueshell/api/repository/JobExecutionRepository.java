package net.blueshell.api.repository;

import net.blueshell.api.base.BaseRepository;
import net.blueshell.api.model.JobExecution;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface JobExecutionRepository extends BaseRepository<JobExecution, UUID> {
    
    JobExecution findTopByJobClassOrderByCreatedAtDesc(String jobClass);
    
    Page<JobExecution> findByJobClassOrderByCreatedAtDesc(String jobClass, Pageable pageable);
    
    Page<JobExecution> findByStatusOrderByCreatedAtDesc(JobExecution.ExecutionStatus status, Pageable pageable);
    
    @Query("SELECT je FROM JobExecution je WHERE je.createdAt >= :since ORDER BY je.createdAt DESC")
    List<JobExecution> findRecentExecutions(@Param("since") LocalDateTime since);
    
    @Query("SELECT je.jobClass, COUNT(je) FROM JobExecution je WHERE je.createdAt >= :since GROUP BY je.jobClass")
    List<Object[]> getExecutionStatistics(@Param("since") LocalDateTime since);
}