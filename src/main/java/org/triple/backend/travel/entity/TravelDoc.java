package org.triple.backend.travel.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "travel_doc")
public class TravelDoc {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "travel_doc_id")
    private Long id;

    @Column(name = "travel_itinerary_id", nullable = false, unique = true)
    private Long travelItineraryId;

    @Lob
    @Column(name = "state", nullable = false)
    private byte[] state;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static TravelDoc of(final Long travelItineraryId, final byte[] state) {
        TravelDoc travelDoc = new TravelDoc();
        travelDoc.travelItineraryId = travelItineraryId;
        travelDoc.state = state;
        return travelDoc;
    }
}
