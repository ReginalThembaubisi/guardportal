package com.propertysecurity.platform.patrol;

import com.propertysecurity.platform.checkpoint.Checkpoint;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Composition data (route <-> checkpoint, ordered) — not its own audited entity. */
@Entity
@Table(name = "patrol_route_checkpoint")
@Getter
@Setter
@NoArgsConstructor
public class PatrolRouteCheckpoint {

    @EmbeddedId
    private PatrolRouteCheckpointId id = new PatrolRouteCheckpointId();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("routeId")
    @JoinColumn(name = "route_id")
    private PatrolRoute route;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("checkpointId")
    @JoinColumn(name = "checkpoint_id")
    private Checkpoint checkpoint;

    @Column(name = "sequence_order", nullable = false)
    private Integer sequenceOrder;
}
