-- Phase 7: voice rooms (WebRTC mesh, signaling over WebSocket).
CREATE TABLE voice_rooms (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(60) NOT NULL,
    host_id     BIGINT NOT NULL,
    community_id BIGINT NULL,   -- optionally attached to a community
    status      VARCHAR(20) NOT NULL DEFAULT 'OPEN', -- OPEN | CLOSED
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    closed_at   TIMESTAMP NULL,
    CONSTRAINT fk_vr_host FOREIGN KEY (host_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_vr_comm FOREIGN KEY (community_id) REFERENCES communities (id) ON DELETE CASCADE,
    INDEX idx_vr_status (status)
) ENGINE = InnoDB;

-- Presence rows; also used to sync the peer list to everyone in the room.
CREATE TABLE voice_participants (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    room_id     BIGINT NOT NULL,
    user_id     BIGINT NOT NULL,
    joined_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    muted       BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_voice_participant UNIQUE (room_id, user_id),
    CONSTRAINT fk_vp_room FOREIGN KEY (room_id) REFERENCES voice_rooms (id) ON DELETE CASCADE,
    CONSTRAINT fk_vp_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    INDEX idx_vp_room (room_id)
) ENGINE = InnoDB;
