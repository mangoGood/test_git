package com.synctask.repository;

import com.synctask.entity.Workflow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface WorkflowRepository extends JpaRepository<Workflow, String> {
    @Query("SELECT w FROM Workflow w WHERE w.userId = :userId ORDER BY w.createdAt DESC")
    Page<Workflow> findByUserId(@Param("userId") Long userId, Pageable pageable);
    
    List<Workflow> findByUserId(Long userId);
    List<Workflow> findByUserIdAndStatus(Long userId, com.synctask.entity.WorkflowStatus status);
}
