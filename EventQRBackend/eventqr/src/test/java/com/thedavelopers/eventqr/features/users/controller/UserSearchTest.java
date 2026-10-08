package com.thedavelopers.eventqr.features.users.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.thedavelopers.eventqr.features.uploads.service.FileStorageService;
import com.thedavelopers.eventqr.features.users.model.dto.UserResponse;
import com.thedavelopers.eventqr.features.users.service.UserService;
import com.thedavelopers.eventqr.shared.constants.AccountRole;
import com.thedavelopers.eventqr.shared.exceptions.GlobalExceptionHandler;
import com.thedavelopers.eventqr.shared.security.JwtService;

/** GET /users?q= keeps the visibility rules and sort, and only adds a contains filter. */
class UserSearchTest {

    private static final String AUTH = "Bearer t";
    private static final List<AccountRole> ADMIN_ROLES = List.of(AccountRole.ADMIN, AccountRole.SUPER_ADMIN);

    private UserService userService;
    private JwtService jwt;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        userService = mock(UserService.class);
        jwt = mock(JwtService.class);
        mvc = MockMvcBuilders.standaloneSetup(new UserController(userService, jwt, mock(FileStorageService.class)))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        when(jwt.extractUserIdFromBearer(AUTH)).thenReturn(UUID.randomUUID());
        Page<UserResponse> empty = Page.empty(PageRequest.of(0, 20));
        when(userService.searchAllUsers(any(), any(Pageable.class))).thenReturn(empty);
        when(userService.searchByRole(any(), any(), any(Pageable.class))).thenReturn(empty);
        when(userService.searchByRoleNotIn(any(), any(), any(Pageable.class))).thenReturn(empty);
    }

    @Test
    void aPlainAdminSearchStaysScopedToNonAdminRolesAndEscapesWildcards() throws Exception {
        when(jwt.extractRoleFromBearer(AUTH)).thenReturn(AccountRole.ADMIN);

        mvc.perform(get("/api/v1/users").param("q", " Ja_ne ").header("Authorization", AUTH)).andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(userService).searchByRoleNotIn(eq(ADMIN_ROLES), eq("%ja!_ne%"), pageable.capture());
        org.assertj.core.api.Assertions.assertThat(pageable.getValue().getSort())
                .isEqualTo(Sort.by(Sort.Direction.DESC, "createdAt"));
        verify(userService, never()).searchAllUsers(any(), any());
        verify(userService, never()).findByRoleNotIn(any(), any());
    }

    @Test
    void aPlainAdminStillGetsNothingWhenSearchingAdminRoles() throws Exception {
        when(jwt.extractRoleFromBearer(AUTH)).thenReturn(AccountRole.ADMIN);

        mvc.perform(get("/api/v1/users").param("q", "x").param("role", "ADMIN").header("Authorization", AUTH))
                .andExpect(status().isOk());

        verify(userService, never()).searchByRole(any(), any(), any());
        verify(userService, never()).searchAllUsers(any(), any());
    }

    @Test
    void aSuperAdminSearchesEveryAccountOrOneRole() throws Exception {
        when(jwt.extractRoleFromBearer(AUTH)).thenReturn(AccountRole.SUPER_ADMIN);

        mvc.perform(get("/api/v1/users").param("q", "bob").header("Authorization", AUTH)).andExpect(status().isOk());
        verify(userService).searchAllUsers(eq("%bob%"), any(Pageable.class));

        mvc.perform(get("/api/v1/users").param("q", "bob").param("role", "STAFF").header("Authorization", AUTH))
                .andExpect(status().isOk());
        verify(userService).searchByRole(eq(AccountRole.STAFF), eq("%bob%"), any(Pageable.class));
    }

    @Test
    void aBlankQueryKeepsTheDefaultListing() throws Exception {
        when(jwt.extractRoleFromBearer(AUTH)).thenReturn(AccountRole.SUPER_ADMIN);
        when(userService.findAllUsers(any(Pageable.class))).thenReturn(Page.empty(PageRequest.of(0, 20)));

        mvc.perform(get("/api/v1/users").param("q", "   ").header("Authorization", AUTH)).andExpect(status().isOk());

        verify(userService).findAllUsers(any(Pageable.class));
        verify(userService, never()).searchAllUsers(any(), any());
    }

    @Test
    void pagingIsClampedForListing() throws Exception {
        when(jwt.extractRoleFromBearer(AUTH)).thenReturn(AccountRole.SUPER_ADMIN);
        when(userService.findAllUsers(any(Pageable.class))).thenReturn(Page.empty(PageRequest.of(0, 20)));
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);

        mvc.perform(get("/api/v1/users").param("size", "0").param("page", "-1").header("Authorization", AUTH)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/users").param("size", "9999").header("Authorization", AUTH)).andExpect(status().isOk());

        verify(userService, org.mockito.Mockito.times(2)).findAllUsers(pageable.capture());
        org.assertj.core.api.Assertions.assertThat(pageable.getAllValues().get(0).getPageSize()).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(pageable.getAllValues().get(0).getPageNumber()).isZero();
        org.assertj.core.api.Assertions.assertThat(pageable.getAllValues().get(1).getPageSize()).isEqualTo(100);
    }
}
