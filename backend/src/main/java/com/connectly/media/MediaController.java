package com.connectly.media;

import com.connectly.security.RateLimiter;
import com.connectly.common.error.ApiException;
import com.connectly.user.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;

@RestController
public class MediaController {

    private final MediaService media;
    private final RateLimiter rateLimiter;
    private final Path storeDir;

    public MediaController(MediaService media, RateLimiter rateLimiter,
                           @Value("${app.media.dir:.data/media}") String dir) {
        this.media = media;
        this.rateLimiter = rateLimiter;
        this.storeDir = Path.of(dir).toAbsolutePath().normalize();
    }

    public record UploadResponse(String url, String contentType, long sizeBytes) {}

    @PostMapping("/api/v1/media")
    public UploadResponse upload(@AuthenticationPrincipal User me,
                                 @RequestParam("file") MultipartFile file) {
        if (!rateLimiter.allow("media-upload:" + me.getUsername(), 20, 60)) {
            throw ApiException.tooManyRequests("Too many uploads, slow down");
        }
        MediaService.StoredMedia stored = media.store(file);
        return new UploadResponse(stored.url(), stored.contentType(), stored.sizeBytes());
    }

    /** Public, read-only serving of stored media (generated names only). */
    @GetMapping("/media/{name}")
    public ResponseEntity<Resource> serve(@PathVariable String name) {
        // Path-traversal guard: only simple generated names ever resolve.
        if (!name.matches("[A-Za-z0-9._-]{1,80}")) {
            return ResponseEntity.badRequest().build();
        }
        Path file = storeDir.resolve(name).normalize();
        if (!file.startsWith(storeDir) || !Files.isRegularFile(file)) {
            return ResponseEntity.notFound().build();
        }
        try {
            String probe = Files.probeContentType(file);
            MediaType mt = switch (probe == null ? "" : probe) {
                case "image/png" -> MediaType.IMAGE_PNG;
                case "image/jpeg" -> MediaType.IMAGE_JPEG;
                case "image/gif" -> MediaType.IMAGE_GIF;
                case "image/webp" -> MediaType.parseMediaType("image/webp");
                default -> MediaType.APPLICATION_OCTET_STREAM;
            };
            Resource res = new org.springframework.core.io.FileSystemResource(file);
            return ResponseEntity.ok().contentType(mt).body(res);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }
}
