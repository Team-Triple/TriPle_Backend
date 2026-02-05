package org.triple.backend.user.entity;

import jakarta.persistence.*;
import org.triple.backend.global.common.BaseEntity;
import org.triple.backend.group.entity.joinApply.JoinApply;
import org.triple.backend.group.entity.userGroup.UserGroup;
import org.triple.backend.invoice.entity.InvoiceUser;
import org.triple.backend.travel.entity.UserTravelItinerary;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
public class User extends BaseEntity {

    @Id
    @Column(name = "user_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String providerId;

    private String nickname;

    @Enumerated(EnumType.STRING)
    private Gender gender;

    private LocalDateTime birth;

    private String description;

    private String profileUrl;

    @OneToMany(mappedBy = "user")
    private List<JoinApply> joinApplies = new ArrayList<>();

    @OneToMany(mappedBy = "user")
    private List<UserGroup> userGroups = new ArrayList<>();

    @OneToMany(mappedBy = "user")
    private List<UserTravelItinerary> userTravelItineraries = new ArrayList<>();

    @OneToMany(mappedBy = "user")
    private List<InvoiceUser> invoiceUsers = new ArrayList<>();
}
