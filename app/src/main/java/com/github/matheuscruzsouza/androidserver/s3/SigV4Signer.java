package com.github.matheuscruzsouza.androidserver.s3;

import com.github.matheuscruzsouza.androidserver.model.IamUser;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utilitário e validador de AWS Signature Version 4 (SigV4).
 * Suporta Authorization: AWS4-HMAC-SHA256 Credential=... SignedHeaders=... Signature=...
 */
public class SigV4Signer {

    private static final Pattern CREDENTIAL_PATTERN = Pattern.compile("Credential=([^/,]+)/([^/,]+)/([^/,]+)/([^/,]+)/aws4_request");
    private static final Pattern SIGNED_HEADERS_PATTERN = Pattern.compile("SignedHeaders=([^,]+)");
    private static final Pattern SIGNATURE_PATTERN = Pattern.compile("Signature=([a-fA-F0-9]+)");

    public static class AuthHeaderComponents {
        public String accessKey;
        public String dateStamp;
        public String region;
        public String service;
        public String signedHeaders;
        public String signature;
    }

    public static AuthHeaderComponents parseAuthorizationHeader(String authHeader) {
        if (authHeader == null || !authHeader.contains("AWS4-HMAC-SHA256")) {
            return null;
        }

        AuthHeaderComponents components = new AuthHeaderComponents();

        Matcher mCred = CREDENTIAL_PATTERN.matcher(authHeader);
        if (mCred.find()) {
            components.accessKey = mCred.group(1);
            components.dateStamp = mCred.group(2);
            components.region = mCred.group(3);
            components.service = mCred.group(4);
        } else {
            return null;
        }

        Matcher mSigned = SIGNED_HEADERS_PATTERN.matcher(authHeader);
        if (mSigned.find()) {
            components.signedHeaders = mSigned.group(1).trim();
        } else {
            return null;
        }

        Matcher mSig = SIGNATURE_PATTERN.matcher(authHeader);
        if (mSig.find()) {
            components.signature = mSig.group(1).trim();
        } else {
            return null;
        }

        return components;
    }

    public static boolean validate(String httpMethod,
                                   String canonicalUri,
                                   Map<String, String> queryParams,
                                   Map<String, String> headers,
                                   IamUser user,
                                   String payloadHash) {
        if (user == null || user.getSecretKey() == null) {
            return false;
        }

        String authHeader = getHeader(headers, "authorization");
        if (authHeader != null && authHeader.contains("AWS4-HMAC-SHA256")) {
            return validateHeader(httpMethod, canonicalUri, queryParams, headers, user, payloadHash, authHeader);
        }

        // Suporte a Presigned URL via query params (X-Amz-Signature)
        if (queryParams != null && queryParams.containsKey("X-Amz-Signature")) {
            return validatePresignedUrl(httpMethod, canonicalUri, queryParams, headers, user);
        }

        return false;
    }

