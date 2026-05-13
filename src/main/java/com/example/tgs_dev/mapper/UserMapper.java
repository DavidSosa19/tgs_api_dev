package com.example.tgs_dev.mapper;

import com.example.tgs_dev.controller.response.UserDTO;
import com.example.tgs_dev.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserMapper {

    private final PersonMapper personMapper;

    public UserDTO toDTO(User user) {
        if (user == null) return null;
        return new UserDTO(
                user.getId(),
                user.getUsername(),
                user.getRol().name(),
                user.isEnabled(),
                personMapper.toDTO(user.getPerson())
        );
    }
}
