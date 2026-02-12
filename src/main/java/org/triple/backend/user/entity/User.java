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

@Getter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "users")
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
}
