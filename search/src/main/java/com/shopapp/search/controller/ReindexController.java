package com.shopapp.search.controller;

import com.shopapp.commonlibrary.utils.AuthenticationUtils;
import com.shopapp.search.service.ReindexService;
import com.shopapp.search.viewmodel.ReindexResultVm;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** Back office: rebuild the index from product (ADMIN, SecurityConfig). Synchronous; the shop has few products. */
@RestController
@RequiredArgsConstructor
public class ReindexController {

    private final ReindexService reindexService;

    @PostMapping("/backoffice/reindex")
    public ResponseEntity<ReindexResultVm> reindex() {
        return ResponseEntity.ok(reindexService.reindex(AuthenticationUtils.extractJwt()));
    }
}
