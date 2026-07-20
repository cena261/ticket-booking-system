package com.ticketapp.persistence;

import com.ticketapp.domain.user.User;
import com.ticketapp.domain.user.UserRepository;
import com.ticketapp.domain.user.UserRole;
import com.ticketapp.support.AbstractIntegrationTest;
import com.ticketapp.support.Fixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class UserRepositoryIT extends AbstractIntegrationTest {

    @Autowired
    UserRepository userRepository;

    @Test
    void savesAndFindsByEmail() {
        User saved = userRepository.save(Fixtures.newUser(UserRole.USER));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(userRepository.findByEmail(saved.getEmail()))
                .get()
                .extracting(User::getId)
                .isEqualTo(saved.getId());
    }

    @Test
    void rejectsDuplicateEmail() {
        User first = userRepository.save(Fixtures.newUser(UserRole.USER));

        User duplicate = Fixtures.newUser(UserRole.USER);
        duplicate.setEmail(first.getEmail());

        assertThatThrownBy(() -> userRepository.save(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
