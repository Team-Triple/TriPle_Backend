package org.triple.backend.user.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.triple.backend.auth.oauth.OauthProvider;
import org.triple.backend.global.common.BaseEntity;
import org.triple.backend.group.entity.joinApply.JoinApply;
import org.triple.backend.group.entity.userGroup.UserGroup;
import org.triple.backend.invoice.entity.InvoiceUser;
import org.triple.backend.travel.entity.UserTravelItinerary;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.triple.backend.user.dto.request.UpdateUserInfoReq;

@Getter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "users",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_users_public_uuid",
                        columnNames = {"public_uuid"}
                )
        }
)
public class User extends BaseEntity {

    @Id
    @Column(name = "user_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private OauthProvider provider;

    private String providerId;

    private String nickname;

    private String email;

    @Enumerated(EnumType.STRING)
    private Gender gender;

    private LocalDate birth;

    private String description;

    private String profileUrl;

    @Column(name = "public_uuid")
    private UUID publicUuid;

    @OneToMany(mappedBy = "user")
    @Builder.Default
    private List<JoinApply> joinApplies = new ArrayList<>();

    @OneToMany(mappedBy = "user")
    @Builder.Default
    private List<UserGroup> userGroups = new ArrayList<>();

    @OneToMany(mappedBy = "user")
    @Builder.Default
    private List<UserTravelItinerary> userTravelItineraries = new ArrayList<>();

    @OneToMany(mappedBy = "user")
    @Builder.Default
    private List<InvoiceUser> invoiceUsers = new ArrayList<>();

    @PrePersist
    void initPublicUuid() {
        if(publicUuid == null) publicUuid = UUID.randomUUID();
    }

    public void assignPublicUuidIfAbsent() {
        if (publicUuid == null) {
            publicUuid = UUID.randomUUID();
        }
    }

    public void patchUserInfo(UpdateUserInfoReq req) {
        if (req.nickname() != null) {
            this.nickname = validateNickname(req.nickname());
        }

        if (req.gender() != null) {
            this.gender = req.gender();
        }

        if (req.birth() != null) {
            this.birth = validateBirth(req.birth());
        }

        if (req.description() != null) {
            this.description = validateDescription(req.description());
        }

        if (req.profileUrl() != null) {
            this.profileUrl = validateProfileUrl(req.profileUrl());
        }
    }

    private String validateNickname(String nickname) {
        String trimmedNickname = nickname.trim();
        if (trimmedNickname.isEmpty()) {
            throw new IllegalArgumentException("닉네임은 공백일 수 없습니다.");
        }
        return trimmedNickname;
    }

    private LocalDate validateBirth(LocalDate birth) {
        if (birth.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("생년월일은 미래일 수 없습니다.");
        }
        return birth;
    }

    private String validateDescription(String description) {
        String trimmedDescription = description.trim();
        if (trimmedDescription.isEmpty()) {
            throw new IllegalArgumentException("소개글은 공백일 수 없습니다.");
        }
        return trimmedDescription;
    }

    private String validateProfileUrl(String profileUrl) {
        String trimmedProfileUrl = profileUrl.trim();
        if (trimmedProfileUrl.isEmpty()) {
            throw new IllegalArgumentException("프로필 URL은 공백일 수 없습니다.");
        }
        return trimmedProfileUrl;
    }
}
