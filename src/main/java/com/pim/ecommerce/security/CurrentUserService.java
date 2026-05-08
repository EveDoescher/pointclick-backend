package com.pim.ecommerce.security;

import com.pim.ecommerce.domain.entity.Order;
import com.pim.ecommerce.domain.entity.Payment;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserService {

    public AuthenticatedUser getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AuthenticationCredentialsNotFoundException("Usuário não autenticado");
        }

        Object principal = authentication.getPrincipal();

        if (!(principal instanceof AuthenticatedUser authenticatedUser)) {
            throw new AuthenticationCredentialsNotFoundException("Usuário autenticado inválido");
        }

        return authenticatedUser;
    }

    public Long getCurrentUserId() {
        return getCurrentUser().getId();
    }

    public boolean isAdmin() {
        return getCurrentUser()
                .getAuthorities()
                .stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
    }

    public boolean isCustomer() {
        return getCurrentUser()
                .getAuthorities()
                .stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_CUSTOMER"));
    }

    public void ensureAdminOrSelf(Long targetUserId) {
        if (isAdmin()) {
            return;
        }

        Long currentUserId = getCurrentUserId();

        if (!currentUserId.equals(targetUserId)) {
            throw new AccessDeniedException("Você não tem permissão para acessar este usuário");
        }
    }

    public void ensureAdminOrOrderOwner(Order order) {
        if (isAdmin()) {
            return;
        }

        if (order == null || order.getUser() == null) {
            throw new AccessDeniedException("Você não tem permissão para acessar este pedido");
        }

        Long currentUserId = getCurrentUserId();
        Long orderOwnerId = order.getUser().getId();

        if (!currentUserId.equals(orderOwnerId)) {
            throw new AccessDeniedException("Você não tem permissão para acessar este pedido");
        }
    }

    public void ensureAdminOrPaymentOwner(Payment payment) {
        if (isAdmin()) {
            return;
        }

        if (payment == null || payment.getOrder() == null || payment.getOrder().getUser() == null) {
            throw new AccessDeniedException("Você não tem permissão para acessar este pagamento");
        }

        Long currentUserId = getCurrentUserId();
        Long paymentOwnerId = payment.getOrder().getUser().getId();

        if (!currentUserId.equals(paymentOwnerId)) {
            throw new AccessDeniedException("Você não tem permissão para acessar este pagamento");
        }
    }
}