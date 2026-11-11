package com.hbsisms.feature.sales;

import com.hbsisms.common.web.PageRequests;
import com.hbsisms.common.web.PageResponse;
import com.hbsisms.feature.sales.dto.QuoteConvertResponse;
import com.hbsisms.feature.sales.dto.QuoteDetailResponse;
import com.hbsisms.feature.sales.dto.QuoteInputRequest;
import com.hbsisms.feature.sales.dto.QuoteListItemResponse;
import com.hbsisms.feature.sales.dto.QuoteStatusRequest;
import com.hbsisms.security.AuthUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/quotes")
@PreAuthorize("hasAuthority('orders')")
public class QuoteController {

    private final QuoteService quoteService;

    public QuoteController(QuoteService quoteService) {
        this.quoteService = quoteService;
    }

    @GetMapping
    public PageResponse<QuoteListItemResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "" + PageRequests.DEFAULT_PAGE_SIZE) int pageSize) {
        return quoteService.search(q, status, page, pageSize);
    }

    @GetMapping("/{id}")
    public QuoteDetailResponse get(@PathVariable Long id) {
        return quoteService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public QuoteDetailResponse create(
            @Valid @RequestBody QuoteInputRequest request, @AuthenticationPrincipal AuthUser actor) {
        return quoteService.create(request, actor == null ? null : actor.id());
    }

    @PatchMapping("/{id}/status")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setStatus(
            @PathVariable Long id,
            @Valid @RequestBody QuoteStatusRequest request,
            @AuthenticationPrincipal AuthUser actor) {
        quoteService.setStatus(id, request, actor == null ? null : actor.id());
    }

    @PostMapping("/{id}/convert")
    public QuoteConvertResponse convert(@PathVariable Long id, @AuthenticationPrincipal AuthUser actor) {
        return quoteService.convert(id, actor == null ? null : actor.id());
    }
}
