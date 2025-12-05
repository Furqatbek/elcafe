package com.elcafe.modules.menu.controller;

import com.elcafe.modules.menu.dto.*;
import com.elcafe.modules.menu.enums.LinkType;
import com.elcafe.modules.menu.service.LinkedItemService;
import com.elcafe.utils.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/products/{productId}/linked-items")
@RequiredArgsConstructor
@Tag(name = "Linked Items", description = "Product recommendations and linked items APIs")
public class LinkedItemController {

    private final LinkedItemService linkedItemService;

    @GetMapping
    @Operation(summary = "Get linked items for product (public)")
    public ResponseEntity<ApiResponse<List<LinkedItemDTO>>> getLinkedItems(@PathVariable Long productId) {
        List<LinkedItemDTO> linkedItems = linkedItemService.getLinkedItems(productId);
        return ResponseEntity.ok(ApiResponse.success("Linked items retrieved successfully", linkedItems));
    }

    @GetMapping("/by-type")
    @Operation(summary = "Get linked items by type (public)")
    public ResponseEntity<ApiResponse<List<LinkedItemDTO>>> getLinkedItemsByType(
            @PathVariable Long productId,
            @RequestParam LinkType linkType
    ) {
        List<LinkedItemDTO> linkedItems = linkedItemService.getLinkedItemsByType(productId, linkType);
        return ResponseEntity.ok(ApiResponse.success("Linked items retrieved successfully", linkedItems));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Add linked item")
    public ResponseEntity<ApiResponse<LinkedItemDTO>> addLinkedItem(
            @PathVariable Long productId,
            @Valid @RequestBody AddLinkedItemRequest request
    ) {
        LinkedItemDTO linkedItem = linkedItemService.addLinkedItem(productId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Linked item added successfully", linkedItem));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete linked item")
    public ResponseEntity<ApiResponse<Void>> deleteLinkedItem(
            @PathVariable Long productId,
            @PathVariable Long id
    ) {
        linkedItemService.deleteLinkedItem(id);
        return ResponseEntity.ok(ApiResponse.success("Linked item deleted successfully", null));
    }
}
