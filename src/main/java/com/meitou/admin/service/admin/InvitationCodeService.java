package com.meitou.admin.service.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.meitou.admin.entity.InvitationCode;
import com.meitou.admin.mapper.InvitationCodeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Random;

/**
 * 管理端邀请码服务类
 */
@Service
@RequiredArgsConstructor
public class InvitationCodeService extends ServiceImpl<InvitationCodeMapper, InvitationCode> {
    
    private final InvitationCodeMapper codeMapper;
    
    /**
     * 获取邀请码列表（按站点分类）
     * 多租户插件会自动过滤当前站点的数据
     * 
     * @return 邀请码列表
     */
    public List<InvitationCode> getCodes() {
        LambdaQueryWrapper<InvitationCode> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(InvitationCode::getCreatedAt);
        return codeMapper.selectList(wrapper);
    }
    
    /**
     * 获取指定站点的邀请码列表（管理后台使用）
     * 注意：调用此方法前，需要先设置 SiteContext.setSiteId(siteId)，
     * 这样多租户插件会自动添加 site_id 过滤条件
     * 
     * @param siteId 站点ID
     * @return 邀请码列表
     */
    public List<InvitationCode> getCodesBySiteId(Long siteId) {
        // 不在这里添加 siteId 条件，因为多租户插件会自动添加
        // 如果在这里添加，会导致 SQL 中出现重复的 site_id 条件
        LambdaQueryWrapper<InvitationCode> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(InvitationCode::getCreatedAt);
        return codeMapper.selectList(wrapper);
    }
    
    /**
     * 分页获取邀请码列表
     * 
     * @param page 分页对象
     * @param code 邀请码
     * @param channel 渠道
     * @param status 状态
     * @return 分页结果
     */
    public IPage<InvitationCode> getPage(Page<InvitationCode> page, String code, String channel, String status) {
        LambdaQueryWrapper<InvitationCode> wrapper = new LambdaQueryWrapper<>();
        
        if (StringUtils.hasText(code)) {
            wrapper.like(InvitationCode::getCode, code);
        }
        if (StringUtils.hasText(channel)) {
            wrapper.like(InvitationCode::getChannel, channel);
        }
        if (StringUtils.hasText(status) && !"all".equals(status)) {
            wrapper.eq(InvitationCode::getStatus, status);
        }
        
        wrapper.orderByDesc(InvitationCode::getCreatedAt);
        return codeMapper.selectPage(page, wrapper);
    }

    /**
     * 生成邀请码
     * 
     * @param count 生成数量
     * @param points 赠送积分
     * @param maxUses 最大使用次数
     * @param siteId 站点ID
     * @param channel 渠道
     * @param validStartDate 有效期开始
     * @param validEndDate 有效期结束
     * @return 生成的邀请码列表
     */
    public List<InvitationCode> generateCodes(Integer count, Integer points, Integer maxUses,
                                              Long siteId, String channel,
                                              LocalDate validStartDate, LocalDate validEndDate) {
        List<InvitationCode> codes = new java.util.ArrayList<>();
        
        for (int i = 0; i < count; i++) {
            InvitationCode code = new InvitationCode();
            // 生成随机邀请码
            code.setCode("INV" + generateRandomString(6).toUpperCase());
            code.setPoints(points);
            code.setMaxUses(maxUses);
            code.setUsedCount(0);
            code.setStatus("active");
            code.setSiteId(siteId);
            code.setChannel(channel != null ? channel : "默认渠道");
            code.setValidStartDate(validStartDate);
            code.setValidEndDate(validEndDate);
            
            codeMapper.insert(code);
            codes.add(code);
        }
        
        return codes;
    }
    
    /**
     * 生成随机字符串
     * 
     * @param length 长度
     * @return 随机字符串
     */
    private String generateRandomString(int length) {
        String chars = "abcdefghijklmnopqrstuvwxyz0123456789";
        Random random = new Random();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }
    
    /**
     * 更新邀请码
     * 
     * @param id 邀请码ID
     * @param code 邀请码信息
     * @return 更新后的邀请码
     */
    public InvitationCode updateCode(Long id, InvitationCode code) {
        InvitationCode existing = getCodeById(id);
        
        if (code.getStatus() != null) {
            existing.setStatus(code.getStatus());
        }
        if (code.getChannel() != null) {
            existing.setChannel(code.getChannel());
        }
        if (code.getPoints() != null) {
            existing.setPoints(code.getPoints());
        }
        if (code.getMaxUses() != null) {
            existing.setMaxUses(code.getMaxUses());
        }
        if (code.getValidStartDate() != null) {
            existing.setValidStartDate(code.getValidStartDate());
        }
        if (code.getValidEndDate() != null) {
            existing.setValidEndDate(code.getValidEndDate());
        }
        
        codeMapper.updateById(existing);
        return existing;
    }

    /**
     * 删除邀请码
     *
     * @param id 邀请码ID
     */
    public void deleteCode(Long id) {
        codeMapper.deleteById(id);
    }
    
    /**
     * 根据ID获取邀请码
     * 
     * @param id 邀请码ID
     * @return 邀请码
     */
    public InvitationCode getCodeById(Long id) {
        InvitationCode code = codeMapper.selectById(id);
        if (code == null) {
            throw new RuntimeException("邀请码不存在");
        }
        return code;
    }
}

