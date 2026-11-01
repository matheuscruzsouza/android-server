package com.github.matheuscruzsouza.androidserver.controller;

import com.github.matheuscruzsouza.androidserver.model.IamUser;
import com.github.matheuscruzsouza.androidserver.service.IamService;
import com.github.matheuscruzsouza.nanospring.annotation.Autowired;
import com.github.matheuscruzsouza.nanospring.annotation.DeleteMethod;
import com.github.matheuscruzsouza.nanospring.annotation.GetMethod;
import com.github.matheuscruzsouza.nanospring.annotation.PathVariable;
import com.github.matheuscruzsouza.nanospring.annotation.PostMethod;
import com.github.matheuscruzsouza.nanospring.annotation.RequestBody;
import com.github.matheuscruzsouza.nanospring.annotation.RestController;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.Response;
import fi.iki.elonen.NanoHTTPD.Response.Status;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    public List<Map<String, Object>> listUsers() {
        List<IamUser> users = getIamService().listUsers();
        List<Map<String, Object>> result = new ArrayList<>();
        for (IamUser u : users) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", u.getId());
            map.put("name", u.getName());
            map.put("accessKey", u.getAccessKey());
            String sec = u.getSecretKey();
            map.put("maskedSecretKey", (sec != null && sec.length() > 6) ? sec.substring(0, 4) + "..." + sec.substring(sec.length() - 2) : "******");
            map.put("policy", u.getPolicy());
            map.put("enabled", u.isEnabled());
            map.put("createdAt", u.getCreatedAt());
            result.add(map);
        }
        return result;
    }

    // Criar nova credencial
    @PostMethod("/users")
    public Response createUser(@RequestBody Map<String, String> body) {
        if (body == null || !body.containsKey("name") || !body.containsKey("accessKey") || !body.containsKey("secretKey")) {
            return NanoHTTPD.newFixedLengthResponse(Status.BAD_REQUEST, "application/json", "{\"error\":\"Campos 'name', 'accessKey' e 'secretKey' são obrigatórios\"}");
        }

        String name = body.get("name");
        String accessKey = body.get("accessKey");
        String secretKey = body.get("secretKey");
        String policy = body.getOrDefault("policy", "ReadOnly");

        boolean created = getIamService().createUser(name, accessKey, secretKey, policy);
        if (created) {
            return NanoHTTPD.newFixedLengthResponse(Status.OK, "application/json", "{\"status\":\"created\",\"accessKey\":\"" + accessKey + "\"}");
        } else {
            return NanoHTTPD.newFixedLengthResponse(Status.INTERNAL_ERROR, "application/json", "{\"error\":\"Falha ao criar credencial IAM\"}");
        }
    }

    // Revogar credencial
    @DeleteMethod("/users/:accessKey")
    public Response deleteUser(@PathVariable("accessKey") String accessKey) {
        boolean deleted = getIamService().deleteUser(accessKey);
        if (deleted) {
            return NanoHTTPD.newFixedLengthResponse(Status.OK, "application/json", "{\"status\":\"deleted\"}");
        } else {
            return NanoHTTPD.newFixedLengthResponse(Status.NOT_FOUND, "application/json", "{\"error\":\"Credencial não encontrada\"}");
        }
    }
}
