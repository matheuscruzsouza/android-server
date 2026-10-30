package com.github.matheuscruzsouza.androidserver.service;

import com.github.matheuscruzsouza.androidserver.model.IamUser;
import com.github.matheuscruzsouza.androidserver.repository.IamUserRepository;
import com.github.matheuscruzsouza.nanospring.annotation.Autowired;
import com.github.matheuscruzsouza.nanospring.annotation.Service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
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
        IamUser user = new IamUser(name, accessKey, secretKey, policy, true);
        return getRepository().save(user);
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

        // 2. Authorization: Basic <Base64(AccessKey:SecretKey)>
        if (authHeader != null && authHeader.toLowerCase().startsWith("basic ")) {
            try {
                String encoded = authHeader.substring(6).trim();
                byte[] decoded = Base64.getDecoder().decode(encoded);
                String credentials = new String(decoded, StandardCharsets.UTF_8);
                String[] parts = credentials.split(":", 2);
                if (parts.length == 2) {
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
                return getRepository().findByAccessKey(accessKey);
            }
        }

        // 4. AWS <AccessKey>:<Signature> (S3 V2)
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
                                byte[] decoded = Base64.getDecoder().decode(cookieVal.substring(6).trim());
                                String creds = new String(decoded, StandardCharsets.UTF_8);
                                String[] parts = creds.split(":", 2);
                                if (parts.length == 2) {
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

        // 6. Query parameters: ?access_key=... ou ?auth=...
        Map<String, String> parms = session.getParms();
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
                   action.equals("s3:PutObject");
        }

        return false;
    }
}
