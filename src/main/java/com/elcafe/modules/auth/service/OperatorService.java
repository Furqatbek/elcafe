package com.elcafe.modules.auth.service;

import com.elcafe.modules.auth.dto.CreateOperatorRequest;
import com.elcafe.modules.auth.dto.OperatorDTO;
import com.elcafe.modules.auth.dto.UpdateOperatorRequest;
import com.elcafe.modules.auth.entity.User;
import com.elcafe.modules.auth.enums.UserRole;
import com.elcafe.modules.auth.repository.UserRepository;
import com.elcafe.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OperatorService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Get all operators with pagination
     */
    @Transactional(readOnly = true)
    public Page<OperatorDTO> getAllOperators(Pageable pageable) {
        return userRepository.findByRole(UserRole.OPERATOR, pageable)
                .map(this::convertToDTO);
    }

    /**
     * Get operator by ID
     */
    @Transactional(readOnly = true)
    public OperatorDTO getOperatorById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Operator not found with id: " + id));

        if (user.getRole() != UserRole.OPERATOR) {
            throw new ResourceNotFoundException("User with id " + id + " is not an operator");
        }

        return convertToDTO(user);
    }

    /**
     * Create new operator
     */
    @Transactional
    public OperatorDTO createOperator(CreateOperatorRequest request) {
        // Check if email already exists
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already exists: " + request.getEmail());
        }

        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .phone(request.getPhone())
                .role(UserRole.OPERATOR)
                .active(request.getActive() != null ? request.getActive() : true)
                .emailVerified(false)
                .build();

        User savedUser = userRepository.save(user);
        return convertToDTO(savedUser);
    }

    /**
     * Update operator
     */
    @Transactional
    public OperatorDTO updateOperator(Long id, UpdateOperatorRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Operator not found with id: " + id));

        if (user.getRole() != UserRole.OPERATOR) {
            throw new IllegalArgumentException("User with id " + id + " is not an operator");
        }

        // Check if email is being updated and if it already exists
        if (request.getEmail() != null && !request.getEmail().equals(user.getEmail())) {
            if (userRepository.existsByEmail(request.getEmail())) {
                throw new IllegalArgumentException("Email already exists: " + request.getEmail());
            }
            user.setEmail(request.getEmail());
        }

        if (request.getPassword() != null && !request.getPassword().isEmpty()) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }

        if (request.getFirstName() != null) {
            user.setFirstName(request.getFirstName());
        }

        if (request.getLastName() != null) {
            user.setLastName(request.getLastName());
        }

        if (request.getPhone() != null) {
            user.setPhone(request.getPhone());
        }

        if (request.getActive() != null) {
            user.setActive(request.getActive());
        }

        User updatedUser = userRepository.save(user);
        return convertToDTO(updatedUser);
    }

    /**
     * Delete operator
     */
    @Transactional
    public void deleteOperator(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Operator not found with id: " + id));

        if (user.getRole() != UserRole.OPERATOR) {
            throw new IllegalArgumentException("User with id " + id + " is not an operator");
        }

        userRepository.delete(user);
    }

    /**
     * Convert User entity to OperatorDTO
     */
    private OperatorDTO convertToDTO(User user) {
        return OperatorDTO.builder()
                .id(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .phone(user.getPhone())
                .role(user.getRole())
                .active(user.getActive())
                .emailVerified(user.getEmailVerified())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
