package com.connectly.media;

import com.connectly.common.error.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Media uploads with defense in depth: extension allowlist, declared content-type
 * check, real magic-byte sniffing, and hard size limits. Files are stored under
 * generated names (never user-controlled) in .data/media. Swap this class for S3
 * later — the interface stays the same.
 */
@Service
public class MediaService {

    /** PNG, JPEG, GIF, WebP magic numbers. */
    private static final Map<String, byte[]> MAGIC = Map.of(
            "image/png", Arrays.copyOf(hex("89504E470D0A1A0A"), 8),
            "image/jpeg", hex("FFD8FF"),
            "image/gif", hex("47494638"),
            "image/webp", hex("52494646")
    );

    private static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024; // 5 MB
    private static final String STORE = ".data/media";

    private final Path storeDir;

    public MediaService(@Value("${app.media.dir:.data/media}") String dir) throws IOException {
        Path configured = Path.of(dir).toAbsolutePath().normalize();
        Path primary;
        try {
            Files.createDirectories(configured);
            primary = configured;
        } catch (IOException e) {
            // Cloud hosts without a mounted disk (e.g. Render free tier before a Disk
            // is attached) cannot create the configured path. Fall back to a writable
            // temp dir so the app still boots; uploads there are lost on redeploy.
            Path fallback = Path.of(System.getProperty("java.io.tmpdir"), "connectly-media");
            Files.createDirectories(fallback);
            primary = fallback;
            org.slf4j.LoggerFactory.getLogger(MediaService.class)
                    .warn("Cannot use media dir {} ({}). Falling back to {} — uploads are ephemeral until a persistent disk is mounted.",
                            configured, e.getMessage(), fallback);
        }
        this.storeDir = primary;
    }

    public record StoredMedia(String url, String contentType, long sizeBytes) {}

    public StoredMedia store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("No file provided");
        }
        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw ApiException.badRequest("Image too large (max 5 MB)");
        }

        String declared = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!MAGIC.containsKey(declared)) {
            throw ApiException.badRequest("Unsupported image type (allowed: png, jpeg, gif, webp)");
        }

        byte[] head = readHead(file);
        if (!matchesMagic(declared, head)) {
            // A text file renamed to .png dies right here.
            throw ApiException.badRequest("File content does not match its image type");
        }

        String ext = switch (declared) {
            case "image/png" -> "png";
            case "image/jpeg" -> "jpg";
            case "image/gif" -> "gif";
            case "image/webp" -> "webp";
            default -> throw ApiException.badRequest("Unsupported image type");
        };
        String name = UUID.randomUUID() + "." + ext;
        Path target = storeDir.resolve(name);
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to store media", e);
        }
        return new StoredMedia("/media/" + name, declared, file.getSize());
    }

    private static byte[] readHead(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            byte[] head = new byte[12];
            int n = in.read(head);
            return n <= 0 ? new byte[0] : Arrays.copyOf(head, n);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read upload", e);
        }
    }

    private static boolean matchesMagic(String contentType, byte[] head) {
        byte[] magic = MAGIC.get(contentType);
        if (magic == null || head.length < magic.length) return false;
        // WebP: "RIFF" at 0 and "WEBP" at 8 — we only check the RIFF prefix here.
        if ("image/webp".equals(contentType)) {
            return head[0] == magic[0] && head[1] == magic[1] && head[2] == magic[2] && head[3] == magic[3];
        }
        for (int i = 0; i < magic.length; i++) {
            if (head[i] != magic[i]) return false;
        }
        return true;
    }

    private static byte[] hex(String s) {
        int len = s.length();
        byte[] out = new byte[len / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) ((Character.digit(s.charAt(i * 2), 16) << 4) + Character.digit(s.charAt(i * 2 + 1), 16));
        }
        return out;
    }
}
