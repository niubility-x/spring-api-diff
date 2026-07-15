# spring-api-diff

spring-api-diff 是一个面向 Spring Boot 项目的 REST API 兼容性检查工具。它的重点不是替代 Swagger 文档，而是在开发、提交、PR 或 CI 阶段自动判断接口改动是否会破坏已有调用方。

一句话：改完接口后，运行一条命令，知道这次改动有没有 BREAKING CHANGE。

## 核心价值

- 自动比较 Git 基准分支和当前代码的 Spring REST API。
- 识别删除接口、新增必填参数、字段类型变化、删除响应字段等高风险变更。
- 将变更分为 BREAKING、WARNING、NON_BREAKING，帮助团队判断兼容性风险。
- 支持 `--fail-on-breaking`，可以作为 CI 门禁阻止破坏性接口变更合并。
- 支持 Markdown 和 JSON 报告，方便人工评审或接入质量平台。
- 支持多模块项目、CI 目标分支自动识别、配置文件和 endpoint 忽略规则。

## 当前定位

spring-api-diff 0.1.1 是早期版本，适合在团队内部或开源项目中试点 API 兼容性检查。当前主要覆盖 Java Spring MVC 的常见 Controller 和 DTO 写法；复杂泛型、动态响应、WebFlux、Kotlin、运行时注册路由等场景仍在完善中。

## 环境要求

- JDK 8 或更高版本
- Maven 3.6 或更高版本
- Git

## 安装

### 从源码构建

```bash
git clone https://github.com/niubility-x/spring-api-diff.git
cd spring-api-diff
mvn clean package
```

构建完成后，可执行 Jar 位于：

```text
target/spring-api-diff-0.1.1.jar
```

### 从 GitHub Release 下载

发布 release 后，可以直接下载 `spring-api-diff-0.1.1.jar`，然后在目标 Spring Boot 项目仓库中运行：

```bash
java -jar /path/to/spring-api-diff-0.1.1.jar check
```

## 快速开始

在 Spring Boot 项目仓库中运行：

```bash
java -jar /path/to/spring-api-diff-0.1.1.jar check
```

默认行为：

1. 自动选择基准版本：优先使用 CI 目标分支环境变量，其次使用当前分支 upstream 的 merge-base，再使用 `origin/HEAD`，最后尝试 `origin/main`、`origin/master`、`origin/develop`、`main`、`master`、`develop`。
2. 如果当前工作区有未提交改动，则比较基准分支和当前工作区。
3. 如果工作区干净，则比较基准分支和 `HEAD`。
4. 自动扫描当前项目的 `src/main/java`；如果仓库根目录是多模块父工程，则自动发现子模块中的 `src/main/java`。
5. 在 stderr 输出 Git 准备、API 扫描和快照比较等阶段进度。
6. 输出 API 兼容性摘要和变更列表。

示例输出：

```text
API compatibility check

Base: origin/main
Base source: origin/HEAD
Head: worktree
Scan paths:
- src/main/java

Changes: 3
- BREAKING: 1
- WARNING: 1
- NON_BREAKING: 1

BREAKING
- GET /api/users/{id} | Response field 'email' was removed. (String -> -)
```

## 常见场景

### 改完接口但还没提交

```bash
java -jar spring-api-diff-0.1.1.jar check --base main --worktree
```

### 已经提交到当前分支

```bash
java -jar spring-api-diff-0.1.1.jar check --base main --head HEAD
```

### 多模块项目限定扫描模块

默认情况下，在多模块仓库根目录直接运行 `check` 会自动发现子模块中的 `src/main/java` 并扫描。通常不需要额外参数。

如果只想检查某一个模块，可以使用 `--module` 指定模块路径：

```bash
java -jar spring-api-diff-0.1.1.jar check \
  --base origin/main \
  --head HEAD \
  --module user-service
```

`--module` 是相对于 Git 仓库根目录的路径，也支持更深层目录：

```bash
java -jar spring-api-diff-0.1.1.jar check \
  --base origin/main \
  --head HEAD \
  --module services/user-service
```

如果希望保留自动发现，但排除非 API 模块，可以使用模块 glob 过滤：

```bash
java -jar spring-api-diff-0.1.1.jar check \
  --exclude-module sdip-module-excel2pdf
```

也可以只包含匹配的模块：

```bash
java -jar spring-api-diff-0.1.1.jar check \
  --include-module "sdip-module-report-*"
```

Git 比较仍然基于整个仓库的 `base` 和 `head`，但 API 扫描会定位到：

