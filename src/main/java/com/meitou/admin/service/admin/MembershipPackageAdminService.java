package com.meitou.admin.service.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meitou.admin.entity.MembershipPackage;
import com.meitou.admin.mapper.MembershipPackageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 会员套餐配置服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MembershipPackageAdminService {

    private final MembershipPackageMapper membershipPackageMapper;

    /**
     * 根据站点ID获取会员套餐列表
     * 
     * @param siteId 站点ID
     * @return 套餐列表
     */
    public List<MembershipPackage> getPackagesBySiteId(Long siteId) {
        LambdaQueryWrapper<MembershipPackage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MembershipPackage::getSiteId, siteId);
        wrapper.eq(MembershipPackage::getDeleted, 0);
        // 按 sort_order 升序排序
        wrapper.orderByAsc(MembershipPackage::getSortOrder);
        return membershipPackageMapper.selectList(wrapper);
    }

    /**
     * 根据ID获取会员套餐
     * 
     * @param id 套餐ID
     * @return 套餐信息
     */
    public MembershipPackage getPackageById(Integer id) {
        return membershipPackageMapper.selectById(id);
    }

    /**
     * 创建会员套餐
     * 
     * @param pkg 套餐信息
     * @return 创建的套餐
     */
    public MembershipPackage createPackage(MembershipPackage pkg) {
        membershipPackageMapper.insert(pkg);
        return pkg;
    }

    /**
     * 更新会员套餐
     * 
     * @param id 套餐ID
     * @param pkg 套餐信息
     * @return 更新后的套餐
     */
    public MembershipPackage updatePackage(Integer id, MembershipPackage pkg) {
        MembershipPackage existing = getPackageById(id);
        if (existing == null) {
            throw new RuntimeException("套餐不存在");
        }
        
        // 更新字段
        if (pkg.getName() != null) existing.setName(pkg.getName());
        if (pkg.getLevelCode() != null) existing.setLevelCode(pkg.getLevelCode());
        if (pkg.getSortOrder() != null) existing.setSortOrder(pkg.getSortOrder());
        if (pkg.getIsActive() != null) existing.setIsActive(pkg.getIsActive());
        if (pkg.getIsRecommended() != null) existing.setIsRecommended(pkg.getIsRecommended());
        if (pkg.getBadgeText() != null) existing.setBadgeText(pkg.getBadgeText());
        
        if (pkg.getMonthlyPrice() != null) existing.setMonthlyPrice(pkg.getMonthlyPrice());
        if (pkg.getMonthlyDiscountPrice() != null) existing.setMonthlyDiscountPrice(pkg.getMonthlyDiscountPrice());
        if (pkg.getYearlyPrice() != null) existing.setYearlyPrice(pkg.getYearlyPrice());
        if (pkg.getYearlyDiscountPrice() != null) existing.setYearlyDiscountPrice(pkg.getYearlyDiscountPrice());
        
        if (pkg.getPointsReward() != null) existing.setPointsReward(pkg.getPointsReward());
        
        if (pkg.getButtonText() != null) existing.setButtonText(pkg.getButtonText());
        if (pkg.getButtonType() != null) existing.setButtonType(pkg.getButtonType());
        if (pkg.getPrimaryColor() != null) existing.setPrimaryColor(pkg.getPrimaryColor());
        if (pkg.getFeaturesJson() != null) existing.setFeaturesJson(pkg.getFeaturesJson());
        
        // site_id 一般不更新，除非特殊需求
        
        membershipPackageMapper.updateById(existing);
        return existing;
    }

    /**
     * 删除会员套餐
     * 
     * @param id 套餐ID
     */
    public void deletePackage(Integer id) {
        membershipPackageMapper.deleteById(id);
    }
}
