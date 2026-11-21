package com.departementemployee.users.repos;

import org.springframework.data.jpa.repository.JpaRepository;
import com.departementemployee.users.entities.VerificationToken;

public interface VerificationTokenRepository extends JpaRepository<VerificationToken, Long> {
    VerificationToken findByToken(String token);
}