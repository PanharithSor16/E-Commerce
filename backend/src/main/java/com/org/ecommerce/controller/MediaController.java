package com.org.ecommerce.controller;

import java.nio.file.Path;
import java.time.Duration;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.org.ecommerce.exception.ApiException;
import com.org.ecommerce.service.ImageStorageService;

import io.swagger.v3.oas.annotations.Hidden;

/** Serves uploaded product images. Keys are validated against a strict pattern (no traversal). */
@Hidden
@RestController
@RequestMapping("/api/v1/media")
public class MediaController {

    private final ImageStorageService storage;

    public MediaController(ImageStorageService storage) {
        this.storage = storage;
    }

    @GetMapping("/products/{file}")
    public ResponseEntity<Resource> productImage(@PathVariable String file) {
        String key = "products/" + file;
        Path path = storage.load(key).orElseThrow(() -> ApiException.notFound("Image"));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(ImageStorageService.contentTypeFor(key)))
                // File names are random and immutable, so they can be cached aggressively.
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                .header("X-Content-Type-Options", "nosniff")
                .body(new FileSystemResource(path));
    }
}
