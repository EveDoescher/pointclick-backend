package com.pim.ecommerce.service;

import com.pim.ecommerce.domain.entity.Address;
import com.pim.ecommerce.domain.entity.User;
import com.pim.ecommerce.domain.entity.enums.UserRole;
import com.pim.ecommerce.domain.repository.UserRepository;
import com.pim.ecommerce.dto.request.CreateUserRequest;
import com.pim.ecommerce.dto.request.UpdateAddressRequest;
import com.pim.ecommerce.dto.request.UpdateMyProfileRequest;
import com.pim.ecommerce.dto.request.UpdateUserRequest;
import com.pim.ecommerce.dto.response.AddressResponse;
import com.pim.ecommerce.dto.response.UserResponse;
import com.pim.ecommerce.integration.viacep.ViaCepClient;
import com.pim.ecommerce.integration.viacep.ViaCepResponse;
import com.pim.ecommerce.security.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUserService currentUserService;
    private final RefreshTokenService refreshTokenService;
    private final FileStorageService fileStorageService;
    private final ViaCepClient viaCepClient;


    @Transactional
    public UserResponse create(CreateUserRequest request) {
        String normalizedEmail = normalizeEmail(request.email());

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new IllegalArgumentException("Já existe um usuário com este e-mail");
        }

        User user = User.builder()
                .fullName(request.fullName().trim())
                .email(normalizedEmail)
                .passwordHash(passwordEncoder.encode(request.password()))
                .cpf(null)
                .phone(null)
                .address(null)
                .active(true)
                .role(UserRole.CUSTOMER)
                .build();

        User savedUser = userRepository.save(user);

        return toResponse(savedUser);
    }

    @Transactional(readOnly = true)
    public List<UserResponse> findAll() {
        return userRepository.findAllByActiveTrue()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<UserResponse> findAllForAdmin(Boolean active, UserRole role) {
        List<User> users;

        if (role != null) {
            users = userRepository.findByRoleOrderByCreatedAtDesc(role);
        } else {
            users = userRepository.findAllByOrderByCreatedAtDesc();
        }

        return users.stream()
                .filter(user -> active == null || active.equals(Boolean.TRUE.equals(user.getActive())))
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public UserResponse findById(Long id) {
        currentUserService.ensureAdminOrSelf(id);

        User user = findActiveUserById(id);

        return toResponse(user);
    }

    @Transactional(readOnly = true)
    public UserResponse findByIdForAdmin(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado"));

        return toResponse(user);
    }

    @Transactional(readOnly = true)
    public UserResponse findMe() {
        Long currentUserId = currentUserService.getCurrentUserId();

        User user = findActiveUserById(currentUserId);

        return toResponse(user);
    }

    @Transactional
    public UserResponse update(Long id, UpdateUserRequest request) {
        currentUserService.ensureAdminOrSelf(id);

        User user = findActiveUserById(id);

        String normalizedEmail = normalizeEmail(request.email());
        String normalizedCpf = normalizeCpfRequired(request.cpf());

        if (userRepository.existsByEmailAndIdNot(normalizedEmail, id)) {
            throw new IllegalArgumentException("Já existe um usuário com este e-mail");
        }

        if (userRepository.existsByCpfAndIdNot(normalizedCpf, id)) {
            throw new IllegalArgumentException("Já existe um usuário com este CPF");
        }

        user.setFullName(request.fullName().trim());
        user.setEmail(normalizedEmail);
        user.setCpf(normalizedCpf);
        user.setPhone(normalizeOptionalText(request.phone()));

        Address address = getOrCreateAddress(user);
        applyAddressUpdate(address, request.address());

        User updatedUser = userRepository.save(user);

        return toResponse(updatedUser);
    }

    @Transactional
    public UserResponse updateMyProfile(UpdateMyProfileRequest request) {
        Long currentUserId = currentUserService.getCurrentUserId();

        User user = findActiveUserById(currentUserId);

        if (request.fullName() != null) {
            String fullName = request.fullName().trim();

            if (fullName.isBlank()) {
                throw new IllegalArgumentException("Nome completo não pode ser vazio");
            }

            user.setFullName(fullName);
        }

        if (request.cpf() != null) {
            String normalizedCpf = normalizeCpfOptional(request.cpf());

            if (normalizedCpf != null && userRepository.existsByCpfAndIdNot(normalizedCpf, user.getId())) {
                throw new IllegalArgumentException("Já existe um usuário com este CPF");
            }

            user.setCpf(normalizedCpf);
        }

        if (request.phone() != null) {
            user.setPhone(normalizeOptionalText(request.phone()));
        }

        User updatedUser = userRepository.save(user);

        return toResponse(updatedUser);
    }

    @Transactional
    public UserResponse updateMyAvatar(MultipartFile file) {
        Long currentUserId = currentUserService.getCurrentUserId();

        User user = findActiveUserById(currentUserId);

        if (user.getAvatarUrl() != null && !user.getAvatarUrl().isBlank()) {
            fileStorageService.deletePublicFile(user.getAvatarUrl());
        }

        String avatarUrl = fileStorageService.storeUserAvatar(file);

        user.setAvatarUrl(avatarUrl);

        return toResponse(userRepository.save(user));
    }

    @Transactional
    public UserResponse updateMyAddress(UpdateAddressRequest request) {
        Long currentUserId = currentUserService.getCurrentUserId();

        User user = findActiveUserById(currentUserId);

        Address address = getOrCreateAddress(user);
        applyAddressUpdate(address, request);

        User updatedUser = userRepository.save(user);

        return toResponse(updatedUser);
    }

    @Transactional
    public void delete(Long id) {
        currentUserService.ensureAdminOrSelf(id);

        User user = findActiveUserById(id);

        user.setActive(false);

        userRepository.save(user);
        refreshTokenService.revokeAllUserTokens(id);
    }

    @Transactional
    public UserResponse reactivate(Long id) {
        if (!currentUserService.isAdmin()) {
            throw new org.springframework.security.access.AccessDeniedException("Apenas ADMIN pode reativar usuários");
        }

        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado"));

        user.setActive(true);

        return toResponse(userRepository.save(user));
    }

    private User findActiveUserById(Long id) {
        return userRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado"));
    }

    private Address getOrCreateAddress(User user) {
        Address address = user.getAddress();

        if (address == null) {
            address = new Address();
            user.setAddress(address);
        }

        return address;
    }

    private void applyAddressUpdate(Address address, UpdateAddressRequest request) {
        String normalizedCep = normalizeCep(request.cep());

        ViaCepResponse viaCepResponse = viaCepClient.getAddressByCep(normalizedCep);

        address.setCep(normalizedCep);
        address.setStreet(request.street().trim());
        address.setNumber(request.number().trim());
        address.setComplement(normalizeOptionalText(request.complement()));
        address.setCity(viaCepResponse.localidade().trim());
        address.setState(viaCepResponse.uf().trim().toUpperCase());
    }

    private UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getCpf(),
                user.getPhone(),
                user.getAvatarUrl(),
                user.getRole(),
                user.getActive(),
                toAddressResponse(user.getAddress()),
                isProfileCompleteForCheckout(user),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }

    private AddressResponse toAddressResponse(Address address) {
        if (address == null) {
            return null;
        }

        return new AddressResponse(
                address.getId(),
                address.getCep(),
                address.getStreet(),
                address.getNumber(),
                address.getComplement(),
                address.getCity(),
                address.getState(),
                address.getCreatedAt(),
                address.getUpdatedAt()
        );
    }

    private boolean isProfileCompleteForCheckout(User user) {
        Address address = user.getAddress();

        return address != null
                && isFilled(address.getCep())
                && isFilled(address.getStreet())
                && isFilled(address.getNumber())
                && isFilled(address.getCity())
                && isFilled(address.getState());
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("E-mail é obrigatório");
        }

        return email.trim().toLowerCase();
    }

    private String normalizeCpfRequired(String cpf) {
        String normalizedCpf = normalizeCpfOptional(cpf);

        if (normalizedCpf == null) {
            throw new IllegalArgumentException("CPF é obrigatório");
        }

        return normalizedCpf;
    }

    private String normalizeCpfOptional(String cpf) {
        if (cpf == null || cpf.isBlank()) {
            return null;
        }

        String normalizedCpf = cpf.replaceAll("\\D", "");

        if (normalizedCpf.length() != 11) {
            throw new IllegalArgumentException("CPF inválido");
        }

        return normalizedCpf;
    }

    private String normalizeCep(String cep) {
        if (cep == null || cep.isBlank()) {
            throw new IllegalArgumentException("CEP é obrigatório");
        }

        String normalizedCep = cep.replaceAll("\\D", "");

        if (normalizedCep.length() != 8) {
            throw new IllegalArgumentException("CEP deve conter 8 dígitos");
        }

        return normalizedCep;
    }

    private String normalizeOptionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }

    private boolean isFilled(String value) {
        return value != null && !value.isBlank();
    }
}