package com.leadrat.crm.leads.api.user;

import com.leadrat.crm.leads.api.user.dto.MeResponse;
import com.leadrat.crm.leads.api.user.dto.UserDto;
import com.leadrat.crm.leads.api.user.dto.UserNamesDto;
import com.leadrat.crm.leads.api.user.dto.UserRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface UserService {

    Page<UserDto> getAll(Pageable pageable, UserListFilters filters);

    Page<UserDto> simpleSearch(String query, List<UserSearchField> searchFields, Pageable pageable, UserListFilters filters);

    List<UserNamesDto> getNames();

    UserDto get(UUID id);

    UserDto create(UserRequest request);

    UserDto edit(UUID id, UserDto request);

    UserDto setStatus(UUID id, boolean enabled);

    void resetPassword(UUID id, String newPassword);

    void delete(UUID id);

    MeResponse getMe();
}
