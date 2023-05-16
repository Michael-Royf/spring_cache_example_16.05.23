package com.michael.cash.service;

import com.michael.cash.payload.request.UserRequest;
import com.michael.cash.payload.response.MessageResponse;
import com.michael.cash.payload.response.UserResponse;

import java.util.List;

public interface UserService {

    UserResponse createUser(UserRequest userRequest);

    List<UserResponse> getAllUsers();

    UserResponse getUserById(Long id);

    UserResponse getUserByUsername(String username);

    UserResponse updateUser(Long id, UserRequest userRequest);

    MessageResponse deleteUser(Long id);
}
