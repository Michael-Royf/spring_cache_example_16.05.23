package com.michael.cash.service.impl;

import com.michael.cash.entity.User;
import com.michael.cash.exception.payload.EmailExistException;
import com.michael.cash.exception.payload.UserNotFoundException;
import com.michael.cash.exception.payload.UsernameExistException;
import com.michael.cash.payload.request.UserRequest;
import com.michael.cash.payload.response.MessageResponse;
import com.michael.cash.payload.response.UserResponse;
import com.michael.cash.repository.UserRepository;
import com.michael.cash.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.modelmapper.ModelMapper;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
@CacheConfig(cacheNames = "users")
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final ModelMapper mapper;
    private final CacheManager cacheManager;

    @Override
    public UserResponse createUser(UserRequest userRequest) {
        validateNewUsernameAndEmail(StringUtils.EMPTY, userRequest.getUsername(), userRequest.getEmail());
        User user = User.builder()
                .firstName(userRequest.getFirstName())
                .lastName(userRequest.getLastName())
                .username(userRequest.getUsername())
                .email(userRequest.getEmail())
                .build();
        user = userRepository.save(user);

        return mapper.map(user, UserResponse.class);
    }

    @Override
    public List<UserResponse> getAllUsers() {
        return userRepository.findAll()
                .stream()
                .map(user -> mapper.map(user, UserResponse.class))
                .collect(Collectors.toList());
    }

    @Cacheable(key = "#id")
    @Override
    public UserResponse getUserById(Long id) {
        log.info("getting user by id: {}", id);
        User user = findUserByIdInDB(id);
        return mapper.map(user, UserResponse.class);
    }

    @Cacheable(key = "#username")
    @Override
    public UserResponse getUserByUsername(String username) {
        log.info("getting user by username: {}", username);
        User user = findOptionalUserByUsername(username)
                .orElseThrow(() -> new UserNotFoundException(String.format("User with username %s not found", username)));
        return mapper.map(user, UserResponse.class);
    }

    @CachePut(key = "#id")
    //   @Caching(put = { @CachePut(key = "#id"), @CachePut( key="#username") })
    @Override
    public UserResponse updateUser(Long id, UserRequest userRequest) {
        User user = findUserByIdInDB(id);
        validateNewUsernameAndEmail(user.getUsername(), userRequest.getUsername(), userRequest.getEmail());
        user.setFirstName(userRequest.getFirstName());
        user.setLastName(userRequest.getLastName());
        user.setUsername(userRequest.getUsername());
        user.setEmail(userRequest.getEmail());
        user = userRepository.save(user);

        // Обновление кэша для метода getUserByUsername
        String usernameCacheKey = user.getUsername();
        Cache userByUsernameCache = cacheManager.getCache("users");
        userByUsernameCache.put(usernameCacheKey, mapper.map(user, UserResponse.class));

        return mapper.map(user, UserResponse.class);
    }

    @CacheEvict(allEntries = true)
    @Override
    public MessageResponse deleteUser(Long id) {
        userRepository.delete(findUserByIdInDB(id));
        return new MessageResponse(String.format("User with id %s was deleted", id));
    }


    private User validateNewUsernameAndEmail(String currentUsername, String newUsername, String newEmail) throws UserNotFoundException, UsernameExistException, EmailExistException {
        User userByNewUsername = findOptionalUserByUsername(newUsername).orElse(null);
        User userByNewEmail = findUserByEmail(newEmail).orElse(null);
        if (StringUtils.isNotBlank(currentUsername)) {
            User currentUser = findOptionalUserByUsername(currentUsername).orElse(null);
            if (currentUser == null) {
                throw new UserNotFoundException("Not user found by username: " + currentUsername);
            }
            if (userByNewUsername != null && !currentUser.getId().equals(userByNewUsername.getId())) {
                throw new UsernameExistException("Username already exists");
            }
            if (userByNewEmail != null && !currentUser.getId().equals(userByNewEmail.getId())) {
                throw new EmailExistException("Email already exists");
            }
            return currentUser;
        } else {
            if (userByNewUsername != null) {
                throw new UsernameExistException("Username already exists");
            }
            if (userByNewEmail != null) {
                throw new EmailExistException("Email already exists");
            }
            return null;
        }
    }


    private Optional<User> findOptionalUserByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    private Optional<User> findUserByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    private User findUserByIdInDB(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(String.format("User with id %s not found", userId)));
    }
}
