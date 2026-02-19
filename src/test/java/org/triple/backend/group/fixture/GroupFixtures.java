package org.triple.backend.group.fixture;

import org.triple.backend.group.entity.group.Group;
import org.triple.backend.group.entity.group.GroupKind;

public class GroupFixtures {

    private GroupFixtures(){}

    public static Group publicGroup(final String name) {
        return Group.builder()
                .groupKind(GroupKind.PUBLIC)
                .name(name)
                .description("desc")
                .thumbNailUrl("https://example.com/thumb.png")
                .memberLimit(10)
                .build();
    }

    public static Group privateGroup(final String name) {
        return Group.builder()
                .groupKind(GroupKind.PRIVATE)
                .name(name)
                .description("desc")
                .thumbNailUrl("https://example.com/thumb.png")
                .memberLimit(10)
                .build();
    }
}