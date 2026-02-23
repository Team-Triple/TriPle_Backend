package org.triple.backend.file.unit.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.triple.backend.common.annotation.RepositoryTest;
import org.triple.backend.file.entity.File;
import org.triple.backend.file.repository.FileJpaRepository;

import static org.assertj.core.api.Assertions.assertThat;

@RepositoryTest
class FileJpaRepositoryTest {

    @Autowired
    private FileJpaRepository fileJpaRepository;

    @Test
    @DisplayName("파일 엔티티를 저장할 수 있다.")
    void 파일_엔티티_저장() {
        // given
        File file = File.of(1L, "uploads/uploaded/1/test.jpg");

        // when
        File savedFile = fileJpaRepository.save(file);
        File foundedFile = fileJpaRepository.findById(savedFile.getId()).orElseThrow();

        // then
        assertThat(foundedFile)
                .extracting(File::getId,File::getKey,File::getOwnerId)
                        .containsExactly(savedFile.getId(), "uploads/uploaded/1/test.jpg", 1L);
    }
}
