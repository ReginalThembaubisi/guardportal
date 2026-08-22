package com.propertysecurity.platform.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByEmailAndDeletedAtIsNull(String email);

    Optional<AppUser> findByPhoneNumberAndDeletedAtIsNull(String phoneNumber);

    boolean existsByEmailAndDeletedAtIsNull(String email);

    boolean existsByPhoneNumberAndDeletedAtIsNull(String phoneNumber);

    List<AppUser> findAllByRolesContainingAndDeletedAtIsNull(Role role);

    List<AppUser> findAllByDeletedAtIsNull();
}
