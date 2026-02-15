package com.meitou.admin.service.app;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meitou.admin.entity.MembershipPackage;
import com.meitou.admin.mapper.MembershipPackageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * 会员套餐 App 服务类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MembershipPackageAppService {

    private final MembershipPackageMapper membershipPackageMapper;

    /**
     * 获取指定站点的上架套餐列表
     *
     * @param siteId 站点ID
     * @return 套餐列表
     */
    public List<MembershipPackage> getActivePackages(Long siteId) {
        if (siteId == null) {
            return Collections.emptyList();
        }
        
        LambdaQueryWrapper<MembershipPackage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MembershipPackage::getSiteId, siteId)
               .eq(MembershipPackage::getIsActive, true)
               .orderByAsc(MembershipPackage::getSortOrder);
               
        return membershipPackageMapper.selectList(wrapper);
    }
}
