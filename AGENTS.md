# AE2Enhanced —— AI 编码代理项目指南

> 本文档面向 AI 编码代理。阅读本文档前，默认读者对本项目一无所知。
> **注意：当前分支为 `1.20.1-neoforge`，开发平台为 Minecraft 1.20.1 / NeoForge 47.1.106。**

---

## 项目概览

**AE2Enhanced** 是一个针对 **Minecraft 1.20.1 / NeoForge 47.1.106** 的 NeoForge 模组项目，作为 **Applied Energistics 2 (AE2) 15.3.4** 的扩展。

- **当前版本**：**2.0.0**
- **包名**：`com.github.aeddddd.ae2enhanced`
- **Group ID**：`com.github.aeddddd`
- **Mod ID**：`ae2enhanced`

---

## 当前分支目标

本分支已从 `master`（1.12.2 / Forge）清理全部旧代码，当前仅保留最小可构建骨架。

**当前移植计划范围**：

1. **三个多方块结构**
   - 超因果装配枢纽（Assembly Hub）
   - 超维度仓储中枢（Hyperdimensional Storage Nexus）
   - 超因果计算核心（Supercausal Computation Core）
2. **先进 ME 工具**
   - 先进 ME 全能工具（Advanced ME Omni Tool）
   - ME 放置工具（ME Placement Tool）

其他 1.12 功能（个人维度、气体/源质假物品、第三方机器远程接口等）暂不移植。

---

## 项目目录结构

| 目录 | 说明 |
|---|---|
| `src/main/java/.../ae2enhanced/` | **主源码目录**。当前仅含入口类 `AE2Enhanced.java`。 |
| `src/main/resources/assets/ae2enhanced/` | **资源目录**。当前为空，待后续添加模型、纹理、配方、lang 文件。 |
| `src/main/resources/META-INF/mods.toml` | **mod 信息文件**。`${version}` / `${mod_id}` 由 processResources 展开。 |
| `src/generated/resources/` | **数据生成输出目录**。由 runData 任务生成。 |
| `src/test/` | **测试目录**（当前为空）。 |
| `build.gradle` | NeoGradle 构建脚本。 |
| `gradle.properties` | Gradle 与 mod 基础属性。 |
| `libs/` | **本地依赖 jar**。当前仅存放 `appliedenergistics2-forge-15.3.4.jar`。 |

### 文档

- `README.md` / `README_zh.md`：面向人类贡献者。
- `AGENTS.md`：面向 AI 编码代理（即本文件）。
- `docs/`、`research/`、`github-wiki/`：已在 `.gitignore` 中排除，不加入 Git。

---

## 技术栈与构建环境

| 组件 | 版本 / 说明 |
|---|---|
| Java | **17** |
| Gradle | **8.5** |
| 构建插件 | **NeoGradle 7.0.145** (`net.neoforged.gradle.userdev`) |
| 目标平台 | Minecraft 1.20.1 + NeoForge 47.1.106 + AE2 15.3.4 |
| Mappings | 官方映射（NeoGradle 自动提供） |

### 本地依赖（`libs/` 目录）

| 文件 | 用途 |
|---|---|
| `appliedenergistics2-forge-15.3.4.jar` | **必需**：AE2 主 mod（1.20.1 Forge 构建，1.20.1 NeoForge 兼容层加载） |

### 构建命令

```bash
./gradlew build      # 编译并打包，输出 build/libs/AE2Enhanced-2.0.0.jar
./gradlew runClient  # 运行客户端（开发环境）
./gradlew runServer  # 运行服务端（开发环境）
./gradlew runData    # 运行数据生成
./gradlew test       # 运行测试（src/test 为空）
./gradlew clean      # 清理构建产物
```

---

## 代码风格与开发约定

