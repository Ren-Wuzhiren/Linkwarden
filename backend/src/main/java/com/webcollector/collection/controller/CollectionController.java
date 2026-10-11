package com.webcollector.collection.controller;

import com.webcollector.collection.dto.CollectionSummary;
import com.webcollector.collection.dto.CreateCollectionRequest;
import com.webcollector.collection.dto.UpdateCollectionRequest;
import com.webcollector.collection.service.CollectionService;
import com.webcollector.common.web.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/collections")
public class CollectionController {
    private final CollectionService collectionService;

    public CollectionController(
            CollectionService collectionService
    ) {
        this.collectionService = collectionService;
    }

    @GetMapping
    public ApiResponse<List<CollectionSummary>> listCollections() {
        return ApiResponse.success(
                collectionService.listCollections()
        );
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CollectionSummary>> createCollection(
            @Valid @RequestBody CreateCollectionRequest request
            ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        collectionService.createCollection(request),
                        "集合创建成功"
                ));
    }

    @PatchMapping("/{id}")
    public ApiResponse<CollectionSummary> updateCollection(
            @PathVariable Long id,
            @Valid @RequestBody
            UpdateCollectionRequest request
    ) {
        return ApiResponse.success(
          collectionService.updateCollection(id, request),
          "集合更新成功"
        );
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteCollection(
            @PathVariable Long id
    ) {
        collectionService.deleteCollection(id);
        return ApiResponse.success(null, "集合删除成功");
    }
}
