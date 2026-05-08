package com.pim.ecommerce.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileStorageServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldStoreProductImageWithSanitizedName() throws Exception {
        FileStorageService service = service();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "Teclado Mecânico Premium.png",
                "image/png",
                new byte[]{1, 2, 3}
        );

        String publicUrl = service.storeProductImage(file);

        assertThat(publicUrl).startsWith("/uploads/products/");
        assertThat(publicUrl).endsWith("-teclado-mecanico-premium.png");
        String filename = publicUrl.substring("/uploads/products/".length());
        assertThat(Files.exists(tempDir.resolve("products").resolve(filename))).isTrue();
    }

    @Test
    void shouldStoreReviewImage() throws Exception {
        FileStorageService service = service();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "avaliacao.webp",
                "image/webp",
                new byte[]{1, 2, 3}
        );

        String publicUrl = service.storeReviewImage(file);

        assertThat(publicUrl).startsWith("/uploads/reviews/");
        String filename = publicUrl.substring("/uploads/reviews/".length());
        assertThat(Files.exists(tempDir.resolve("reviews").resolve(filename))).isTrue();
    }

    @Test
    void shouldRejectUnsupportedContentType() {
        FileStorageService service = service();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "arquivo.txt",
                "text/plain",
                new byte[]{1, 2, 3}
        );

        assertThatThrownBy(() -> service.storeProductImage(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Formato de imagem não permitido. Use JPG, PNG, WEBP ou GIF");
    }

    @Test
    void shouldRejectFileWithoutExtension() {
        FileStorageService service = service();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "arquivo",
                "image/png",
                new byte[]{1, 2, 3}
        );

        assertThatThrownBy(() -> service.storeProductImage(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Arquivo sem extensão válida");
    }

    @Test
    void shouldDeleteProductPublicFile() throws Exception {
        FileStorageService service = service();
        Path productsDir = tempDir.resolve("products");
        Files.createDirectories(productsDir);
        Path file = productsDir.resolve("imagem.png");
        Files.write(file, new byte[]{1, 2, 3});

        service.deletePublicFile("/uploads/products/imagem.png");

        assertThat(Files.exists(file)).isFalse();
    }

    @Test
    void shouldIgnoreDeleteForNonProductUpload() throws Exception {
        FileStorageService service = service();
        Path reviewsDir = tempDir.resolve("reviews");
        Files.createDirectories(reviewsDir);
        Path file = reviewsDir.resolve("imagem.png");
        Files.write(file, new byte[]{1, 2, 3});

        service.deletePublicFile("/uploads/reviews/imagem.png");

        assertThat(Files.exists(file)).isTrue();
    }


    @Test
    void shouldStoreImageWithUppercaseExtensionAndContentType() throws Exception {
        FileStorageService service = service();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "NOTEBOOK GAMER.JPG",
                "IMAGE/JPEG",
                new byte[]{1, 2, 3}
        );

        String publicUrl = service.storeProductImage(file);

        assertThat(publicUrl).startsWith("/uploads/products/");
        assertThat(publicUrl).endsWith("-notebook-gamer.jpg");
    }

    @Test
    void shouldUseDefaultBaseNameWhenSanitizedNameIsBlank() {
        FileStorageService service = service();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "!!!.png",
                "image/png",
                new byte[]{1, 2, 3}
        );

        String publicUrl = service.storeProductImage(file);

        assertThat(publicUrl).contains("-produto.png");
    }

    @Test
    void shouldRejectNullImage() {
        FileStorageService service = service();

        assertThatThrownBy(() -> service.storeProductImage(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Imagem é obrigatória");
    }

    @Test
    void shouldRejectEmptyImage() {
        FileStorageService service = service();
        MockMultipartFile file = new MockMultipartFile("file", "imagem.png", "image/png", new byte[]{});

        assertThatThrownBy(() -> service.storeProductImage(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Imagem é obrigatória");
    }

    @Test
    void shouldRejectImageLargerThanFiveMb() {
        FileStorageService service = service();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "imagem.png",
                "image/png",
                new byte[(5 * 1024 * 1024) + 1]
        );

        assertThatThrownBy(() -> service.storeProductImage(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Imagem deve ter no máximo 5MB");
    }

    @Test
    void shouldRejectNullContentType() {
        FileStorageService service = service();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "imagem.png",
                null,
                new byte[]{1, 2, 3}
        );

        assertThatThrownBy(() -> service.storeProductImage(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Formato de imagem não permitido. Use JPG, PNG, WEBP ou GIF");
    }

    @Test
    void shouldRejectUnsupportedExtensionEvenWhenContentTypeIsAllowed() {
        FileStorageService service = service();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "imagem.bmp",
                "image/png",
                new byte[]{1, 2, 3}
        );

        assertThatThrownBy(() -> service.storeProductImage(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Extensão de imagem não permitida. Use JPG, PNG, WEBP ou GIF");
    }

    @Test
    void shouldRejectBlankExtension() {
        FileStorageService service = service();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "imagem.",
                "image/png",
                new byte[]{1, 2, 3}
        );

        assertThatThrownBy(() -> service.storeProductImage(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Arquivo sem extensão válida");
    }

    @Test
    void shouldIgnoreDeleteWhenPublicUrlIsNullBlankOrUnsafe() throws Exception {
        FileStorageService service = service();
        Path productsDir = tempDir.resolve("products");
        Files.createDirectories(productsDir);
        Path file = productsDir.resolve("imagem.png");
        Files.write(file, new byte[]{1, 2, 3});

        service.deletePublicFile(null);
        service.deletePublicFile("   ");
        service.deletePublicFile("/uploads/products/");
        service.deletePublicFile("/uploads/products/sub/imagem.png");
        service.deletePublicFile("/uploads/products/..\\imagem.png");

        assertThat(Files.exists(file)).isTrue();
    }

    @Test
    void shouldTranslateProductImageStorageIOExceptionToFriendlyMessage() {
        FileStorageService service = service();
        org.springframework.web.multipart.MultipartFile file = throwingMultipartFile("imagem.png", "image/png");

        assertThatThrownBy(() -> service.storeProductImage(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Não foi possível salvar a imagem do produto");
    }

    @Test
    void shouldTranslateReviewImageStorageIOExceptionToFriendlyMessage() {
        FileStorageService service = service();
        org.springframework.web.multipart.MultipartFile file = throwingMultipartFile("imagem.png", "image/png");

        assertThatThrownBy(() -> service.storeReviewImage(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Não foi possível salvar a imagem da avaliação");
    }

    private org.springframework.web.multipart.MultipartFile throwingMultipartFile(String originalFilename, String contentType) {
        return new org.springframework.web.multipart.MultipartFile() {
            @Override
            public String getName() {
                return "file";
            }

            @Override
            public String getOriginalFilename() {
                return originalFilename;
            }

            @Override
            public String getContentType() {
                return contentType;
            }

            @Override
            public boolean isEmpty() {
                return false;
            }

            @Override
            public long getSize() {
                return 3;
            }

            @Override
            public byte[] getBytes() {
                return new byte[]{1, 2, 3};
            }

            @Override
            public java.io.InputStream getInputStream() {
                return new java.io.ByteArrayInputStream(new byte[]{1, 2, 3});
            }

            @Override
            public void transferTo(java.io.File dest) throws java.io.IOException {
                throw new java.io.IOException("falha simulada");
            }

            @Override
            public void transferTo(Path dest) throws java.io.IOException {
                throw new java.io.IOException("falha simulada");
            }
        };
    }

    private FileStorageService service() {
        FileStorageService service = new FileStorageService();
        ReflectionTestUtils.setField(service, "uploadDir", tempDir.toString());
        return service;
    }
}
