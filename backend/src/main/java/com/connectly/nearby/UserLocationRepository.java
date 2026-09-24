package com.connectly.nearby;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserLocationRepository extends JpaRepository<UserLocation, Long> {

    Optional<UserLocation> findByUserId(Long userId);

    /** Only users who opted in to discovery — the privacy gate is in the query itself. */
    List<UserLocation> findByDiscoverableTrue();
}
