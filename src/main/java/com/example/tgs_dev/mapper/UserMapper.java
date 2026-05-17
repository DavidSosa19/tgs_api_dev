package com.example.tgs_dev.mapper;

import com.example.tgs_dev.controller.response.UserDTO;
import com.example.tgs_dev.entity.AppRoleEntity;
import com.example.tgs_dev.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class UserMapper {

    private final PersonMapper personMapper;

    public UserDTO toDTO(User user) {
        if (user == null) return null;

        List<String> roleNames = user.getRoles() == null
                ? List.of()
                : user.getRoles().stream()
                        .map(AppRoleEntity::getName)
                        .sorted()
                        .toList();

        return new UserDTO(
                user.getId(),
                user.getUsername(),
                roleNames,
                user.isEnabled(),
                personMapper.toDTO(user.getPerson())
        );
    }
}
