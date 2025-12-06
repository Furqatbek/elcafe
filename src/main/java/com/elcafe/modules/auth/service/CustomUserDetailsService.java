package com.elcafe.modules.auth.service;

import com.elcafe.modules.auth.entity.User;
import com.elcafe.modules.auth.repository.UserRepository;
import com.elcafe.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // Try finding by email first
        User user = userRepository.findByEmail(username)
                .orElseGet(() -> {
                    // If not found and looks like a phone number, try phone
                    if (username.startsWith("+")) {
                        return userRepository.findByPhone(username).orElse(null);
                    }
                    return null;
                });

        if (user == null) {
            throw new UsernameNotFoundException("User not found with email or phone: " + username);
        }

        return UserPrincipal.create(user);
    }
}
