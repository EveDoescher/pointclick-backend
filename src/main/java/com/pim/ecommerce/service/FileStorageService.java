package com.pim.ecommerce.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FileStorageService {

    private static final long MAX_FILE_SIZE_BYTES = 5L * 1024L * 1024L;

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp",
            "image/gif"
    );

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "jpg",
            "jpeg",
            "png",
            "webp",
            "gif"
    );

    @Value("${supabase.url}")
    private String supabaseUrl;

    @Value("${supabase.service-role-key}")
    private String serviceRoleKey;

    @Value("${supabase.storage.bucket:product-images}")
    private String bucket;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    public String storeProductImage(MultipartFile file) {
        return uploadImage(file, "products", "produto");
    }

    public String storeReviewImage(MultipartFile file) {
        return uploadImage(file, "reviews", "avaliacao");
    }

    public void deletePublicFile(String publicUrl) {
        if (publicUrl == null || publicUrl.isBlank()) {
            return;
        }

        String publicPrefix = buildPublicPrefix();

        if (!publicUrl.startsWith(publicPrefix)) {
            return;
        }

        String objectPath = publicUrl.substring(publicPrefix.length());

        if (objectPath.isBlank() || objectPath.contains("..")) {
            return;
        }

        try {
            String encodedPath = encodePath(objectPath);

            URI deleteUri = URI.create(
                    normalizeSupabaseUrl() + "/storage/v1/object/" + bucket + "/" + encodedPath
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(deleteUri)
                    .header("Authorization", "Bearer " + serviceRoleKey)
                    .header("apikey", serviceRoleKey)
                    .DELETE()
                    .build();

            httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception ignored) {
            // Falha ao apagar arquivo antigo não deve quebrar operação principal.
        }
    }

    private String uploadImage(MultipartFile file, String folder, String fallbackBaseName) {
        validateImage(file);
        validateSupabaseConfig();

        try {
            String originalFilename = file.getOriginalFilename();
            String extension = extractExtension(originalFilename);
            String safeBaseName = sanitizeBaseName(originalFilename, fallbackBaseName);

            String filename = UUID.randomUUID() + "-" + safeBaseName + "." + extension;
            String objectPath = folder + "/" + filename;
            String encodedPath = encodePath(objectPath);

            URI uploadUri = URI.create(
                    normalizeSupabaseUrl() + "/storage/v1/object/" + bucket + "/" + encodedPath
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uploadUri)
                    .header("Authorization", "Bearer " + serviceRoleKey)
                    .header("apikey", serviceRoleKey)
                    .header("Content-Type", resolveContentType(file))
                    .header("x-upsert", "false")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(file.getBytes()))
                    .build();

            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalArgumentException(
                        "Não foi possível enviar a imagem para o Supabase Storage"
                );
            }

            return buildPublicPrefix() + objectPath;
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Não foi possível salvar a imagem");
        }
    }

    private void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Imagem é obrigatória");
        }

        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new IllegalArgumentException("Imagem deve ter no máximo 5MB");
        }

        String contentType = file.getContentType();

        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Formato de imagem não permitido. Use JPG, PNG, WEBP ou GIF");
        }

        String extension = extractExtension(file.getOriginalFilename());

        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("Extensão de imagem não permitida. Use JPG, PNG, WEBP ou GIF");
        }
    }

    private void validateSupabaseConfig() {
        if (supabaseUrl == null || supabaseUrl.isBlank()) {
            throw new IllegalArgumentException("SUPABASE_URL não configurada");
        }

        if (serviceRoleKey == null || serviceRoleKey.isBlank()) {
            throw new IllegalArgumentException("SUPABASE_SERVICE_ROLE_KEY não configurada");
        }

        if (bucket == null || bucket.isBlank()) {
            throw new IllegalArgumentException("SUPABASE_STORAGE_BUCKET não configurado");
        }
    }

    private String extractExtension(String filename) {
        if (filename == null || filename.isBlank() || !filename.contains(".")) {
            throw new IllegalArgumentException("Arquivo sem extensão válida");
        }

        String extension = filename.substring(filename.lastIndexOf(".") + 1)
                .trim()
                .toLowerCase(Locale.ROOT);

        if (extension.isBlank()) {
            throw new IllegalArgumentException("Arquivo sem extensão válida");
        }

        return extension;
    }

    private String sanitizeBaseName(String filename, String fallback) {
        if (filename == null || filename.isBlank()) {
            return fallback;
        }

        String baseName = filename;

        if (baseName.contains(".")) {
            baseName = baseName.substring(0, baseName.lastIndexOf("."));
        }

        String normalized = Normalizer.normalize(baseName, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");

        String sanitized = normalized
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9-_]+", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");

        if (sanitized.isBlank()) {
            return fallback;
        }

        return sanitized;
    }

    private String resolveContentType(MultipartFile file) {
        String contentType = file.getContentType();

        if (contentType == null || contentType.isBlank()) {
            return "application/octet-stream";
        }

        return contentType;
    }

    private String normalizeSupabaseUrl() {
        return supabaseUrl.replaceAll("/+$", "");
    }

    private String buildPublicPrefix() {
        return normalizeSupabaseUrl() + "/storage/v1/object/public/" + bucket + "/";
    }

    private String encodePath(String path) {
        String[] parts = path.split("/");
        StringBuilder encoded = new StringBuilder();

        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                encoded.append("/");
            }

            encoded.append(URLEncoder.encode(parts[i], StandardCharsets.UTF_8));
        }

        return encoded.toString();
    }
}