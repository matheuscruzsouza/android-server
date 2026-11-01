package com.github.matheuscruzsouza.androidserver.controller;

import com.github.matheuscruzsouza.nanospring.annotation.Autowired;
import com.github.matheuscruzsouza.nanospring.annotation.GetMethod;
import com.github.matheuscruzsouza.nanospring.annotation.PostMethod;
import com.github.matheuscruzsouza.nanospring.annotation.PathVariable;
import com.github.matheuscruzsouza.nanospring.annotation.RequestBody;
import com.github.matheuscruzsouza.nanospring.annotation.RequestParam;
import com.github.matheuscruzsouza.nanospring.annotation.RestController;
import com.github.matheuscruzsouza.nanospring.openapi.annotation.Hidden;
import com.github.matheuscruzsouza.nanospring.http.HttpStatus;
import com.github.matheuscruzsouza.nanospring.http.ResponseEntity;
import com.github.matheuscruzsouza.androidserver.model.User;
import com.github.matheuscruzsouza.androidserver.service.UserService;

import java.util.List;
import java.util.stream.Collectors;

@Hidden
@RestController("/api/users")
public class UserController {

    @Autowired
    private UserService userService;

    private UserService getUserService() {
        if (userService == null) {
            userService = new UserService();
        }
        return userService;
    }

    @GetMethod("")
    public List<User> getAllUsers() {
        return getUserService().getUsers();
    }

    @GetMethod("/:id")
    public ResponseEntity<User> getUserById(@PathVariable("id") String id) {
        User user = getUserService().findById(Integer.parseInt(id));
        if (user == null) {
            throw new IndexOutOfBoundsException("Usuário não encontrado com id: " + id);
        }
        return ResponseEntity.ok(user);
    }

    @PostMethod("")
    public ResponseEntity<User> createUser(@RequestBody User user) {
        if (user == null || user.getUsername() == null || user.getUsername().trim().isEmpty() ||
            user.getName() == null || user.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Campos 'username' e 'name' são obrigatórios");
        }
        User created = getUserService().addUser(user);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMethod("/search")
    public List<User> searchUsers(@RequestParam("q") String query) {
        if (query == null || query.isEmpty()) return getUserService().getUsers();
        return getUserService().getUsers().stream()
                .filter(u -> u.getName().toLowerCase().contains(query.toLowerCase()) || 
                             u.getUsername().toLowerCase().contains(query.toLowerCase()))
                .collect(Collectors.toList());
    }
}