1. **语言**：源码注释、日志提示以**中文**为主。GUI 文本必须使用本地化键（`lang/*.json`）。Git commit message 使用英文。
2. **包名**：所有类必须使用 `com.github.aeddddd.ae2enhanced`。
3. **注册方式**：1.20.1 使用 `DeferredRegister` 进行方块、物品、方块实体、菜单类型的注册；禁止回到 1.12 的 `GameRegistry.register` 模式。
4. **网络**：使用 NeoForge `SimpleChannel` + `NetworkEvent`；禁止继续使用 1.12 的 `SimpleNetworkWrapper` + `IMessage`。
5. **GUI**：使用 `MenuType` + `AbstractContainerMenu` + `Screen`；通过 `NetworkHooks.openScreen` 打开。
6. **方块实体**：使用 NeoForge `BlockEntity` + `DeferredRegister<BlockEntityType>`。
7. **结构验证**：`structure/` 包下的坐标集与验证逻辑可继承 1.12 设计思路，但需将 `EnumFacing`→`Direction`、`IBlockState`→`BlockState`、`World` API 替换为 1.20 对应 API。
8. **AE2 API**：1.20 AE2 官方 API 与 1.12 AE2-UEL 差异巨大，禁止直接复制旧 import；必须对照 AE2 15.3.4 源码/文档重写。
9. **Lang 同步**：任何新增 GUI 文本、tooltip、物品名称，必须同时更新 `en_us.json` 和 `zh_cn.json`。
10. **版本号同步**：`build.gradle` 中的 `version` 与 `gradle.properties` 中的 `mod_version`、源码中的常量必须保持一致。
11. **禁止加入 Git 的文件**：`.gradle/`、`build/`、`bin/`、`.idea/`、`.vscode/`、`run/`、`runClient/`、`runServer/`、`docs/`、`research/`、`github-wiki/`、本地日志、反编译资料等。详见 `.gitignore`。

---

## 代理操作规则

### 存在疑问时直接询问

当对需求、设计或实现细节存在任何不确定性时，必须直接向用户提问确认，严禁基于猜测或假设继续推进。

### 编译与 Git 提交规则

1. **每次代码修改后必须执行完整构建**：使用 `./gradlew build` 编译并打包。
2. **每次代码修改后必须提交 Git**：使用英文 commit message。

### 禁止擅自简化

当主分支（`master`）中已有成熟参考实现时，严禁以“优化”“精简”为由替换为简化方案。向 1.20.1 移植时应优先复刻原逻辑，仅在 API 不兼容时做必要改写。

---

## 1.12 → 1.20.1 移植关键差异速查

| 1.12 (AE2-UEL) | 1.20.1 (AE2 官方) |
|---|---|
| `TileEntity` | `BlockEntity` |
| `WorldSavedData` | `SavedData` |
| `EnumFacing` | `Direction` |
| `IBlockState` / `BlockState` | `BlockState` |
| `GameRegistry.registerTileEntity` | `DeferredRegister<BlockEntityType>` |
| `SimpleNetworkWrapper` + `IMessage` | `SimpleChannel` + `NetworkEvent` |
| `IGuiHandler` + `Container`/`GuiContainer` | `MenuType` + `AbstractContainerMenu` + `Screen` |
| `player.openGui` | `NetworkHooks.openScreen` |
| `ItemStack` NBT：`getTagCompound`/`setTag` | `getTag`/`setTag` / `getOrCreateTag` |
| `CreativeTabs` | `CreativeModeTab` + `DeferredRegister` |
| `@Config` / `ConfigManager.sync` | `ModConfigSpec` / `ForgeConfigSpec` |
| AE2 `appeng.api.storage.data.IAEItemStack` | AE2 1.20 对应 API（需重新核对） |
| AE2 `appeng.me.cluster.implementations.CraftingCPUCluster` | AE2 1.20 对应内部类（可能不存在） |

---

*文档版本：v2.0.0-active | 当前分支：1.20.1-neoforge | 最后更新：2026-06-29*
