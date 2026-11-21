package com.departementemployee.users.service;

import com.departementemployee.users.entities.Role;
import com.departementemployee.users.entities.User;
import com.departementemployee.users.register.RegistationRequest;

import java.util.List;

public interface UserService {
    User saveUser(User user);
    User findUserByUsername(String username);
    Role addRole(Role role);
    User addRoleToUser(String username, String rolename);
    List<User> findAllUsers();
    User registerUser(RegistationRequest request);
    User validateToken(String code);
}