package com.planted.storage;

import com.planted.entity.PlantImage;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LocalImageStorageServiceGetUrlTest {

    @Test
    void getUrl_usesRootRelativePathWhenPublicBaseUnset() {
        LocalImageStorageService svc = new LocalImageStorageService();
        ReflectionTestUtils.setField(svc, "basePath", "./data/images");
        ReflectionTestUtils.setField(svc, "publicBaseUrl", "");
        assertEquals(
                "/images/plants/1/a.jpg",
                svc.getUrl("plants/1/a.jpg", PlantImage.StorageType.LOCAL));
    }

    @Test
    void getUrl_stripsTrailingSlashOnPublicBase() {
        LocalImageStorageService svc = new LocalImageStorageService();
        ReflectionTestUtils.setField(svc, "basePath", "./data/images");
        ReflectionTestUtils.setField(svc, "publicBaseUrl", "http://192.168.1.10:8080/");
        assertEquals(
                "http://192.168.1.10:8080/images/plants/1/a.jpg",
                svc.getUrl("plants/1/a.jpg", PlantImage.StorageType.LOCAL));
    }

    @Test
    void getUrl_passesThroughRemoteUrlType() {
        LocalImageStorageService svc = new LocalImageStorageService();
        ReflectionTestUtils.setField(svc, "publicBaseUrl", "http://192.168.1.10:8080");
        assertEquals(
                "https://cdn.example.com/p.png",
                svc.getUrl("https://cdn.example.com/p.png", PlantImage.StorageType.URL));
    }
}
