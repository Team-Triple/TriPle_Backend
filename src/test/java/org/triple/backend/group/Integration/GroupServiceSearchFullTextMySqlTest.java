package org.triple.backend.group.Integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.triple.backend.group.dto.response.GroupCursorResponseDto;
import org.triple.backend.group.entity.group.Group;
import org.triple.backend.group.entity.group.GroupKind;
import org.triple.backend.group.repository.GroupJpaRepository;
import org.triple.backend.group.service.GroupService;
import org.triple.backend.user.service.UserFinder;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({GroupService.class, UserFinder.class})
@Testcontainers(disabledWithoutDocker = true)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQLDialect"
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class GroupServiceSearchFullTextMySqlTest {

    private static final String FULLTEXT_INDEX_NAME = "idx_travel_group_name_description";

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.3")
            .withDatabaseName("triple_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void setDatasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
    }

    @Autowired
    private GroupService groupService;

    @Autowired
    private GroupJpaRepository groupJpaRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        groupJpaRepository.deleteAllInBatch();
        ensureFullTextIndex();
    }

    @Test
    @DisplayName("FULLTEXT 검색은 PUBLIC 그룹 중 키워드와 매칭되는 그룹만 조회한다")
    void FULLTEXT_검색은_PUBLIC_그룹_중_키워드와_매칭되는_그룹만_조회한다() {
        // given
        groupJpaRepository.saveAndFlush(Group.create(GroupKind.PUBLIC, "jeju travel crew", "weekend trip", "thumb", 10));
        groupJpaRepository.saveAndFlush(Group.create(GroupKind.PUBLIC, "busan club", "jeju travel plan", "thumb", 10));
        groupJpaRepository.saveAndFlush(Group.create(GroupKind.PUBLIC, "seoul walkers", "hangang walk", "thumb", 10));
        groupJpaRepository.saveAndFlush(Group.create(GroupKind.PRIVATE, "jeju travel private", "jeju travel secret", "thumb", 10));

        // when
        GroupCursorResponseDto response = groupService.search("jeju travel", null, 10);

        // then
        assertThat(response.items()).hasSize(2);
        assertThat(response.items())
                .extracting(GroupCursorResponseDto.GroupSummaryDto::name)
                .containsExactlyInAnyOrder("jeju travel crew", "busan club");
        assertThat(response.hasNext()).isFalse();
        assertThat(response.nextCursor()).isNull();
    }

    @Test
    @DisplayName("FULLTEXT 검색은 cursor 기반으로 다음 페이지를 중복 없이 조회한다")
    void FULLTEXT_검색은_cursor_기반으로_다음_페이지를_중복_없이_조회한다() {
        // given
        for (int i = 1; i <= 12; i++) {
            groupJpaRepository.saveAndFlush(Group.create(GroupKind.PUBLIC, "jeju travel group " + i, "trip plan", "thumb", 10));
        }

        // when
        GroupCursorResponseDto first = groupService.search("jeju travel", null, 5);
        GroupCursorResponseDto second = groupService.search("jeju travel", first.nextCursor(), 5);

        // then
        assertThat(first.items()).hasSize(5);
        assertThat(first.hasNext()).isTrue();
        assertThat(first.nextCursor()).isNotNull();

        assertThat(second.items()).hasSize(5);
        assertThat(second.hasNext()).isTrue();
        assertThat(second.nextCursor()).isNotNull();
        assertThat(second.items())
                .allSatisfy(item -> assertThat(item.groupId()).isLessThan(first.nextCursor()));

        List<Long> firstIds = first.items().stream().map(GroupCursorResponseDto.GroupSummaryDto::groupId).toList();
        List<Long> secondIds = second.items().stream().map(GroupCursorResponseDto.GroupSummaryDto::groupId).toList();
        List<Long> mergedIds = new ArrayList<>(firstIds);
        mergedIds.addAll(secondIds);

        assertThat(mergedIds).doesNotHaveDuplicates();
    }

    private void ensureFullTextIndex() {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.statistics
                WHERE table_schema = DATABASE()
                  AND table_name = 'travel_group'
                  AND index_name = ?
                """, Integer.class, FULLTEXT_INDEX_NAME);

        if (count != null && count == 0) {
            jdbcTemplate.execute("""
                    ALTER TABLE travel_group
                    ADD FULLTEXT INDEX idx_travel_group_name_description (name, description)
                    """);
        }
    }
}
