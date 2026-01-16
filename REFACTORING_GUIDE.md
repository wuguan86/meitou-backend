# 项目重构指南 - 分包管理

## 重构目标

将backend项目按照管理端和用户端进行分包管理：
- 管理端：`com.meitou.admin.controller.admin` 和 `com.meitou.admin.service.admin`
- 用户端：`com.meitou.admin.controller.app` 和 `com.meitou.admin.service.app`

API路径：
- 管理端：`/api/v1/admin/**`
- 用户端：`/api/v1/app/**`

## 已完成的工作

### Controller（已完成）
✅ 管理端Controller已创建：
- `controller/admin/AuthController.java` - `/api/v1/admin/auth`
- `controller/admin/UploadController.java` - `/api/v1/admin/upload`
- `controller/admin/AccountController.java` - `/api/v1/admin/accounts`
- `controller/admin/UserController.java` - `/api/v1/admin/users`
- `controller/admin/MarketingController.java` - `/api/v1/admin/marketing`
- `controller/admin/DashboardController.java` - `/api/v1/admin/dashboard`
- `controller/admin/MenuController.java` - `/api/v1/admin/menus`
- `controller/admin/GenerationRecordController.java` - `/api/v1/admin/generation-records`
- `controller/admin/InvitationController.java` - `/api/v1/admin/invitations`
- `controller/admin/AssetController.java` - `/api/v1/admin/assets`

✅ 用户端Controller已创建：
- `controller/app/SquareController.java` - `/api/v1/app/square`

### Service（需要完成）

需要将以下Service移动到对应的包：

**管理端Service（移动到 `service/admin`）：**
- `AuthService` → `service/admin/AuthService.java` ✅ 已创建
- `BackendAccountService` → `service/admin/BackendAccountService.java`
- `UserService` → `service/admin/UserService.java`
- `DashboardService` → `service/admin/DashboardService.java`
- `MarketingAdService` → `service/admin/MarketingAdService.java`
- `MenuConfigService` → `service/admin/MenuConfigService.java`
- `GenerationRecordService` → `service/admin/GenerationRecordService.java`
- `InvitationCodeService` → `service/admin/InvitationCodeService.java`
- `UserAssetService` → `service/admin/UserAssetService.java`（管理端管理资产）

**用户端Service（移动到 `service/app`）：**
- `UserAssetService` → `service/app/UserAssetService.java`（用户端查看资产）

## 迁移步骤

### 1. 移动Service文件

对于每个Service文件：
1. 复制原文件内容
2. 修改package声明为新的包名
3. 更新所有import语句（如果有引用其他Service）
4. 保存到新位置
5. 删除旧文件

### 2. 更新Controller中的import

所有Controller中的Service import需要更新：
- `com.meitou.admin.service.XXX` → `com.meitou.admin.service.admin.XXX` 或 `com.meitou.admin.service.app.XXX`

### 3. 删除旧的Controller文件

删除 `controller/` 目录下的所有旧Controller文件：
- `AuthController.java`
- `UploadController.java`
- `AccountController.java`
- `UserController.java`
- `MarketingController.java`
- `DashboardController.java`
- `MenuController.java`
- `GenerationRecordController.java`
- `InvitationController.java`
- `AssetController.java`
- `SquareController.java`

### 4. 更新前端API调用

前端项目需要更新API路径：
- 管理端（前端-后台）：所有API路径从 `/api/xxx` 改为 `/api/v1/admin/xxx`
- 用户端（前端-用户端）：API路径从 `/api/xxx` 改为 `/api/v1/app/xxx`

## 注意事项

1. **FileStorageService** 是通用服务，可以保留在 `service/` 目录下
2. **Service实现类**（如 `TencentCosServiceImpl`）可以保留在 `service/impl/` 目录下
3. 确保所有Mapper的import路径正确（Mapper不需要移动）
4. 确保所有Entity和DTO的import路径正确（这些不需要移动）

## 快速迁移脚本建议

可以使用IDE的重构功能：
1. 选中Service类
2. 右键 → Refactor → Move
3. 选择目标包
4. IDE会自动更新所有引用

