package com.ticketapp.infrastructure.persistence.user;

import com.ticketapp.domain.user.User;
import com.ticketapp.domain.user.UserRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserJpaRepository extends UserRepository, JpaRepository<User, Long> {
}
