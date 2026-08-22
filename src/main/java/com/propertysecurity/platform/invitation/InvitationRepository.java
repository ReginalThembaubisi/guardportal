package com.propertysecurity.platform.invitation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InvitationRepository extends JpaRepository<Invitation, Long> {

    Optional<Invitation> findByQrToken(String qrToken);

    List<Invitation> findAllByResident_IdOrderByCreatedAtDesc(Long residentId);

    Optional<Invitation> findByIdAndResident_Id(Long id, Long residentId);
}
