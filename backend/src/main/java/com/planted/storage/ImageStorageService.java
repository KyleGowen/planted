package com.planted.storage;

import com.planted.entity.PlantImage;
import org.springframework.web.multipart.MultipartFile;

/**
 * Abstraction over image storage backends.
 * Local profile uses LocalImageStorageService; prod uses S3ImageStorageService.
 */
public interface ImageStorageService {

    /**
     * Store an uploaded file and return its storage path.
     *
     * @param file    the uploaded file
     * @param plantId the owning plant's ID, used for path namespacing
     * @return the storage path (relative path for local, S3 key for S3)
     */
    String store(MultipartFile file, Long plantId);

    /**
     * Return a publicly accessible or resolvable URL for the given storage path.
     */
    String getUrl(String storagePath, PlantImage.StorageType storageType);

    /**
     * Store raw bytes (e.g. generated images from OpenAI) and return the storage path.
     */
    String storeBytes(byte[] data, String filename, String mimeType, Long plantId);

    /**
     * The storage type this implementation represents.
     */
    PlantImage.StorageType getStorageType();
}
