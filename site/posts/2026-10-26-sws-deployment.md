---
title: "在阿里云 ECS 上使用 Rust Static Web Server 部署 HTTPS 静态网站并自动发布"

tags:
  - Rust
  - ECS
  - Website
---

本文记录如何在一台 **2 GB RAM、x86_64、Ubuntu** 的阿里云 ECS 上，使用 Rust 编写的 Static Web Server（SWS）部署静态网站，并配置 HTTPS、`systemd`、URL 重定向，以及 GitHub Actions 通过 SSH + `rsync` 自动发布。

本文使用以下约定：

| 项目 | 值 |
| --- | --- |
| 域名 | `lenox.fyi` |
| SWS 版本 | `v2.44.1` |
| 静态文件目录 | `/var/www/site` |
| SWS 配置 | `/etc/static-web-server.toml` |
| `systemd` 服务 | `/etc/systemd/system/sws.service` |
| TLS 证书副本 | `/etc/static-web-server/certs/` |
| 部署用户 | `deploy` |
| SWS 运行用户 | `sws` |
| 默认分支 | `trunk` |

> 命令默认在 ECS 上以具有 `sudo` 权限的用户执行；带有 `YOUR_...` 的值需要替换为自己的实际信息。部署前请确认 DNS、公网 IP 和安全组配置正确。

### 1. 配置 DNS 和安全组

在域名 DNS 管理中添加 A 记录：

| 主机记录 | 类型 | 记录值 |
| --- | --- | --- |
| `@` | A | ECS 公网 IPv4 地址 |

如果还希望通过 `www.lenox.fyi` 访问，可以额外添加 `www` 的 A 记录，指向同一 IP。只有在证书包含 `www.lenox.fyi` 时，才能保证通过 HTTPS 访问 `www` 不会出现证书错误。

在阿里云 ECS 安全组中放行：

- TCP `80`：用于 HTTP 和 Certbot standalone 验证。
- TCP `443`：用于 HTTPS。
- TCP `22`：用于 SSH；建议限制为自己的管理 IP 或可信来源。

确认域名已解析到 ECS：

```bash
dig +short lenox.fyi
```

返回结果应包含 ECS 的公网 IPv4 地址。

### 2. 安装依赖和 SWS

更新软件包并安装 Certbot、`rsync` 等工具：

```bash
sudo apt update
sudo apt install -y curl ca-certificates certbot rsync
```

安装指定版本的 SWS：

```bash
curl --proto '=https' --tlsv1.2 -sSfL \
  https://get.static-web-server.net \
  | sudo env SWS_INSTALL_VERSION="2.44.1" sh
```

检查安装结果：

```bash
/usr/local/bin/static-web-server --version
```

