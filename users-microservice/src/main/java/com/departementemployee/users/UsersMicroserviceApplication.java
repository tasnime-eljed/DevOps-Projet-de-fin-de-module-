package com.departementemployee.users;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.departementemployee.users.entities.Role;
import com.departementemployee.users.entities.User;
import com.departementemployee.users.service.UserService;

import jakarta.annotation.PostConstruct;

@SpringBootApplication
public class UsersMicroserviceApplication {

    @Autowired
    UserService userService;

    public static void main(String[] args) {
        SpringApplication.run(UsersMicroserviceApplication.class, args);
    }

    /*@PostConstruct
    void init_users() {
        // rôles
        userService.addRole(new Role(null, "ADMIN"));
        userService.addRole(new Role(null, "USER"));

        // utilisateurs
        userService.saveUser(new User(null, "admin", "123", true, null));
        userService.saveUser(new User(null, "tasnime", "123", true, null));
        userService.saveUser(new User(null, "aman", "123", true, null));

        // rôles des utilisateurs
        userService.addRoleToUser("admin", "ADMIN");
        userService.addRoleToUser("admin", "USER");
        userService.addRoleToUser("tasnime", "USER");
        userService.addRoleToUser("aman", "USER");
    }*/
}