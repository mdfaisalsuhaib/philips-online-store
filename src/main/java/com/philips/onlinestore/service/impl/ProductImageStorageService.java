package com.philips.onlinestore.service.impl;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Iterator;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class ProductImageStorageService {

    private static final long MAX_IMAGE_SIZE_BYTES = 5 * 1024 * 1024;
    private static final long MAX_IMAGE_PIXELS = 20_000_000L;
    private static final byte[] PNG_SIGNATURE = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };

    private final Path storageDirectory;

    public ProductImageStorageService(
            @Value("${app.product-image.storage-directory:${user.home}/philips-online-store/product-images}")
            String storageDirectory
    ) {
        try {
            this.storageDirectory = Path.of(storageDirectory).toAbsolutePath().normalize();
            Files.createDirectories(this.storageDirectory);
        } catch (IOException | InvalidPathException exception) {
            throw new StorageException("Could not initialize product image storage.", exception);
        }
    }

    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidImageException("An image file is required.");
        }
        if (file.getSize() > MAX_IMAGE_SIZE_BYTES) {
            throw new InvalidImageException("Image files must not exceed 5 MB.");
        }

        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException exception) {
            throw new StorageException("Could not read the uploaded image.", exception);
        }
        if (content.length == 0 || content.length > MAX_IMAGE_SIZE_BYTES) {
            throw new InvalidImageException("Image files must be non-empty and must not exceed 5 MB.");
        }

        ImageType imageType = detectImageType(content);
        if (imageType == null || !imageType.matchesContentType(file.getContentType())) {
            throw new InvalidImageException("Only valid JPG, JPEG, PNG, and WEBP images are allowed.");
        }
        validateImageContent(content);

        String filename = UUID.randomUUID() + imageType.extension();
        Path destination = resolveStoredImage(filename);
        try {
            Files.write(destination, content, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            return filename;
        } catch (IOException exception) {
            throw new StorageException("Could not store the uploaded image.", exception);
        }
    }

    public StoredImage load(String filename) {
        Path imagePath = resolveStoredImage(filename);
        if (!Files.isRegularFile(imagePath, LinkOption.NOFOLLOW_LINKS)) {
            throw new ImageNotFoundException("Product image was not found.");
        }

        try {
            return new StoredImage(new UrlResource(imagePath.toUri()), mediaTypeFor(filename));
        } catch (IOException exception) {
            throw new StorageException("Could not access the stored image.", exception);
        }
    }

    public void delete(String filename) {
        if (filename == null || filename.isBlank()) {
            return;
        }

        Path imagePath = resolveStoredImage(filename);
        try {
            Files.deleteIfExists(imagePath);
        } catch (IOException exception) {
            throw new StorageException("Could not delete the stored image.", exception);
        }
    }

    private Path resolveStoredImage(String filename) {
        if (filename == null || !filename.matches(
                "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|png|webp)"
        )) {
            throw new InvalidImageException("Invalid stored image name.");
        }

        Path imagePath = storageDirectory.resolve(filename).normalize();
        if (!imagePath.startsWith(storageDirectory)) {
            throw new InvalidImageException("Invalid stored image path.");
        }
        return imagePath;
    }

    private ImageType detectImageType(byte[] content) {
        if (content.length >= 3
                && (content[0] & 0xFF) == 0xFF
                && (content[1] & 0xFF) == 0xD8
                && (content[2] & 0xFF) == 0xFF) {
            return ImageType.JPEG;
        }
        if (content.length >= PNG_SIGNATURE.length
                && startsWith(content, PNG_SIGNATURE)) {
            return ImageType.PNG;
        }
        if (content.length >= 12
                && asciiEquals(content, 0, "RIFF")
                && asciiEquals(content, 8, "WEBP")) {
            return ImageType.WEBP;
        }
        return null;
    }

    private void validateImageContent(byte[] content) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(content))) {
            if (input == null) {
                throw new InvalidImageException("The uploaded file is not a readable image.");
            }

            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new InvalidImageException("The uploaded file is not a supported image.");
            }

            ImageReader reader = readers.next();
            AtomicReference<String> decodeWarning = new AtomicReference<>();
            try {
                reader.addIIOReadWarningListener(
                        (source, warning) -> decodeWarning.compareAndSet(null, warning)
                );
                reader.setInput(input, true, true);

                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || (long) width * height > MAX_IMAGE_PIXELS) {
                    throw new InvalidImageException("The image dimensions are invalid or too large.");
                }

                BufferedImage decodedImage = reader.read(0);
                if (decodedImage == null || decodeWarning.get() != null) {
                    throw new InvalidImageException("The uploaded image is corrupted or incomplete.");
                }
            } finally {
                reader.dispose();
            }
        } catch (InvalidImageException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new InvalidImageException("The uploaded image is corrupted or incomplete.");
        }
    }

    private boolean startsWith(byte[] content, byte[] signature) {
        for (int index = 0; index < signature.length; index++) {
            if (content[index] != signature[index]) {
                return false;
            }
        }
        return true;
    }

    private boolean asciiEquals(byte[] content, int offset, String expected) {
        for (int index = 0; index < expected.length(); index++) {
            if (content[offset + index] != expected.charAt(index)) {
                return false;
            }
        }
        return true;
    }

    private MediaType mediaTypeFor(String filename) {
        if (filename.endsWith(".jpg")) {
            return MediaType.IMAGE_JPEG;
        }
        if (filename.endsWith(".png")) {
            return MediaType.IMAGE_PNG;
        }
        return MediaType.parseMediaType("image/webp");
    }

    private record ImageType(String extension, MediaType mediaType) {

        private static final ImageType JPEG = new ImageType(".jpg", MediaType.IMAGE_JPEG);
        private static final ImageType PNG = new ImageType(".png", MediaType.IMAGE_PNG);
        private static final ImageType WEBP = new ImageType(".webp", MediaType.parseMediaType("image/webp"));

        private boolean matchesContentType(String contentType) {
            if (contentType == null || contentType.isBlank()) {
                return true;
            }
            try {
                MediaType uploadedMediaType = MediaType.parseMediaType(contentType);
                return mediaType.getType().equalsIgnoreCase(uploadedMediaType.getType())
                        && mediaType.getSubtype().equalsIgnoreCase(uploadedMediaType.getSubtype());
            } catch (InvalidMediaTypeException exception) {
                return false;
            }
        }
    }

    public record StoredImage(Resource resource, MediaType mediaType) {
    }

    public static class InvalidImageException extends RuntimeException {

        public InvalidImageException(String message) {
            super(message);
        }
    }

    public static class ImageNotFoundException extends RuntimeException {

        public ImageNotFoundException(String message) {
            super(message);
        }
    }

    public static class StorageException extends RuntimeException {

        public StorageException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
