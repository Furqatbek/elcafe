package uz.megahotdog.modules.auth.service;

import uz.megahotdog.modules.auth.entity.User;
import uz.megahotdog.modules.auth.repository.UserRepository;
import uz.megahotdog.modules.customer.repository.CustomerRepository;
import uz.megahotdog.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final CustomerRepository customerRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // Try finding in User repository by email first
        User user = userRepository.findByEmail(username)
                .orElseGet(() -> {
                    // If not found and looks like a phone number, try phone
                    if (username.startsWith("+")) {
                        return userRepository.findByPhone(username).orElse(null);
                    }
                    return null;
                });

        if (user == null) {
            // Check if this is a customer account
            boolean isCustomer = customerRepository.findByEmail(username).isPresent() ||
                    (username.startsWith("+") && customerRepository.findByPhone(username).isPresent());

            if (isCustomer) {
                throw new UsernameNotFoundException(
                    "This is a customer account. Please use OTP authentication endpoint: POST /api/auth/consumer/login"
                );
            }

            throw new UsernameNotFoundException("User not found with email or phone: " + username);
        }

        return UserPrincipal.create(user);
    }
}