确认输出版本为 `2.44.1`。如果安装脚本无法获取该版本，请查看 [SWS 官方安装文档](https://static-web-server.net/v2/) 和 [GitHub Releases](https://github.com/static-web-server/static-web-server/releases)，不要在未确认版本的情况下继续部署。

### 3. 创建运行用户和目录

创建专门的 SWS 系统用户，以及用于 SSH 部署的用户：

```bash
sudo useradd --system --home /nonexistent --shell /usr/sbin/nologin sws
sudo adduser deploy
```

如果 `sws` 或 `deploy` 已经存在，不要重复创建；按实际情况跳过对应命令。

创建网站和证书目录：

```bash
sudo install -d -o deploy -g deploy -m 755 /var/www/site
sudo install -d -o root -g sws -m 750 /etc/static-web-server/certs
```

SWS 只需要读取 `/var/www/site` 中的静态文件；部署用户 `deploy` 负责更新文件。不要把私钥、环境变量文件或其他敏感数据放在网站根目录内。

### 4. 申请 HTTPS 证书

确保 DNS 已生效，并且 TCP `80` 可以从公网访问。先确认没有其他服务占用 `80` 端口：

```bash
sudo ss -lntp | grep ':80 ' || true
```

使用 Certbot standalone 模式申请证书：

```bash
sudo certbot certonly --standalone -d lenox.fyi
```

如果还要提供 `www.lenox.fyi`，且 DNS 已配置，可以一并申请：

```bash
sudo certbot certonly --standalone \
  -d lenox.fyi \
  -d www.lenox.fyi
```

将证书复制到 SWS 可以读取的位置。SWS 运行用户通常不能直接读取 `/etc/letsencrypt/live/` 下的私钥，因此使用独立副本：

```bash
sudo install -o root -g sws -m 640 \
  /etc/letsencrypt/live/lenox.fyi/fullchain.pem \
  /etc/static-web-server/certs/fullchain.pem

sudo install -o root -g sws -m 640 \
  /etc/letsencrypt/live/lenox.fyi/privkey.pem \
  /etc/static-web-server/certs/privkey.pem
```

如果证书使用了多个域名，证书目录仍通常为 `/etc/letsencrypt/live/lenox.fyi/`，请以 Certbot 实际输出为准。

### 5. 配置 SWS

编辑配置文件：

```bash
sudo nano /etc/static-web-server.toml
```

示例配置：

```toml
[general]
host = "0.0.0.0"
port = 443
root = "/var/www/site"

# HTTPS / HTTP/2
http2 = true
http2-tls-cert = "/etc/static-web-server/certs/fullchain.pem"
http2-tls-key = "/etc/static-web-server/certs/privkey.pem"

# 将 HTTP 请求重定向到 HTTPS
https-redirect = true
https-redirect-host = "lenox.fyi"
https-redirect-from-port = 80
https-redirect-from-hosts = "lenox.fyi"

# 静态资源
compression = true
cache-control-headers = true
security-headers = true
log-level = "info"

[advanced]

# 旧 Feed 地址永久重定向到 Atom Feed
[[advanced.redirects]]
source = "/feed.xml"
destination = "/atom.xml"
kind = 301
```

如果 DNS 也配置了 `www.lenox.fyi`，并且希望 HTTP 的 `www` 请求也通过 SWS 跳转到 HTTPS，可以将：

```toml
https-redirect-from-hosts = "lenox.fyi,www.lenox.fyi"
```

如果要让 `https://www.lenox.fyi/...` 也重定向到根域名，证书必须先覆盖 `www.lenox.fyi`，然后可添加一条主机匹配规则：

```toml
[[advanced.redirects]]
host = "www.lenox.fyi"
source = "/{*}"
destination = "https://lenox.fyi/$1"
kind = 301
```

> `[[advanced.redirects]]` 是数组表，可以重复出现多次。若已有 `[advanced]`，不要再次声明该表头。SWS 的路径规则使用 glob 模式；`/{*}` 用于捕获路径部分，并通过 `$1` 放入目标地址。可参阅 [SWS URL Redirects 文档](https://static-web-server.net/v2/features/url-redirects/)。

### 6. 配置 systemd

创建服务文件：

```bash
sudo nano /etc/systemd/system/sws.service
```

写入：

```ini
[Unit]
Description=Rust Static Web Server
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=sws
Group=sws
WorkingDirectory=/var/www/site
ExecStart=/usr/local/bin/static-web-server --config-file /etc/static-web-server.toml
Restart=on-failure
RestartSec=3

# 允许非 root 进程监听 80/443 等低端口
AmbientCapabilities=CAP_NET_BIND_SERVICE
CapabilityBoundingSet=CAP_NET_BIND_SERVICE
NoNewPrivileges=true

# 基础系统加固
ProtectSystem=strict
ProtectHome=true
PrivateTmp=true
ProtectKernelTunables=true
ProtectControlGroups=true
RestrictSUIDSGID=true

[Install]
WantedBy=multi-user.target
```

加载并启动服务：

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now sws
sudo systemctl status sws
```

查看日志：

```bash
sudo journalctl -u sws -n 100 --no-pager
```

检查监听端口：

```bash
sudo ss -lntp | grep -E ':(80|443)\b'
```

SWS 应在 HTTPS 端口提供网站，并在 HTTP 端口处理跳转请求。若启动失败，优先检查 TOML 配置、证书权限，以及 `80`/`443` 端口是否已被其他服务占用。

### 7. 配置证书自动续期

SWS 使用复制到 `/etc/static-web-server/certs/` 的证书文件，所以证书续期后必须重新复制证书并重启 SWS。

创建 Certbot 的 pre-hook，用于释放 `80` 端口：

```bash
sudo install -d -m 755 /etc/letsencrypt/renewal-hooks/pre
sudo tee /etc/letsencrypt/renewal-hooks/pre/stop-sws >/dev/null <<'EOF'
#!/bin/sh
systemctl stop sws || true
EOF
sudo chmod 755 /etc/letsencrypt/renewal-hooks/pre/stop-sws
```

创建 deploy-hook，续期成功后更新证书副本并重启 SWS：

```bash
sudo install -d -m 755 /etc/letsencrypt/renewal-hooks/deploy
sudo tee /etc/letsencrypt/renewal-hooks/deploy/deploy-sws-certificate >/dev/null <<'EOF'
#!/bin/sh
set -eu

install -o root -g sws -m 640 \
  /etc/letsencrypt/live/lenox.fyi/fullchain.pem \
  /etc/static-web-server/certs/fullchain.pem

install -o root -g sws -m 640 \
  /etc/letsencrypt/live/lenox.fyi/privkey.pem \
  /etc/static-web-server/certs/privkey.pem

systemctl restart sws
EOF
sudo chmod 755 /etc/letsencrypt/renewal-hooks/deploy/deploy-sws-certificate
```

创建 post-hook，确保续期流程结束后服务恢复：

```bash
sudo install -d -m 755 /etc/letsencrypt/renewal-hooks/post
sudo tee /etc/letsencrypt/renewal-hooks/post/start-sws >/dev/null <<'EOF'
#!/bin/sh
systemctl start sws || true
EOF
sudo chmod 755 /etc/letsencrypt/renewal-hooks/post/start-sws
```

启用定时续期并测试：

```bash
sudo systemctl enable --now certbot.timer
sudo certbot renew --dry-run
sudo systemctl status certbot.timer
```

> standalone 验证需要 `80` 端口，因此续期期间 SWS 会短暂停止。`--dry-run` 主要用于验证续期流程；测试后还应检查 `sws` 服务状态与日志。若当前主机上已有其他依赖 SWS 的服务，请先规划维护窗口。

### 8. 配置 SSH 部署账号

在本地生成专用的部署密钥（如果已有专用密钥，可复用）：

```bash
ssh-keygen -t ed25519 -C "github-actions-lenox-fyi" -f ~/.ssh/lenox_fyi_deploy
```

将公钥添加到 ECS 的 `/home/deploy/.ssh/authorized_keys`。可先在本地查看公钥：

```bash
cat ~/.ssh/lenox_fyi_deploy.pub
```

在 ECS 上设置权限：

```bash
sudo install -d -o deploy -g deploy -m 700 /home/deploy/.ssh
sudo nano /home/deploy/.ssh/authorized_keys
sudo chown deploy:deploy /home/deploy/.ssh/authorized_keys
sudo chmod 600 /home/deploy/.ssh/authorized_keys
```

先从本地验证连接：

```bash
ssh -i ~/.ssh/lenox_fyi_deploy deploy@YOUR_ECS_IP
```

部署用户需要能写入 `/var/www/site`。验证：

```bash
ssh -i ~/.ssh/lenox_fyi_deploy deploy@YOUR_ECS_IP \
  'test -d /var/www/site && test -w /var/www/site'
```

为 GitHub Actions 配置以下仓库 Secrets（仓库路径：`nikeorever/lenox.fyi`）：

| Secret | 内容 |
| --- | --- |
| `ECS_HOST` | ECS 公网 IP 或主机名 |
| `ECS_SSH_KEY` | 专用私钥的完整内容 |
| `ECS_KNOWN_HOSTS` | 经可信渠道核验后的 SSH 主机公钥记录 |

`ECS_KNOWN_HOSTS` 应来自已验证的服务器主机指纹。不要关闭 `StrictHostKeyChecking`，也不要把私钥提交到仓库。

### 9. 使用 GitHub Actions 自动构建与发布

在仓库中创建或更新 `.github/workflows/site.yml`：

```yaml
name: site

on:
  pull_request: {}
  workflow_dispatch: {}
  push:
    branches:
      - trunk

permissions:
  contents: read

jobs:
  compile:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1 # v7.0.1

      - name: Build Docker image
        run: docker build -t lenox .

      - name: Generate site
        run: docker run --rm --volume="$PWD/site:/blog" lenox /blog

      - name: Upload site artifact
        uses: actions/upload-artifact@043fb46d1a93c77aae656e7c1c64a875d1fc6a0a # v7.0.1
        with:
          name: site
          path: site/out
          if-no-files-found: error

  check:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1 # v7.0.1

      - uses: actions/setup-java@de7274f081f381c8f8158605e0321c36c376e2e6 # v6.0.1
        with:
          distribution: zulu
          java-version-file: .github/workflows/.java-version

      - run: ./gradlew check

  publish:
    runs-on: ubuntu-latest
    needs:
      - compile
      - check

    if: >-
      ${{
        github.repository == 'nikeorever/lenox.fyi'
        && github.ref == 'refs/heads/trunk'
        && github.event_name != 'pull_request'
      }}

    concurrency:
      group: production-site-deploy
      cancel-in-progress: false

    steps:
      - name: Download site artifact
        uses: actions/download-artifact@3e5f45b2cfb9172054b4087a40e8e0b5a5461e7c # v8.0.1
        with:
          name: site
          path: out

      - name: Configure SSH
        env:
          ECS_SSH_KEY: ${{ secrets.ECS_SSH_KEY }}
          ECS_KNOWN_HOSTS: ${{ secrets.ECS_KNOWN_HOSTS }}
        run: |
          install -m 700 -d ~/.ssh
          printf '%s\n' "$ECS_SSH_KEY" > ~/.ssh/id_ed25519
          chmod 600 ~/.ssh/id_ed25519
          printf '%s\n' "$ECS_KNOWN_HOSTS" > ~/.ssh/known_hosts
          chmod 600 ~/.ssh/known_hosts

      - name: Test SSH connection
        env:
          ECS_HOST: ${{ secrets.ECS_HOST }}
        run: |
          ssh \
            -i ~/.ssh/id_ed25519 \
            -o IdentitiesOnly=yes \
            -o StrictHostKeyChecking=yes \
            "deploy@${ECS_HOST}" \
            'test -d /var/www/site && test -w /var/www/site'

      - name: Deploy to ECS
        env:
          ECS_HOST: ${{ secrets.ECS_HOST }}
        run: |
          rsync -az \
            --delete \
            --delay-updates \
            --chmod=D755,F644 \
            -e "ssh -i ~/.ssh/id_ed25519 -o IdentitiesOnly=yes -o StrictHostKeyChecking=yes" \
            out/ \
            "deploy@${ECS_HOST}:/var/www/site/"
```

工作流行为：

- Pull request：执行构建与检查，不发布。
- `trunk` 分支 push：执行构建与检查，两个 job 都成功后才发布。
- 手动触发：通过 `workflow_dispatch` 启动；由于 `publish` 还会检查分支，因此只有 `trunk` 才会部署。
- `rsync --delete` 会删除远端目标目录中不在本次构建产物里的文件，因此 `/var/www/site` 应专用于本网站静态文件。

> GitHub Actions 的 Action 版本和提交 SHA 应定期核验。若 GitHub 账户仍因账单问题被锁定，workflow 可能无法启动；可先在本地完成构建和检查，并用 SSH + `rsync` 手动发布。

### 10. 验证部署与重定向

确认首页可访问：

```bash
curl -I https://lenox.fyi/
```

确认 HTTP 自动跳转到 HTTPS：

```bash
curl -I http://lenox.fyi/
```

确认 Feed 地址永久重定向到 Atom：

```bash
curl -I https://lenox.fyi/feed.xml
```

预期响应包含：

```http
HTTP/2 301
location: /atom.xml
```

确认目标文件存在：

```bash
curl -I https://lenox.fyi/atom.xml
```

也可以使用 `-L` 查看完整跳转链：

```bash
curl -IL https://lenox.fyi/feed.xml
```

### 11. 日常维护命令

```bash
# 服务状态
sudo systemctl status sws

# 最近日志
sudo journalctl -u sws -n 100 --no-pager

# 实时日志
sudo journalctl -u sws -f

# 检查端口
sudo ss -lntp | grep -E ':(80|443)\b'

# 检查证书续期
sudo certbot renew --dry-run

# 重新加载 systemd 服务定义
sudo systemctl daemon-reload

# 重启 SWS
sudo systemctl restart sws
```

修改 `/etc/static-web-server.toml` 后，需要重启 SWS 才能使配置生效。

### 参考资料

- [Static Web Server v2 文档](https://static-web-server.net/v2/)
- [SWS URL Redirects](https://static-web-server.net/v2/features/url-redirects/)
- [SWS HTTP-to-HTTPS Redirect](https://static-web-server.net/v2/features/http-https-redirect/)
- [SWS GitHub Releases](https://github.com/static-web-server/static-web-server/releases)
- [Certbot 文档](https://certbot.eff.org/)
