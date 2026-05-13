package com.example.tgs_dev.repository;

import com.example.tgs_dev.entity.User;
import com.example.tgs_dev.repository.base.BaseRepository;

import java.util.Optional;

public interface UserRepository  extends BaseRepository<User,Integer> {
    Optional<User> findByUserName(String userName);
}
