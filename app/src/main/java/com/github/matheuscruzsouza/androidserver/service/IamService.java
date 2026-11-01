package com.github.matheuscruzsouza.androidserver.service;

import com.github.matheuscruzsouza.androidserver.model.IamUser;
import com.github.matheuscruzsouza.androidserver.repository.IamUserRepository;
import com.github.matheuscruzsouza.nanospring.annotation.Autowired;
import com.github.matheuscruzsouza.nanospring.annotation.Service;

import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import fi.iki.elonen.NanoHTTPD;

@Service
public class IamService {

    @Autowired
    private IamUserRepository iamUserRepository;

    private static final Pattern AWS4_CREDENTIAL_PATTERN = Pattern.compile("Credential=([^/]+)/");

    public IamService() {
    }

    public IamService(android.content.Context context) {
        this.iamUserRepository = new IamUserRepository(context);
    }

    public IamService(IamUserRepository iamUserRepository) {
        this.iamUserRepository = iamUserRepository;
    }

    private IamUserRepository getRepository() {
        if (iamUserRepository == null) {
            iamUserRepository = new IamUserRepository();
        }
        return iamUserRepository;
    }

    public List<IamUser> listUsers() {
        return getRepository().findAll();
    }

    public boolean createUser(String name, String accessKey, String secretKey, String policy) {
        String token = generateResetToken();
        IamUser user = new IamUser(name, accessKey, secretKey, policy, true);
        user.setResetToken(token);
        return getRepository().save(user);
    }

    public String generateResetToken() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        java.security.SecureRandom random = new java.security.SecureRandom();
        StringBuilder sb = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    public String hashPassword(String password) {
        if (password == null) return null;
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(password.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return password;
        }
    }

    public boolean setPasswordWithToken(String username, String token, String newPassword) {
        if (username == null || token == null || newPassword == null || newPassword.trim().isEmpty()) {
            return false;
        }
        IamUser user = getRepository().findByNameOrAccessKey(username.trim());
        if (user == null) {
            return false;
        }
        String storedToken = user.getResetToken();
        if (storedToken == null || !storedToken.equalsIgnoreCase(token.trim())) {
            return false;
        }
        String hash = hashPassword(newPassword.trim());
        return getRepository().updatePassword(user.getAccessKey(), hash);
    }

    public boolean rotateResetToken(String accessKey) {
        String token = generateResetToken();
        return getRepository().updateResetToken(accessKey, token);
    }

    public IamUser authenticateUsernamePassword(String username, String password) {
        if (username == null || password == null) return null;
        IamUser user = getRepository().findByNameOrAccessKey(username.trim());
        if (user == null || !user.isEnabled()) return null;
        String hash = hashPassword(password.trim());
        if (hash != null && hash.equals(user.getPasswordHash())) {
            return user;
        }
        return null;
    }

    public boolean updatePolicy(String accessKey, String policy) {
        return getRepository().updatePolicy(accessKey, policy);
    }

    public boolean updateEnabled(String accessKey, boolean enabled) {
        return getRepository().updateEnabled(accessKey, enabled);
    }

    public boolean updateSecretKey(String accessKey, String secretKey) {
        return getRepository().updateSecretKey(accessKey, secretKey);
    }

    public boolean deleteUser(String accessKey) {
        return getRepository().deleteByAccessKey(accessKey);
    }

