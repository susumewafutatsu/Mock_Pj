package com.example.demo.repository;

import com.example.demo.domain.model.Subject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

// TODO: Add custom query methods:
// - findByName(String name)
@Repository
public interface SubjectRepository extends JpaRepository<Subject, Long> {

}
