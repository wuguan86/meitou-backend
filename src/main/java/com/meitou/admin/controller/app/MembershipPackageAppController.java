package com.meitou.admin.controller.app;

import com.meitou.admin.common.Result;
import com.meitou.admin.common.SiteContext;
import com.meitou.admin.entity.MembershipPackage;
import com.meitou.admin.service.app.MembershipPackageAppService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 会员套餐 App 控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/app/membership-packages")
@RequiredArgsConstructor
public class MembershipPackageAppController {

    private final MembershipPackageAppService membershipPackageService;

    /**
     * 获取当前站点的会员套餐列表
     *
     * @return 套餐列表
     */
    @GetMapping
    public Result<List<MembershipPackage>> getPackages() {
        Long siteId = SiteContext.getSiteId();
        if (siteId == null) {
            return Result.error("无法识别当前站点");
        }
        
        List<MembershipPackage> packages = membershipPackageService.getActivePackages(siteId);
        return Result.success("查询成功", packages);
    }
}