- base checkout 下的 `user-service/src/main/java`
- head checkout 下的 `user-service/src/main/java`

### 输出 Markdown 报告

```bash
java -jar spring-api-diff-0.1.1.jar check \
  --base origin/main \
  --head HEAD \
  --report api-diff.md
```

### 输出 JSON 报告

```bash
java -jar spring-api-diff-0.1.1.jar check \
  --base origin/main \
  --head HEAD \
  --format json \
  --report api-diff.json
```

JSON 报告包含 summary、changes、impact 和 suggestion，适合接入自定义 CI、质量平台或 PR 评论机器人。

## CI 接入

在 GitHub Actions、GitLab CI 或 Jenkins PR 构建中，工具会优先读取 CI 目标分支环境变量来自动选择基准分支。推荐写法：

```bash
java -jar spring-api-diff-0.1.1.jar check --fetch --fail-on-breaking
```

`--fetch` 会在 CI 目标分支本地缺失时自动拉取对应远程分支，然后继续检查。

工具会自动识别这些环境变量：

- `GITHUB_BASE_REF`
- `CI_MERGE_REQUEST_TARGET_BRANCH_NAME`
- `CI_DEFAULT_BRANCH`
- `CHANGE_TARGET`

如果存在 BREAKING 变更，命令会返回非零退出码。

### GitHub Actions 示例

```yaml
name: API compatibility

on:
  pull_request:

jobs:
  spring-api-diff:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
        with:
          fetch-depth: 0

      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '8'

      - name: Download spring-api-diff
        run: |
          curl -L -o spring-api-diff.jar \
            https://github.com/niubility-x/spring-api-diff/releases/download/v0.1.1/spring-api-diff-0.1.1.jar

      - name: Check API compatibility
        run: java -jar spring-api-diff.jar check --fetch --fail-on-breaking --report api-diff.md
```

### GitLab CI 示例

```yaml
api-compatibility:
  image: eclipse-temurin:8
  script:
    - curl -L -o spring-api-diff.jar https://github.com/niubility-x/spring-api-diff/releases/download/v0.1.1/spring-api-diff-0.1.1.jar
    - java -jar spring-api-diff.jar check --fetch --fail-on-breaking --report api-diff.md
  artifacts:
    when: always
    paths:
      - api-diff.md
```

## check 命令参数

- `--repo`：Git 仓库路径，默认当前目录。
- `--base`：基准 Git ref，例如 `origin/main`、`main`、某个 commit 或 tag。
- `--head`：新版本 Git ref，例如 `HEAD` 或某个分支。
- `--worktree`：用当前工作区作为新版本，包含未提交改动。
- `--module`：指定要扫描的模块路径，相对于 Git 仓库根目录，例如 `user-service` 或 `services/user-service`。
- `--include-module`：只保留自动发现且路径匹配 glob 的模块，例如 `sdip-module-report-*`。
- `--exclude-module`：排除自动发现且路径匹配 glob 的模块，例如 `sdip-module-excel2pdf`。
- `--report`：输出报告文件。
- `--format`：报告格式，支持 `markdown` 和 `json`，默认 `markdown`。
- `--fail-on-breaking`：存在 BREAKING 变更时返回非零退出码。
- `--fetch`：CI 目标分支本地缺失时自动执行 `git fetch origin <branch>:refs/remotes/origin/<branch>` 后继续检查。
- `--include`：只扫描指定包名前缀下的 Controller。
- `--exclude`：排除指定包名前缀下的 Controller。
- `--ignore-endpoint`：忽略匹配 `METHOD PATH` glob 的 endpoint 变更，例如 `POST /internal/**`。
- `--quiet`：关闭写入 stderr 的阶段进度提示，适合需要精简日志的脚本或 CI。

## 配置文件

可以在 Git 仓库根目录创建 `spring-api-diff.yml`，把常用参数固化下来：

```yaml
fetch: true
failOnBreaking: true
quiet: false
report: api-diff.md

modules:
  include:
    - "sdip-module-report-*"
  exclude:
    - "sdip-module-excel2pdf"

include:
  - "com.sdip"
exclude:
  - "com.sdip.internal"

ignore:
  endpoints:
    - "POST /internal/**"
    - "GET /actuator/**"
```

支持的字段：

