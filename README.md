# Meitou 平台管理系统后端

Spring Boot + MyBatis Plus + MySQL 后端服务

## 技术栈

- Spring Boot 3.2.0
- MyBatis Plus 3.5.5
- MySQL 8.0
- Spring Security（基础认证）
- Lombok

## 项目结构

```
backend/
├── src/main/java/com/meitou/admin/
│   ├── AdminApplication.java          # 启动类
│   ├── config/                        # 配置类
│   ├── controller/                    # 控制器层
│   ├── service/                        # 业务逻辑层
│   ├── mapper/                         # MyBatis Mapper
│   ├── entity/                         # 实体类
│   ├── dto/                            # 数据传输对象
│   ├── common/                         # 通用类
│   └── exception/                      # 异常处理
├── src/main/resources/
│   ├── application.yml                 # 应用配置
│   ├── application-dev.yml            # 开发环境配置
│   ├── mapper/                         # MyBatis XML映射文件
│   └── db/                             # 数据库脚本
│       └── init.sql                    # 初始化SQL脚本
└── pom.xml                             # Maven依赖配置
```

## 数据库配置

数据库连接信息：
- 地址：119.91.142.187
- 端口：3306
- 数据库：meitou_admin
- 用户名：root
- 密码：123456

## 启动步骤

1. 执行数据库脚本 `src/main/resources/db/init.sql` 创建数据库和表

2. 配置数据库连接（已在 `application.yml` 中配置）

3. 启动项目：
   ```bash
   mvn spring-boot:run
   ```

4. 访问地址：http://localhost:8080

## API 接口

### 认证接口
- `POST /api/auth/login` - 登录
- `POST /api/auth/logout` - 登出
- `GET /api/auth/check` - 检查登录状态

### 用户管理
- `GET /api/users` - 获取用户列表
- `GET /api/users/{id}` - 获取用户详情
- `POST /api/users` - 创建用户
- `PUT /api/users/{id}` - 更新用户
- `DELETE /api/users/{id}` - 删除用户
- `POST /api/users/{id}/gift-points` - 赠送积分

### 资产管理
- `GET /api/assets` - 获取资产列表
- `GET /api/assets/{id}` - 获取资产详情
- `PUT /api/assets/{id}` - 更新资产
- `DELETE /api/assets/{id}` - 删除资产
- `PUT /api/assets/{id}/pin` - 置顶/取消置顶
- `PUT /api/assets/{id}/status` - 更新状态
- `PUT /api/assets/{id}/like-count` - 更新点赞数

更多接口请参考 Controller 类。

## 注意事项

1. 所有密码使用 BCrypt 加密存储
2. 默认管理员账号：admin@meitou.com（密码需要在数据库中设置）
3. 跨域已配置，允许前端 localhost:3000 访问
4. 所有代码包含中文注释

