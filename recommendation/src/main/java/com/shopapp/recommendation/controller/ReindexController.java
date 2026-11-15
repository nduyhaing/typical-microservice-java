package com.shopapp.recommendation.controller;

import com.shopapp.commonlibrary.utils.AuthenticationUtils;
import com.shopapp.recommendation.service.ReindexService;
import com.shopapp.recommendation.viewmodel.ReindexResultVm;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Back office: re-embed every product from product (ADMIN, SecurityConfig). Not in YAS; like search's reindex
 * (decisions.md 4-16) it repairs after dead-lettered events or once the topic no longer holds the snapshot.
 */
@RestController
@RequiredArgsConstructor
public class ReindexController {

    private final ReindexService reindexService;

    @PostMapping("/backoffice/reindex")
    public ResponseEntity<ReindexResultVm> reindex() {
        return ResponseEntity.ok(reindexService.reindex(AuthenticationUtils.extractJwt()));
    }
}
