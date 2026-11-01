package com.github.matheuscruzsouza.androidserver.controller;

import com.github.matheuscruzsouza.androidserver.dto.request.CreateIamUserRequest;
import com.github.matheuscruzsouza.androidserver.dto.response.IamUserResponse;
import com.github.matheuscruzsouza.androidserver.model.IamUser;
import com.github.matheuscruzsouza.androidserver.service.IamService;
import com.github.matheuscruzsouza.nanospring.annotation.Autowired;
import com.github.matheuscruzsouza.nanospring.annotation.DeleteMethod;
import com.github.matheuscruzsouza.nanospring.annotation.GetMethod;
import com.github.matheuscruzsouza.nanospring.annotation.PathVariable;
import com.github.matheuscruzsouza.nanospring.annotation.PostMethod;
import com.github.matheuscruzsouza.nanospring.annotation.RequestBody;
import com.github.matheuscruzsouza.nanospring.annotation.RestController;
import com.github.matheuscruzsouza.nanospring.http.HttpStatus;
import com.github.matheuscruzsouza.nanospring.http.ResponseEntity;
import com.github.matheuscruzsouza.nanospring.openapi.annotation.Hidden;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Hidden
@RestController("/api/iam")
public class IamController {

    @Autowired
    private IamService iamService;

    public IamController() {
    }

    public IamController(IamService iamService) {
        this.iamService = iamService;
    }

    private IamService getIamService() {
        if (iamService == null) {
            iamService = new IamService();
        }
        return iamService;
    }

    // Listar credenciais mascarando segredos
    @GetMethod("/users")
    public List<IamUserResponse> listUsers() {
        List<IamUser> users = getIamService().listUsers();
        List<IamUserResponse> result = new ArrayList<>();
        for (IamUser u : users) {
            String sec = u.getSecretKey();
            String masked = (sec != null && sec.length() > 6)
                    ? sec.substring(0, 4) + "..." + sec.substring(sec.length() - 2)
                    : "******";
            result.add(new IamUserResponse(
                    u.getId(),
                    u.getName(),
                    u.getAccessKey(),
                    masked,
                    u.getPolicy(),
                    u.isEnabled(),
                    u.getCreatedAt()
            ));
        }
        return result;
    }

    // Criar nova credencial
    @PostMethod("/users")
    public ResponseEntity<?> createUser(@RequestBody CreateIamUserRequest request) {
        if (request == null || request.getName() == null || request.getName().trim().isEmpty()
                || request.getAccessKey() == null || request.getAccessKey().trim().isEmpty()
                || request.getSecretKey() == null || request.getSecretKey().trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Collections.singletonMap("error", "Campos 'name', 'accessKey' e 'secretKey' são obrigatórios"));
        }

        String policy = (request.getPolicy() != null && !request.getPolicy().trim().isEmpty())
                ? request.getPolicy()
                : "ReadOnly";

        boolean created = getIamService().createUser(request.getName(), request.getAccessKey(), request.getSecretKey(), policy);
        if (created) {
            Map<String, String> response = new HashMap<>();
            response.put("status", "created");
            response.put("accessKey", request.getAccessKey());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } else {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.singletonMap("error", "Falha ao criar credencial IAM"));
        }
    }

    // Revogar credencial
    @DeleteMethod("/users/:accessKey")
    public ResponseEntity<?> deleteUser(@PathVariable("accessKey") String accessKey) {
        boolean deleted = getIamService().deleteUser(accessKey);
        if (deleted) {
            return ResponseEntity.ok(Collections.singletonMap("status", "deleted"));
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Collections.singletonMap("error", "Credencial não encontrada"));
        }
    }
}
