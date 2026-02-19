package org.triple.backend.group.fixture;

import org.triple.backend.group.entity.group.Group;
import org.triple.backend.group.entity.group.GroupKind;

public class GroupFixtures {

    private GroupFixtures(){}

    public static Group publicGroup(final String name) {
        return Group.create(
                GroupKind.PUBLIC,
                name,
                "desc",
                "https://example.com/thumb.png",
                10
        );
    }

    public static Group privateGroup(final String name) {
        return Group.create(
                GroupKind.PRIVATE,
                name,
                "desc",
                "https://example.com/thumb.png",
                10
        );
    }
}