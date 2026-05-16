package com.example.tgs_dev.security;

import com.example.tgs_dev.entity.User;
import com.example.tgs_dev.entity.enums.Role;
import com.example.tgs_dev.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserDetailsServiceImpl")
class UserDetailsServiceImplTest {

    @Mock UserRepository userRepository;
    @InjectMocks UserDetailsServiceImpl sut;

    private User user(String username) {
        return User.builder()
                .userName(username)
                .password("encoded-password")
                .rol(Role.USER)
                .active(true)
                .build();
    }

    @Nested @DisplayName("loadUserByUsername")
    class LoadUserByUsername {

        @Test @DisplayName("returns UserDetails when user is found")
        void found_returnsUserDetails() {
            User u = user("jdoe");
            when(userRepository.findByUserName("jdoe")).thenReturn(Optional.of(u));

            UserDetails result = sut.loadUserByUsername("jdoe");

            assertThat(result.getUsername()).isEqualTo("jdoe");
            assertThat(result.getPassword()).isEqualTo("encoded-password");
        }

        @Test @DisplayName("throws UsernameNotFoundException when user is not found")
        void notFound_throwsUsernameNotFoundException() {
            when(userRepository.findByUserName("ghost")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> sut.loadUserByUsername("ghost"))
                    .isInstanceOf(UsernameNotFoundException.class)
                    .hasMessageContaining("ghost");
        }
    }
}
