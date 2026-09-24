package com.connectly.community;

import java.util.Collection;
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
    List<CommunityMember> findByCommunityId(Long communityId);

    @Query("select m from CommunityMember m where m.user.id = :userId")
    List<CommunityMember> findAllMemberships(@Param("userId") Long userId);

    /** Batch member counts for a list of communities (one query instead of N). */
    @Query("select m.community.id, count(m) from CommunityMember m where m.community.id in :communityIds group by m.community.id")
    List<Object[]> countByCommunityIdIn(@Param("communityIds") Collection<Long> communityIds);
}

interface ChannelRepository extends JpaRepository<Channel, Long> {
    List<Channel> findByCommunityIdOrderByPositionAscIdAsc(Long communityId);
    Optional<Channel> findByIdAndCommunityId(Long id, Long communityId);
}

interface ChannelMessageRepository extends JpaRepository<ChannelMessage, Long> {
    @Query("select m from ChannelMessage m join fetch m.sender where m.channel.id = :channelId order by m.id desc")
    List<ChannelMessage> findByChannelIdOrderByIdDesc(@Param("channelId") Long channelId, Pageable pageable);
}