- `base`：等同于 `--base`
- `head`：等同于 `--head`
- `worktree`：等同于 `--worktree`
- `module`：等同于 `--module`
- `report`：等同于 `--report`
- `fetch`：等同于 `--fetch`
- `failOnBreaking`：等同于 `--fail-on-breaking`
- `quiet`：等同于 `--quiet`
- `modules.include`：等同于 `--include-module`
- `modules.exclude`：等同于 `--exclude-module`
- `include`：等同于 `--include`
- `exclude`：等同于 `--exclude`
- `ignore.endpoints`：等同于 `--ignore-endpoint`

命令行参数优先级高于配置文件。

## 变更等级

### BREAKING

可能破坏已有调用方的变更，例如：

- 删除接口
- 删除路径变量
- 新增必填查询参数
- 查询参数类型变化
- 新增必填请求体字段
- 请求体字段类型变化
- 删除响应字段
- 响应字段类型变化

### WARNING

需要人工确认风险的变更，例如：

- 删除查询参数
- 删除请求体字段

### NON_BREAKING

通常兼容的变更，例如：

- 新增接口
- 新增响应字段

## 当前支持范围

当前版本主要支持：

- Java Spring MVC 项目
- `@RestController` / `@Controller`
- `@RequestMapping`、`@GetMapping`、`@PostMapping`、`@PutMapping`、`@DeleteMapping`、`@PatchMapping`
- 常见 `@PathVariable`、`@RequestParam`、`@RequestBody`
- 常见 DTO 字段、继承字段和泛型响应包装类型
- Maven 单模块和常见多模块仓库

当前不建议完全依赖本工具判断的场景：

- WebFlux 的 `Mono` / `Flux` 响应
- Kotlin Controller 或 Kotlin DTO
- 运行时动态注册路由
- 大量使用 `Map`、`Object`、`JsonNode` 等动态结构的接口
- 复杂 validation group、权限条件或运行时序列化规则
- 需要精确判断 HTTP status、header、cookie、content-type 兼容性的场景

这些场景可以先作为辅助检查结果使用，并结合人工评审。

## 高级用法：快照模式

`check` 是普通用户主入口。底层仍保留 `snapshot` 和 `diff`，适合需要缓存历史快照或自定义流水线的场景。

### 生成 API 快照

```bash
java -jar spring-api-diff-0.1.1.jar snapshot \
  --project /path/to/spring-boot-project \
  --out old.json
```

### 对比 API 快照

```bash
java -jar spring-api-diff-0.1.1.jar diff \
  --old old.json \
  --new new.json \
  --report report.md \
  --fail-on-breaking
```

输出 JSON：

```bash
java -jar spring-api-diff-0.1.1.jar diff \
  --old old.json \
  --new new.json \
  --format json \
  --report report.json
```

## 常见问题

### 提示没有找到 src/main/java

如果没有自动发现到源码目录，请确认项目中是否存在 `src/main/java`。如果只想扫描某个模块，可以指定模块路径：

```bash
java -jar spring-api-diff-0.1.1.jar check --module <module-path>
```

例如：

```bash
java -jar spring-api-diff-0.1.1.jar check --module user-service
```

### 提示模块目录不存在

请确认：

1. `--module` 是相对于 Git 仓库根目录的路径，不是相对于当前 shell 目录。
2. `base` 和 `head` 两个版本都包含该模块。
3. 如果比较远程分支，先执行 `git fetch`，确保本地存在对应 Git ref。

### 提示检测到 CI 目标分支但找不到本地 Git ref

CI 环境变量里有目标分支，但 checkout/fetch 没有把目标分支拉到本地。推荐在命令中加 `--fetch`：

```bash
java -jar spring-api-diff-0.1.1.jar check --fetch --fail-on-breaking
```

也可以手动拉取目标分支：

```bash
git fetch origin main:refs/remotes/origin/main
java -jar spring-api-diff-0.1.1.jar check --fail-on-breaking
```

### 提示 Git ref not found

指定的分支、tag 或 commit 在本地不存在。常见处理方式：

```bash
git fetch origin
java -jar spring-api-diff-0.1.1.jar check --base origin/main --head HEAD
```

### JSON 报告和 Markdown 报告怎么选

- 给人看或贴到 PR：使用默认 Markdown。
- 给 CI、机器人或质量平台消费：使用 `--format json`。

## 测试

```bash
mvn test
```

## 许可协议

本项目采用 MIT License，版权所有 (c) 2026 niubility。

你可以自由使用、复制、修改、分发或商用本项目代码，但必须在副本或主要部分中保留原始版权声明和许可协议文本，以声明原作者来源。

MIT License 不影响作者未来基于本项目推出额外的商业版、收费版或闭源增值功能。
