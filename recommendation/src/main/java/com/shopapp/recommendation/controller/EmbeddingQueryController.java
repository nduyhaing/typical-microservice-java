package com.shopapp.recommendation.controller;

import com.shopapp.recommendation.service.SimilarProductService;
import com.shopapp.recommendation.viewmodel.RelatedProductVm;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** YAS EmbeddingQueryController: similar products for the storefront product page (public). */
@RestController
@RequestMapping("/embedding")
@RequiredArgsConstructor
public class EmbeddingQueryController {

    private final SimilarProductService similarProductService;

    @GetMapping("/product/{id}/similarity")
    public List<RelatedProductVm> searchProduct(@PathVariable("id") long productId) {
        return similarProductService.similarTo(productId);
    }
}
