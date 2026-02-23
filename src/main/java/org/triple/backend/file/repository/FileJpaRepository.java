package org.triple.backend.file.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.triple.backend.file.entity.File;

public interface FileJpaRepository extends JpaRepository<File, Long> {
}
