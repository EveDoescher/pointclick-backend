package com.pim.ecommerce.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    public String storeProductImage(MultipartFile file) {
        validateImage(file);

        try {
            Path productUploadPath = Path.of(uploadDir)
                    .toAbsolutePath()
                    .normalize()
                    .resolve("products");

            Files.createDirectories(productUploadPath);

            String originalFilename = file.getOriginalFilename();
            String extension = extractExtension(originalFilename);
            String safeBaseName = sanitizeBaseName(originalFilename);
            String filename = UUID.randomUUID() + "-" + safeBaseName + "." + extension;

            Path targetPath = productUploadPath.resolve(filename).normalize();

            if (!targetPath.startsWith(productUploadPath)) {
                throw new IllegalArgumentException("Nome de arquivo inválido");
            }

            file.transferTo(targetPath);

            return "/uploads/products/" + filename;
        } catch (IOException ex) {
            throw new IllegalArgumentException("Não foi possível salvar a imagem do produto");
        }
    }

    public String storeReviewImage(MultipartFile file) {
        validateImage(file);

        try {
            Path reviewUploadPath = Path.of(uploadDir)
                    .toAbsolutePath()
                    .normalize()
                    .resolve("reviews");

            Files.createDirectories(reviewUploadPath);

            String originalFilename = file.getOriginalFilename();
            String extension = extractExtension(originalFilename);
            String safeBaseName = sanitizeBaseName(originalFilename);
            String filename = UUID.randomUUID() + "-" + safeBaseName + "." + extension;

            Path targetPath = reviewUploadPath.resolve(filename).normalize();

            if (!targetPath.startsWith(reviewUploadPath)) {
                throw new IllegalArgumentException("Nome de arquivo inválido");
            }

            file.transferTo(targetPath);

            return "/uploads/reviews/" + filename;
        } catch (IOException ex) {
            throw new IllegalArgumentException("Não foi possível salvar a imagem da avaliação");
        }
    }

    public void deletePublicFile(String publicUrl) {
        if (publicUrl == null || publicUrl.isBlank()) {
            return;
        }

        if (!publicUrl.startsWith("/uploads/products/")) {
            return;
        }

        String filename = publicUrl.substring("/uploads/products/".length());

        if (filename.isBlank() || filename.contains("/") || filename.contains("\\")) {
            return;
        }

        try {
            Path productUploadPath = Path.of(uploadDir)
                    .toAbsolutePath()
                    .normalize()
                    .resolve("products");

            Path filePath = productUploadPath.resolve(filename).normalize();

            if (filePath.startsWith(productUploadPath)) {
                Files.deleteIfExists(filePath);
            }
        } catch (IOException ignored) {
            // Falha ao apagar arquivo antigo não deve quebrar operação principal.
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

    private String sanitizeBaseName(String filename) {
        if (filename == null || filename.isBlank()) {
            return "produto";
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
            return "produto";
        }

        return sanitized;
    }
}