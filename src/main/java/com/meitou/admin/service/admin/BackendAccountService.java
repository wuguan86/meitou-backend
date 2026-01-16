package com.meitou.admin.service.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.meitou.admin.entity.BackendAccount;
import com.meitou.admin.exception.BusinessException;
import com.meitou.admin.exception.ErrorCode;
import com.meitou.admin.mapper.BackendAccountMapper;
import com.meitou.admin.util.PasswordValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 管理端后台账号服务类
 */
@Service
@RequiredArgsConstructor
public class BackendAccountService extends ServiceImpl<BackendAccountMapper, BackendAccount> {
    
    private final BackendAccountMapper accountMapper; // 账号Mapper
    private final BCryptPasswordEncoder passwordEncoder; // 密码编码器（通过依赖注入）
    
    /**
     * 获取账号列表
     * 
     * @return 账号列表
     */
    public List<BackendAccount> getAccounts() {
        return accountMapper.selectList(null);
    }
    
    /**
     * 创建账号
     * 
     * @param account 账号信息
     * @return 创建的账号
     */
    public BackendAccount createAccount(BackendAccount account) {
        // 检查邮箱是否已存在
        LambdaQueryWrapper<BackendAccount> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BackendAccount::getEmail, account.getEmail());
        wrapper.eq(BackendAccount::getDeleted, 0);
        BackendAccount existing = accountMapper.selectOne(wrapper);
        if (existing != null) {
            throw new RuntimeException("邮箱已存在");
        }
        
        // 验证密码（创建账号时密码必填）
        if (account.getPassword() == null || account.getPassword().trim().isEmpty()) {
            throw new RuntimeException("密码不能为空");
        }
        if (!PasswordValidator.validate(account.getPassword())) {
            throw new RuntimeException("密码强度不足，需包含字母、数字、符号且长度不少于8位");
        }
        
        // 加密密码
        account.setPassword(passwordEncoder.encode(account.getPassword()));
        
        // 设置默认值
        if (account.getRole() == null) {
            account.setRole("operator");
        }
        if (account.getStatus() == null) {
            account.setStatus("active");
        }
        
        accountMapper.insert(account);
        return account;
    }
    
    /**
     * 更新账号
     * 
     * @param id 账号ID
     * @param account 账号信息
     * @return 更新后的账号
     */
    public BackendAccount updateAccount(Long id, BackendAccount account) {
        BackendAccount existing = getAccountById(id);
        
        if (account.getEmail() != null) {
            existing.setEmail(account.getEmail());
        }
        if (account.getRole() != null) {
            existing.setRole(account.getRole());
        }
        if (account.getStatus() != null) {
            existing.setStatus(account.getStatus());
        }
        // 密码更新（如果提供）
        if (account.getPassword() != null && !account.getPassword().isEmpty()) {
            if (!PasswordValidator.validate(account.getPassword())) {
                throw new BusinessException(ErrorCode.PASSWORD_TOO_WEAK);
            }
            existing.setPassword(passwordEncoder.encode(account.getPassword()));
        }
        
        accountMapper.updateById(existing);
        return existing;
    }
    
    /**
     * 根据ID获取账号
     * 
     * @param id 账号ID
     * @return 账号
     */
    public BackendAccount getAccountById(Long id) {
        BackendAccount account = accountMapper.selectById(id);
        if (account == null) {
            throw new RuntimeException("账号不存在");
        }
        return account;
    }
    
    /**
     * 删除账号
     * 
     * @param id 账号ID
     */
    public void deleteAccount(Long id) {
        getAccountById(id);
        accountMapper.deleteById(id);
    }
}