    private static boolean validateHeader(String httpMethod,
                                          String canonicalUri,
                                          Map<String, String> queryParams,
                                          Map<String, String> headers,
                                          IamUser user,
                                          String payloadHash,
                                          String authHeader) {
        AuthHeaderComponents parsed = parseAuthorizationHeader(authHeader);
        if (parsed == null || !user.getAccessKey().equals(parsed.accessKey)) {
            return false;
        }

        String amzDate = getHeader(headers, "x-amz-date");
        if (amzDate == null) {
            String date = getHeader(headers, "date");
            if (date != null) amzDate = date;
        }

        if (amzDate == null) {
            return false;
        }

        try {
            // 1. Canonical Headers & Signed Headers
            String[] signedHeaderNames = parsed.signedHeaders.split(";");
            StringBuilder canonicalHeaders = new StringBuilder();
            for (String headerName : signedHeaderNames) {
                String cleanName = headerName.trim().toLowerCase();
                String value = getHeader(headers, cleanName);
                if (value == null) value = "";
                canonicalHeaders.append(cleanName).append(":").append(value.trim()).append("\n");
            }

            // 2. Canonical Query String
            StringBuilder canonicalQuery = new StringBuilder();
            if (queryParams != null && !queryParams.isEmpty()) {
                List<String> sortedKeys = new ArrayList<>(queryParams.keySet());
                Collections.sort(sortedKeys);
                boolean first = true;
                for (String key : sortedKeys) {
                    if (!first) canonicalQuery.append("&");
                    canonicalQuery.append(encodeRfc3986(key)).append("=").append(encodeRfc3986(queryParams.get(key)));
                    first = false;
                }
            }

            // 3. Payload hash
            String contentHash = getHeader(headers, "x-amz-content-sha256");
            if (contentHash == null) {
                contentHash = payloadHash != null ? payloadHash : sha256Hex("");
            }

            // 4. Canonical Request
            String canonicalRequest = httpMethod.toUpperCase() + "\n"
                    + canonicalUri + "\n"
                    + canonicalQuery + "\n"
                    + canonicalHeaders
                    + parsed.signedHeaders + "\n"
                    + contentHash;

            String hashedCanonicalRequest = sha256Hex(canonicalRequest);

            // 5. String to Sign
            String credentialScope = parsed.dateStamp + "/" + parsed.region + "/" + parsed.service + "/aws4_request";
            String stringToSign = "AWS4-HMAC-SHA256\n"
                    + amzDate + "\n"
                    + credentialScope + "\n"
                    + hashedCanonicalRequest;

            // 6. Signature Key derivation
            byte[] signingKey = getSignatureKey(user.getSecretKey(), parsed.dateStamp, parsed.region, parsed.service);
            byte[] signatureBytes = hmacSha256(signingKey, stringToSign);
            String calculatedSignature = toHex(signatureBytes);

            return calculatedSignature.equalsIgnoreCase(parsed.signature);
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean validatePresignedUrl(String httpMethod,
                                                String canonicalUri,
                                                Map<String, String> queryParams,
                                                Map<String, String> headers,
                                                IamUser user) {
        String signature = queryParams.get("X-Amz-Signature");
        String credential = queryParams.get("X-Amz-Credential");
        String amzDate = queryParams.get("X-Amz-Date");
        String signedHeaders = queryParams.get("X-Amz-SignedHeaders");
        String expiresStr = queryParams.get("X-Amz-Expires");

        if (signature == null || credential == null || amzDate == null || signedHeaders == null) {
            return false;
        }

        Matcher mCred = Pattern.compile("([^/,]+)/([^/,]+)/([^/,]+)/([^/,]+)/aws4_request").matcher(credential);
        if (!mCred.find()) {
            return false;
        }

        String accessKey = mCred.group(1);
        String dateStamp = mCred.group(2);
        String region = mCred.group(3);
        String service = mCred.group(4);

        if (!user.getAccessKey().equals(accessKey)) {
            return false;
        }

        try {
            // Canonical Query String (todas as query strings ordenadas exceto X-Amz-Signature)
            List<String> sortedKeys = new ArrayList<>();
            for (String k : queryParams.keySet()) {
                if (!"X-Amz-Signature".equalsIgnoreCase(k)) {
                    sortedKeys.add(k);
                }
            }
            Collections.sort(sortedKeys);

            StringBuilder canonicalQuery = new StringBuilder();
            boolean first = true;
            for (String key : sortedKeys) {
                if (!first) canonicalQuery.append("&");
                canonicalQuery.append(encodeRfc3986(key)).append("=").append(encodeRfc3986(queryParams.get(key)));
                first = false;
            }

            // Canonical Headers
            String[] signedHeaderNames = signedHeaders.split(";");
            StringBuilder canonicalHeaders = new StringBuilder();
            for (String headerName : signedHeaderNames) {
                String cleanName = headerName.trim().toLowerCase();
                String value = getHeader(headers, cleanName);
                if (value == null) value = "";
                canonicalHeaders.append(cleanName).append(":").append(value.trim()).append("\n");
            }

            // Presigned URLs utilizam UNSIGNED-PAYLOAD no canonical request
            String canonicalRequest = httpMethod.toUpperCase() + "\n"
                    + canonicalUri + "\n"
                    + canonicalQuery + "\n"
                    + canonicalHeaders
                    + signedHeaders + "\n"
                    + "UNSIGNED-PAYLOAD";

            String hashedCanonicalRequest = sha256Hex(canonicalRequest);
            String credentialScope = dateStamp + "/" + region + "/" + service + "/aws4_request";
            String stringToSign = "AWS4-HMAC-SHA256\n"
                    + amzDate + "\n"
                    + credentialScope + "\n"
                    + hashedCanonicalRequest;

            byte[] signingKey = getSignatureKey(user.getSecretKey(), dateStamp, region, service);
            byte[] signatureBytes = hmacSha256(signingKey, stringToSign);
            String calculatedSignature = toHex(signatureBytes);

            return calculatedSignature.equalsIgnoreCase(signature);
        } catch (Exception e) {
            return false;
        }
    }

    public static byte[] getSignatureKey(String key, String dateStamp, String regionName, String serviceName) throws Exception {
        byte[] kSecret = ("AWS4" + key).getBytes(StandardCharsets.UTF_8);
        byte[] kDate = hmacSha256(kSecret, dateStamp);
        byte[] kRegion = hmacSha256(kDate, regionName);
        byte[] kService = hmacSha256(kRegion, serviceName);
        return hmacSha256(kService, "aws4_request");
    }

    public static byte[] hmacSha256(byte[] key, String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec keySpec = new SecretKeySpec(key, "HmacSHA256");
        mac.init(keySpec);
        return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    }

    public static String sha256Hex(String data) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] hash = md.digest(data.getBytes(StandardCharsets.UTF_8));
        return toHex(hash);
    }

    public static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static String getHeader(Map<String, String> headers, String target) {
        if (headers == null) return null;
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(target)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static String encodeRfc3986(String value) {
        if (value == null) return "";
        try {
            return java.net.URLEncoder.encode(value, "UTF-8")
                    .replace("+", "%20")
                    .replace("*", "%2A")
                    .replace("%7E", "~");
        } catch (Exception e) {
            return value;
        }
    }
}
