package com.pim.ecommerce.service;

import com.pim.ecommerce.domain.entity.Address;
import com.pim.ecommerce.domain.entity.User;
import com.pim.ecommerce.domain.entity.enums.UserRole;
import com.pim.ecommerce.domain.repository.UserRepository;
import com.pim.ecommerce.dto.request.CreateUserRequest;
import com.pim.ecommerce.dto.request.UpdateAddressRequest;
import com.pim.ecommerce.dto.request.UpdateMyProfileRequest;
import com.pim.ecommerce.dto.response.UserResponse;
import com.pim.ecommerce.security.CurrentUserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.pim.ecommerce.dto.request.UpdateUserRequest;
import org.springframework.mock.web.MockMultipartFile;
import java.util.List;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @InjectMocks
    private UserService userService;

    @Test
    void shouldCreateCustomerWithNormalizedEmailAndEncodedPassword() {
        CreateUserRequest request = new CreateUserRequest(" Ana Souza ", " ANA@EMAIL.COM ", "123456");

        when(userRepository.existsByEmail("ana@email.com")).thenReturn(false);
        when(passwordEncoder.encode("123456")).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(1L);
            return user;
        });

        UserResponse response = userService.create(request);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.fullName()).isEqualTo("Ana Souza");
        assertThat(response.email()).isEqualTo("ana@email.com");
        assertThat(response.role()).isEqualTo(UserRole.CUSTOMER);
        assertThat(response.active()).isTrue();
        assertThat(response.profileCompleteForCheckout()).isFalse();
    }

    @Test
    void shouldRejectDuplicatedEmailOnCreate() {
        when(userRepository.existsByEmail("ana@email.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.create(new CreateUserRequest("Ana Souza", "ana@email.com", "123456")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Já existe um usuário com este e-mail");
    }

    @Test
    void shouldUpdateMyProfilePartiallyAndNormalizeCpf() {
        User user = user(1L, true);
        when(currentUserService.getCurrentUserId()).thenReturn(1L);
        when(userRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(user));
        when(userRepository.existsByCpfAndIdNot("12345678900", 1L)).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserResponse response = userService.updateMyProfile(new UpdateMyProfileRequest(
                " Ana Souza Silva ",
                "123.456.789-00",
                " (19) 99999-9999 "
        ));

        assertThat(response.fullName()).isEqualTo("Ana Souza Silva");
        assertThat(response.cpf()).isEqualTo("12345678900");
        assertThat(response.phone()).isEqualTo("(19) 99999-9999");
    }

    @Test
    void shouldRejectBlankNameWhenUpdatingMyProfile() {
        User user = user(1L, true);
        when(currentUserService.getCurrentUserId()).thenReturn(1L);
        when(userRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.updateMyProfile(new UpdateMyProfileRequest("   ", null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Nome completo não pode ser vazio");
    }

    @Test
    void shouldUpdateMyAddressAndMarkProfileCompleteForCheckout() {
        User user = user(1L, true);
        when(currentUserService.getCurrentUserId()).thenReturn(1L);
        when(userRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserResponse response = userService.updateMyAddress(new UpdateAddressRequest(
                "13480-370",
                " Rua das Flores ",
                " 123 ",
                " Casa ",
                " Limeira ",
                "sp"
        ));

        assertThat(response.address()).isNotNull();
        assertThat(response.address().cep()).isEqualTo("13480370");
        assertThat(response.address().state()).isEqualTo("SP");
        assertThat(response.profileCompleteForCheckout()).isTrue();
    }

    @Test
    void shouldSoftDeleteUserAndRevokeRefreshTokens() {
        User user = user(1L, true);
        when(userRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        userService.delete(1L);

        assertThat(user.getActive()).isFalse();
        verify(currentUserService).ensureAdminOrSelf(1L);
        verify(refreshTokenService).revokeAllUserTokens(1L);
    }

    @Test
    void shouldReactivateUserOnlyAsAdmin() {
        User user = user(1L, false);
        when(currentUserService.isAdmin()).thenReturn(true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserResponse response = userService.reactivate(1L);

        assertThat(response.active()).isTrue();
    }

    @Test
    void shouldRejectReactivateWhenNotAdmin() {
        when(currentUserService.isAdmin()).thenReturn(false);

        assertThatThrownBy(() -> userService.reactivate(1L))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Apenas ADMIN pode reativar usuários");
    }

    private User user(Long id, Boolean active) {
        return User.builder()
                .id(id)
                .fullName("Ana Souza")
                .email("ana@email.com")
                .passwordHash("hash")
                .role(UserRole.CUSTOMER)
                .active(active)
                .address(null)
                .build();
    }

    @Test
    void shouldFindAllActiveUsers() {
        when(userRepository.findAllByActiveTrue()).thenReturn(List.of(mutationUser(1L, true), mutationUser(2L, true)));

        var response = userService.findAll();

        assertThat(response).hasSize(2);
        assertThat(response).extracting("active").containsOnly(true);
    }

    @Test
    void shouldFindAllForAdminByRoleAndActiveFilter() {
        User activeCustomer = mutationUser(1L, true);
        User inactiveCustomer = mutationUser(2L, false);
        when(userRepository.findByRoleOrderByCreatedAtDesc(UserRole.CUSTOMER))
                .thenReturn(List.of(activeCustomer, inactiveCustomer));

        var response = userService.findAllForAdmin(true, UserRole.CUSTOMER);

        assertThat(response).hasSize(1);
        assertThat(response.getFirst().id()).isEqualTo(1L);
        assertThat(response.getFirst().active()).isTrue();
    }

    @Test
    void shouldFindAllForAdminWithoutRoleAndInactiveFilter() {
        User activeAdmin = mutationUser(1L, true);
        activeAdmin.setRole(UserRole.ADMIN);
        User inactiveCustomer = mutationUser(2L, false);
        when(userRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(activeAdmin, inactiveCustomer));

        var response = userService.findAllForAdmin(false, null);

        assertThat(response).hasSize(1);
        assertThat(response.getFirst().id()).isEqualTo(2L);
        assertThat(response.getFirst().active()).isFalse();
    }

    @Test
    void shouldFindUserByIdAndCheckCurrentUserPermission() {
        User user = mutationUser(1L, true);
        when(userRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(user));

        var response = userService.findById(1L);

        assertThat(response.id()).isEqualTo(1L);
        verify(currentUserService).ensureAdminOrSelf(1L);
    }

    @Test
    void shouldFindUserByIdForAdminEvenWhenInactive() {
        User user = mutationUser(1L, false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        var response = userService.findByIdForAdmin(1L);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.active()).isFalse();
    }

    @Test
    void shouldFindCurrentUser() {
        User user = mutationUser(1L, true);
        when(currentUserService.getCurrentUserId()).thenReturn(1L);
        when(userRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(user));

        var response = userService.findMe();

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.email()).isEqualTo("ana@email.com");
    }

    @Test
    void shouldUpdateUserCompletelyAndNormalizeAddress() {
        User user = mutationUser(1L, true);
        UpdateUserRequest request = new UpdateUserRequest(
                " Ana Souza Silva ",
                " ANA.SILVA@EMAIL.COM ",
                "123.456.789-00",
                " (19) 98888-7777 ",
                new UpdateAddressRequest(
                        "13480-370",
                        " Rua das Flores ",
                        " 123 ",
                        " Casa ",
                        " Limeira ",
                        "sp"
                )
        );

        when(userRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(user));
        when(userRepository.existsByEmailAndIdNot("ana.silva@email.com", 1L)).thenReturn(false);
        when(userRepository.existsByCpfAndIdNot("12345678900", 1L)).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = userService.update(1L, request);

        assertThat(response.fullName()).isEqualTo("Ana Souza Silva");
        assertThat(response.email()).isEqualTo("ana.silva@email.com");
        assertThat(response.cpf()).isEqualTo("12345678900");
        assertThat(response.phone()).isEqualTo("(19) 98888-7777");
        assertThat(response.address().cep()).isEqualTo("13480370");
        assertThat(response.address().state()).isEqualTo("SP");
        verify(currentUserService).ensureAdminOrSelf(1L);
    }

    @Test
    void shouldRejectUpdateWhenEmailBelongsToAnotherUser() {
        User user = mutationUser(1L, true);
        when(userRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(user));
        when(userRepository.existsByEmailAndIdNot("ana.silva@email.com", 1L)).thenReturn(true);

        assertThatThrownBy(() -> userService.update(1L, mutationUpdateRequest("ana.silva@email.com", "12345678900")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Já existe um usuário com este e-mail");

        verify(userRepository, never()).save(any());
    }

    @Test
    void shouldRejectUpdateWhenCpfBelongsToAnotherUser() {
        User user = mutationUser(1L, true);
        when(userRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(user));
        when(userRepository.existsByEmailAndIdNot("ana.silva@email.com", 1L)).thenReturn(false);
        when(userRepository.existsByCpfAndIdNot("12345678900", 1L)).thenReturn(true);

        assertThatThrownBy(() -> userService.update(1L, mutationUpdateRequest("ana.silva@email.com", "12345678900")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Já existe um usuário com este CPF");

        verify(userRepository, never()).save(any());
    }

    @Test
    void shouldRejectInvalidCpfOnProfileUpdate() {
        User user = mutationUser(1L, true);
        when(currentUserService.getCurrentUserId()).thenReturn(1L);
        when(userRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.updateMyProfile(new UpdateMyProfileRequest(null, "123", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("CPF inválido");
    }

    @Test
    void shouldRejectDuplicatedCpfOnProfileUpdate() {
        User user = mutationUser(1L, true);
        when(currentUserService.getCurrentUserId()).thenReturn(1L);
        when(userRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(user));
        when(userRepository.existsByCpfAndIdNot("12345678900", 1L)).thenReturn(true);

        assertThatThrownBy(() -> userService.updateMyProfile(new UpdateMyProfileRequest(null, "123.456.789-00", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Já existe um usuário com este CPF");
    }

    @Test
    void shouldClearOptionalProfileFieldsWhenBlankValuesAreProvided() {
        User user = mutationUser(1L, true);
        user.setCpf("12345678900");
        user.setPhone("(19) 99999-9999");
        when(currentUserService.getCurrentUserId()).thenReturn(1L);
        when(userRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = userService.updateMyProfile(new UpdateMyProfileRequest(null, "   ", "   "));

        assertThat(response.cpf()).isNull();
        assertThat(response.phone()).isNull();
    }

    @Test
    void shouldRejectInvalidCepWhenUpdatingAddress() {
        User user = mutationUser(1L, true);
        when(currentUserService.getCurrentUserId()).thenReturn(1L);
        when(userRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.updateMyAddress(new UpdateAddressRequest(
                "123",
                "Rua das Flores",
                "123",
                null,
                "Limeira",
                "SP"
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("CEP deve conter 8 dígitos");
    }

    @Test
    void shouldRejectEmptyAvatarFile() {
        User user = mutationUser(1L, true);
        when(currentUserService.getCurrentUserId()).thenReturn(1L);
        when(userRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(user));
        MockMultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png", new byte[]{});

        assertThatThrownBy(() -> userService.updateMyAvatar(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Selecione uma imagem para enviar");
    }

    @Test
    void shouldRejectInvalidAvatarContentType() {
        User user = mutationUser(1L, true);
        when(currentUserService.getCurrentUserId()).thenReturn(1L);
        when(userRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(user));
        MockMultipartFile file = new MockMultipartFile("file", "avatar.txt", "text/plain", new byte[]{1, 2, 3});

        assertThatThrownBy(() -> userService.updateMyAvatar(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Formato inválido. Envie JPG, PNG, WEBP ou GIF");
    }

    private UpdateUserRequest mutationUpdateRequest(String email, String cpf) {
        return new UpdateUserRequest(
                "Ana Souza Silva",
                email,
                cpf,
                null,
                new UpdateAddressRequest("13480370", "Rua", "123", null, "Limeira", "SP")
        );
    }

    private User mutationUser(Long id, Boolean active) {
        return User.builder()
                .id(id)
                .fullName("Ana Souza")
                .email("ana@email.com")
                .passwordHash("hash")
                .role(UserRole.CUSTOMER)
                .active(active)
                .address(null)
                .build();
    }
}
