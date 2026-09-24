package com.connectly.community;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface CommunityMemberRepository extends JpaRepository<CommunityMember, Long> {
    Optional<CommunityMember> findByCommunityIdAndUserId(Long communityId, Long userId);
    boolean existsByCommunityIdAndUserId(Long communityId, Long userId);
    long countByCommunityId(Long communityId);

    @Query("select m from CommunityMember m where m.user.id = :userId")
    List<CommunityMember> findAllMemberships(@Param("userId") Long userId);
}

interface ChannelRepository extends JpaRepository<Channel, Long> {
    List<Channel> findByCommunityIdOrderByPositionAscIdAsc(Long communityId);
    Optional<Channel> findByIdAndCommunityId(Long id, Long communityId);
}

interface ChannelMessageRepository extends JpaRepository<ChannelMessage, Long> {
    List<ChannelMessage> findByChannelIdOrderByIdDesc(Long channelId, Pageable pageable);
}
