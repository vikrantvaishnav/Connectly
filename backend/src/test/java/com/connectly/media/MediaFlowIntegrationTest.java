package com.connectly.media;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 5 media uploads: magic-byte validation, auth, and type rejection.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MediaFlowIntegrationTest {

    @LocalServerPort
    int port;

    private RestClient rest;
    private String token;

    final Random rnd = new Random();

    @BeforeAll
    void setUp() {
        rest = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(code -> true, (req, res) -> {})
                .build();
        String suffix = String.valueOf(rnd.nextInt(1_000_000));
        String handle = "media" + suffix;
        rest.post().uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "firstName", "Media", "lastName", "Test",
                        "username", handle,
                        "email", handle + "@example.com",
                        "password", "Str0ng-Passphrase-9x!"))
                .retrieve().toBodilessEntity();
        ResponseEntity<Map> login = rest.post().uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("identifier", handle + "@example.com", "password", "Str0ng-Passphrase-9x!"))
                .retrieve().toEntity(Map.class);
        token = (String) login.getBody().get("accessToken");
    }

    private ResponseEntity<Map> upload(byte[] bytes, String filename, String contentType) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(bytes) {
            @Override public String getFilename() { return filename; }
        });
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return rest.post().uri("/api/v1/media")
                .headers(h -> h.addAll(headers))
                .body(body)
                .retrieve().toEntity(Map.class);
    }

    @Test
    void mediaFlow() {
        // valid PNG passes (real magic bytes)
        byte[] png = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13};
        ResponseEntity<Map> ok = upload(png, "x.png", "image/png");
        assertThat(ok.getStatusCode().value()).isEqualTo(200);
        assertThat((String) ok.getBody().get("url")).startsWith("/media/");

        // served publicly
        String url = (String) ok.getBody().get("url");
        ResponseEntity<byte[]> served = rest.get().uri(url).retrieve().toEntity(byte[].class);
        assertThat(served.getStatusCode().value()).isEqualTo(200);

        // a text file renamed to .png is rejected by magic-byte sniffing
        byte[] fake = "this is definitely not a png".getBytes();
        ResponseEntity<Map> bad = upload(fake, "evil.png", "image/png");
        assertThat(bad.getStatusCode().value()).isEqualTo(400);

        // unsupported content type rejected
        byte[] txt = "hello".getBytes();
        ResponseEntity<Map> bad2 = upload(txt, "notes.txt", "text/plain");
        assertThat(bad2.getStatusCode().value()).isEqualTo(400);

        // unauthenticated upload rejected
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(png) {
            @Override public String getFilename() { return "x.png"; }
        });
        ResponseEntity<Map> anon = rest.post().uri("/api/v1/media")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve().toEntity(Map.class);
        assertThat(anon.getStatusCode().value()).isEqualTo(401);

        // path traversal on the serving endpoint never resolves
        ResponseEntity<String> trav = rest.get().uri("/media/..%2Fapplication.yml")
                .retrieve().toEntity(String.class);
        assertThat(trav.getStatusCode().value()).isIn(400, 404);
    }
}
