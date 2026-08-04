package com.flashflow.trade.campaign;

import java.util.List;

import com.flashflow.trade.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/api/v1")
public class CampaignController {

    private final CampaignService campaignService;

    public CampaignController(CampaignService campaignService) {
        this.campaignService = campaignService;
    }

    @GetMapping("/campaigns")
    ApiResponse<List<CampaignDtos.CampaignView>> listCampaigns(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId
    ) {
        return ApiResponse.success(campaignService.listAvailableCampaigns(keyword, categoryId));
    }

    @GetMapping("/admin/campaigns")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    ApiResponse<List<CampaignDtos.CampaignView>> listAdminCampaigns() {
        return ApiResponse.success(campaignService.listAllCampaigns());
    }

    @PostMapping("/admin/campaigns")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    ApiResponse<CampaignDtos.CampaignView> createCampaign(
            @Valid @RequestBody CampaignDtos.CreateCampaignRequest request
    ) {
        return ApiResponse.success(campaignService.createCampaign(request));
    }

    @PatchMapping("/admin/campaigns/{campaignId}/status")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    ApiResponse<CampaignDtos.CampaignView> changeStatus(
            @PathVariable long campaignId,
            @Valid @RequestBody CampaignDtos.ChangeCampaignStatusRequest request
    ) {
        return ApiResponse.success(campaignService.changeStatus(campaignId, request));
    }

    @PostMapping("/admin/campaigns/{campaignId}/warmup")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    ApiResponse<Void> warmup(@PathVariable long campaignId) {
        campaignService.forceWarmup(campaignId);
        return ApiResponse.success(null);
    }
}
