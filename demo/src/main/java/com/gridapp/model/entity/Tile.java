package com.gridapp.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.domain.Persistable;

import java.time.Instant;

@Entity
@Table(name = "tiles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Tile implements Persistable<String> {

    /**
     * Primary key: "{row}_{col}", e.g. "12_5"
     */
    @Id
    @Column(name = "tile_id", length = 10)
    private String tileId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private User owner;

    @Column(name = "claimed_at")
    private Instant claimedAt;

    @Transient
    @Builder.Default
    private boolean isNew = true;

    @Override
    public String getId() {
        return this.tileId;
    }

    @Override
    public boolean isNew() {
        return this.isNew;
    }

    @PostPersist
    @PostLoad
    void markNotNew() {
        this.isNew = false;
    }
}
