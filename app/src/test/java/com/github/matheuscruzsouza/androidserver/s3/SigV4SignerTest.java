package com.github.matheuscruzsouza.androidserver.s3;

import com.github.matheuscruzsouza.androidserver.model.IamUser;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class SigV4SignerTest {

    @Test
    public void testSigV4CanonicalCalculationAndValidation() throws Exception {
        IamUser user = new IamUser();
        user.setUsername("admin");
        user.setAccessKey("AKIAIOSFODNN7EXAMPLE");
        user.setSecretKey("wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY");
        user.setEnabled(true);

        String httpMethod = "GET";
        String canonicalUri = "/test-bucket/test.txt";
        String dateStamp = "20261101";
        String amzDate = "20261101T120000Z";
        String region = "us-east-1";
        String service = "s3";

        Map<String, String> queryParams = new HashMap<>();
        Map<String, String> headers = new HashMap<>();
        headers.put("host", "localhost:8080");
        headers.put("x-amz-date", amzDate);
        headers.put("x-amz-content-sha256", "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");

        String signedHeaders = "host;x-amz-content-sha256;x-amz-date";
        String canonicalHeaders = "host:localhost:8080\n" +
                "x-amz-content-sha256:e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855\n" +
                "x-amz-date:" + amzDate + "\n";

        String canonicalRequest = httpMethod + "\n"
                + canonicalUri + "\n"
                + "\n"
                + canonicalHeaders
                + signedHeaders + "\n"
                + headers.get("x-amz-content-sha256");

        String hashedCanonicalRequest = SigV4Signer.sha256Hex(canonicalRequest);
        String credentialScope = dateStamp + "/" + region + "/" + service + "/aws4_request";
        String stringToSign = "AWS4-HMAC-SHA256\n"
                + amzDate + "\n"
                + credentialScope + "\n"
                + hashedCanonicalRequest;

        byte[] signingKey = SigV4Signer.getSignatureKey(user.getSecretKey(), dateStamp, region, service);
        String signature = SigV4Signer.toHex(SigV4Signer.hmacSha256(signingKey, stringToSign));

        String authHeader = "AWS4-HMAC-SHA256 Credential=" + user.getAccessKey() + "/" + credentialScope
                + ", SignedHeaders=" + signedHeaders + ", Signature=" + signature;

        headers.put("authorization", authHeader);

        boolean isValid = SigV4Signer.validate(httpMethod, canonicalUri, queryParams, headers, user, null);
        assertTrue("A assinatura calculada SigV4 deve ser considerada válida", isValid);

        // Assinatura com chave secreta incorreta deve falhar
        IamUser wrongUser = new IamUser();
        wrongUser.setAccessKey(user.getAccessKey());
        wrongUser.setSecretKey("wrongSecretKey");
        boolean isInvalid = SigV4Signer.validate(httpMethod, canonicalUri, queryParams, headers, wrongUser, null);
        assertFalse("Assinatura com chave divergente deve ser rejeitada", isInvalid);
    }

    @Test
    public void testPresignedUrlValidation() throws Exception {
        IamUser user = new IamUser();
        user.setUsername("presigned_user");
        user.setAccessKey("AKIAIOSFODNN7EXAMPLE");
        user.setSecretKey("wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY");
        user.setEnabled(true);

        String httpMethod = "GET";
        String canonicalUri = "/photos/summer.jpg";
        String dateStamp = "20261101";
        String amzDate = "20261101T150000Z";
        String region = "us-east-1";
        String service = "s3";
        String credentialScope = user.getAccessKey() + "/" + dateStamp + "/" + region + "/" + service + "/aws4_request";

        Map<String, String> queryParams = new HashMap<>();
        queryParams.put("X-Amz-Algorithm", "AWS4-HMAC-SHA256");
        queryParams.put("X-Amz-Credential", credentialScope);
        queryParams.put("X-Amz-Date", amzDate);
        queryParams.put("X-Amz-Expires", "3600");
        queryParams.put("X-Amz-SignedHeaders", "host");

        Map<String, String> headers = new HashMap<>();
        headers.put("host", "localhost:8080");

        // Canonical Request para Presigned URL (UNSIGNED-PAYLOAD)
        String canonicalHeaders = "host:localhost:8080\n";
        String canonicalQuery = "X-Amz-Algorithm=AWS4-HMAC-SHA256"
                + "&X-Amz-Credential=" + java.net.URLEncoder.encode(credentialScope, "UTF-8")
                + "&X-Amz-Date=" + amzDate
                + "&X-Amz-Expires=3600"
                + "&X-Amz-SignedHeaders=host";

        String canonicalRequest = httpMethod + "\n"
                + canonicalUri + "\n"
                + canonicalQuery + "\n"
                + canonicalHeaders
                + "host\n"
                + "UNSIGNED-PAYLOAD";

        String hashedCanonicalRequest = SigV4Signer.sha256Hex(canonicalRequest);
        String stringToSign = "AWS4-HMAC-SHA256\n"
                + amzDate + "\n"
                + dateStamp + "/" + region + "/" + service + "/aws4_request\n"
                + hashedCanonicalRequest;

        byte[] signingKey = SigV4Signer.getSignatureKey(user.getSecretKey(), dateStamp, region, service);
        String signature = SigV4Signer.toHex(SigV4Signer.hmacSha256(signingKey, stringToSign));

        queryParams.put("X-Amz-Signature", signature);

        boolean valid = SigV4Signer.validate(httpMethod, canonicalUri, queryParams, headers, user, null);
        assertTrue("Presigned URL legítima deve ser validada com sucesso", valid);
    }
}