    public IamUser authenticate(NanoHTTPD.IHTTPSession session) {
        if (session == null) return null;

        Map<String, String> headers = session.getHeaders();
        String authHeader = null;
        if (headers != null) {
            authHeader = headers.get("authorization");
            if (authHeader == null) {
                authHeader = headers.get("Authorization");
            }
        }

        // 1. Authorization: Bearer <SecretKey>
        if (authHeader != null && authHeader.toLowerCase().startsWith("bearer ")) {
            String secretKey = authHeader.substring(7).trim();
            return getRepository().findBySecretKey(secretKey);
        }

        // 2. Authorization: Basic <Base64(Username:Password)> ou <Base64(AccessKey:SecretKey)>
        if (authHeader != null && authHeader.toLowerCase().startsWith("basic ")) {
            try {
                String encoded = authHeader.substring(6).trim();
                byte[] decoded = Base64.decode(encoded, Base64.DEFAULT);
                String credentials = new String(decoded, StandardCharsets.UTF_8);
                String[] parts = credentials.split(":", 2);
                if (parts.length == 2) {
                    // Primeiro tenta autenticar via Username e Senha (novo padrão)
                    IamUser userPass = authenticateUsernamePassword(parts[0], parts[1]);
                    if (userPass != null) {
                        return userPass;
                    }
                    // Fallback para autenticação de API por AccessKey:SecretKey
                    IamUser user = getRepository().findByAccessKey(parts[0]);
                    if (user != null && parts[1].equals(user.getSecretKey())) {
                        return user;
                    }
                }
            } catch (Exception ignored) {}
        }

        // 3. AWS4-HMAC-SHA256 Credential=<AccessKey>/...
        if (authHeader != null && authHeader.contains("AWS4-HMAC-SHA256")) {
            Matcher m = AWS4_CREDENTIAL_PATTERN.matcher(authHeader);
            if (m.find()) {
                String accessKey = m.group(1);
                IamUser user = getRepository().findByAccessKey(accessKey);
                if (user != null && user.isEnabled()) {
                    boolean valid = com.github.matheuscruzsouza.androidserver.s3.SigV4Signer.validate(
                            session.getMethod() != null ? session.getMethod().name() : "GET",
                            session.getUri(),
                            session.getParms(),
                            session.getHeaders(),
                            user,
                            null
                    );
                    if (valid) {
                        return user;
                    }
                }
            }
            return null;
        }

        // 4. Presigned URL via query params (X-Amz-Signature & X-Amz-Credential)
        Map<String, String> parms = session.getParms();
        if (parms != null && parms.containsKey("X-Amz-Signature") && parms.containsKey("X-Amz-Credential")) {
            String credential = parms.get("X-Amz-Credential");
            int slash = credential.indexOf('/');
            String accessKey = slash > 0 ? credential.substring(0, slash) : credential;
            IamUser user = getRepository().findByAccessKey(accessKey);
            if (user != null && user.isEnabled()) {
                boolean valid = com.github.matheuscruzsouza.androidserver.s3.SigV4Signer.validate(
                        session.getMethod() != null ? session.getMethod().name() : "GET",
                        session.getUri(),
                        parms,
                        session.getHeaders(),
                        user,
                        null
                );
                if (valid) {
                    return user;
                }
            }
            return null;
        }

        // 5. AWS <AccessKey>:<Signature> (S3 V2)
        if (authHeader != null && authHeader.startsWith("AWS ")) {
            String rest = authHeader.substring(4).trim();
            int colon = rest.indexOf(':');
            if (colon > 0) {
                String accessKey = rest.substring(0, colon);
                return getRepository().findByAccessKey(accessKey);
            }
        }

        // 5. Cookie: android_server_auth=...
        if (headers != null) {
            String cookieHeader = headers.get("cookie");
            if (cookieHeader == null) {
                cookieHeader = headers.get("Cookie");
            }
            if (cookieHeader != null) {
                for (String c : cookieHeader.split(";")) {
                    String[] pair = c.trim().split("=", 2);
                    if (pair.length == 2 && "android_server_auth".equalsIgnoreCase(pair[0].trim())) {
                        String cookieVal = pair[1].trim();
                        try {
                            cookieVal = java.net.URLDecoder.decode(cookieVal, "UTF-8");
                        } catch (Exception ignored) {}
                        if (cookieVal.toLowerCase().startsWith("bearer ")) {
                            IamUser u = getRepository().findBySecretKey(cookieVal.substring(7).trim());
                            if (u != null) return u;
                        } else if (cookieVal.toLowerCase().startsWith("basic ")) {
                            try {
                                byte[] decoded = Base64.decode(cookieVal.substring(6).trim(), Base64.DEFAULT);
                                String creds = new String(decoded, StandardCharsets.UTF_8);
                                String[] parts = creds.split(":", 2);
                                if (parts.length == 2) {
                                    IamUser userPass = authenticateUsernamePassword(parts[0], parts[1]);
                                    if (userPass != null) return userPass;
                                    IamUser u = getRepository().findByAccessKey(parts[0]);
                                    if (u != null && parts[1].equals(u.getSecretKey())) return u;
                                }
                            } catch (Exception ignored) {}
                        } else {
                            // Raw token fallback
                            IamUser u = getRepository().findBySecretKey(cookieVal);
                            if (u != null) return u;
                            u = getRepository().findByAccessKey(cookieVal);
                            if (u != null) return u;
                        }
                    }
                }
            }
        }

        // 6. Query parameters: ?access_key=... ou ?token=...
        if (parms != null) {
            String queryKey = parms.get("access_key");
            if (queryKey != null) {
                return getRepository().findByAccessKey(queryKey);
            }
            String secretToken = parms.get("token");
            if (secretToken != null) {
                return getRepository().findBySecretKey(secretToken);
            }
        }

        return null;
    }

    public boolean canPerform(IamUser user, String action) {
        if (user == null || !user.isEnabled()) return false;
        String policy = user.getPolicy();
        if (policy == null) return false;

        if ("Admin".equalsIgnoreCase(policy) || "*".equals(policy)) {
            return true;
        }

        if ("ReadOnly".equalsIgnoreCase(policy)) {
            return action.equals("s3:ListAllMyBuckets") ||
                   action.equals("s3:ListBucket") ||
                   action.equals("s3:GetObject");
        }

        if ("WriteOnly".equalsIgnoreCase(policy)) {
            return action.equals("s3:ListAllMyBuckets") ||
                   action.equals("s3:ListBucket") ||
                   action.equals("s3:GetObject") ||
                   action.equals("s3:PutObject") ||
                   action.equals("s3:CreateBucket") ||
                   action.equals("s3:DeleteBucket") ||
                   action.equals("s3:DeleteObject");
        }

        return false;
    }

    /**
     * Verifica se o usuário tem permissão para executar a ação dentro do bucket especificado.
     * Concede autoridade de proprietário (owner) se o bucket corresponder ao bucket próprio do usuário.
     */
    public boolean canPerformInBucket(IamUser user, String action, String bucketName, String userDefaultBucket) {
        if (user == null || !user.isEnabled()) return false;
        String policy = user.getPolicy();
        if (policy == null) return false;

        if ("Admin".equalsIgnoreCase(policy) || "*".equals(policy)) {
            return true;
        }

        // Autoridade do owner no seu próprio bucket
        boolean isOwner = bucketName != null && userDefaultBucket != null &&
                bucketName.trim().equalsIgnoreCase(userDefaultBucket.trim());
        if (isOwner) {
            return true;
        }

        return canPerform(user, action);
    }
}
