package com.org.ecommerce.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.org.ecommerce.config.AppProperties;
import com.org.ecommerce.exception.ApiException;

/**
 * Stores product images on the local filesystem (mount a volume in Docker; swap for S3-compatible
 * storage behind the same methods if needed). The content type is decided from the file's magic
 * bytes, never from the client-supplied header or file name, and names are server-generated.
 */
@Service
public class ImageStorageService {

    public static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final Pattern SAFE_KEY = Pattern.compile("^products/[a-f0-9]{32}\\.(jpg|png|webp)$");

    private final Path root;
    private final String publicPath;

    public ImageStorageService(AppProperties properties) throws IOException {
        this.root = Path.of(properties.storage().uploadDir()).toAbsolutePath().normalize();
        this.publicPath = properties.storage().publicPath();
        Files.createDirectories(root.resolve("products"));
    }

    public record StoredImage(String key, String url, String contentType, long sizeBytes) {
    }

    public StoredImage store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("EMPTY_FILE", "No file uploaded.");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new ApiException(org.springframework.http.HttpStatus.CONTENT_TOO_LARGE, "FILE_TOO_LARGE",
                    "Uploaded file is too large (max 5 MB).");
        }
        try (InputStream in = file.getInputStream()) {
            byte[] header = in.readNBytes(12);
            ImageType type = detect(header)
                    .orElseThrow(() -> ApiException.badRequest("UNSUPPORTED_IMAGE", "Only JPEG, PNG and WebP images are allowed."));
            String key = "products/" + UUID.randomUUID().toString().replace("-", "") + "." + type.extension;
            Path target = resolve(key);
            try (InputStream full = file.getInputStream()) {
                Files.copy(full, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return new StoredImage(key, publicPath + "/" + key, type.contentType, file.getSize());
        } catch (IOException e) {
            throw new IllegalStateException("Could not store image", e);
        }
    }

    public Optional<Path> load(String key) {
        if (key == null || !SAFE_KEY.matcher(key).matches()) {
            return Optional.empty();
        }
        Path p = resolve(key);
        return Files.isRegularFile(p) ? Optional.of(p) : Optional.empty();
    }

    public void delete(String key) {
        load(key).ifPresent(p -> {
            try {
                Files.deleteIfExists(p);
            } catch (IOException ignored) {
                // Orphaned files are harmless; the DB row is the source of truth.
            }
        });
    }

    public static String contentTypeFor(String key) {
        if (key.endsWith(".png")) {
            return "image/png";
        }
        return key.endsWith(".webp") ? "image/webp" : "image/jpeg";
    }

    private Path resolve(String key) {
        Path p = root.resolve(key).normalize();
        if (!p.startsWith(root)) {
            throw ApiException.badRequest("INVALID_PATH", "Invalid path.");
        }
        return p;
    }

    private enum ImageType {
        JPEG("jpg", "image/jpeg"), PNG("png", "image/png"), WEBP("webp", "image/webp");

        final String extension;
        final String contentType;

        ImageType(String extension, String contentType) {
            this.extension = extension;
            this.contentType = contentType;
        }
    }

    static Optional<ImageType> detect(byte[] h) {
        if (h.length >= 3 && (h[0] & 0xFF) == 0xFF && (h[1] & 0xFF) == 0xD8 && (h[2] & 0xFF) == 0xFF) {
            return Optional.of(ImageType.JPEG);
        }
        if (h.length >= 8 && Arrays.equals(Arrays.copyOf(h, 8),
                new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A})) {
            return Optional.of(ImageType.PNG);
        }
        if (h.length >= 12 && h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F'
                && h[8] == 'W' && h[9] == 'E' && h[10] == 'B' && h[11] == 'P') {
            return Optional.of(ImageType.WEBP);
        }
        return Optional.empty();
    }
}
