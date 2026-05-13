# 版本检查服务器配置教程（从零开始）

## 前提

- 阿里云轻量应用服务器（CentOS / Alibaba Cloud Linux）
- 域名 `tallyapp.top`
- 当前登录用户是 `admin`，需要用 `sudo` 执行管理命令

## 操作步骤

### 第 1 步：切换到 root 或使用 sudo

你当前是 `admin` 用户，所有安装命令前面加 `sudo`：

```bash
# 先测试 sudo 是否可用
sudo whoami
```

如果提示输入密码，输入 admin 用户的密码。如果 sudo 不可用，需要先切换到 root：
```bash
su - root
```

### 第 2 步：安装 Nginx

```bash
# 更新系统包（CentOS/Alibaba Cloud Linux 用 yum）
sudo yum update -y

# 安装 Nginx
sudo yum install nginx -y

# 启动 Nginx 并设置开机自启
sudo systemctl start nginx
sudo systemctl enable nginx
```

如果 `yum install nginx` 报找不到包，先安装 EPEL 源：
```bash
sudo yum install epel-release -y
sudo yum install nginx -y
```

### 第 3 步：确认 Nginx 的 Web 根目录

```bash
# 查看 Nginx 配置中的 root 路径
sudo cat /etc/nginx/nginx.conf | grep root
```

通常是 `/usr/share/nginx/html`。如果看到类似：
```
root /usr/share/nginx/html;
```

### 第 4 步：创建版本文件

```bash
# 先创建目录（如果不存在）
sudo mkdir -p /usr/share/nginx/html

# 创建 version.json
sudo bash -c 'echo "{\"version\": \"3.7.3\"}" > /usr/share/nginx/html/version.json'
```

验证文件内容：
```bash
cat /usr/share/nginx/html/version.json
```

应该输出：`{"version": "3.7.3"}`

### 第 5 步：验证 Nginx 运行状态

```bash
sudo systemctl status nginx
```

如果显示 `active (running)` 就是正常的。

在浏览器访问 `http://tallyapp.top/version.json`，应该看到 JSON 内容。

### 第 6 步：配置 HTTPS

```bash
# 安装 certbot
sudo yum install certbot python3-certbot-nginx -y

# 如果上面报错，试这个：
# sudo yum install certbot python2-certbot-nginx -y

# 申请证书并自动配置
sudo certbot --nginx -d tallyapp.top
```

过程中会问：
1. 输入邮箱 → 输入你的邮箱
2. 同意条款 → 输入 `A`
3. 是否分享邮箱 → 输入 `N`

完成后验证：`https://tallyapp.top/version.json`

### 第 7 步：防火墙放行端口

```bash
# 查看防火墙状态
sudo firewall-cmd --state

# 如果防火墙是 running，放行 80 和 443 端口
sudo firewall-cmd --permanent --add-service=http
sudo firewall-cmd --permanent --add-service=https
sudo firewall-cmd --reload
```

同时在**阿里云控制台**的防火墙/安全组中也要放行：
- 端口 80（HTTP）
- 端口 443（HTTPS）

---

## 完整命令汇总（按顺序执行）

```bash
sudo yum update -y
sudo yum install epel-release -y
sudo yum install nginx -y
sudo systemctl start nginx
sudo systemctl enable nginx
sudo bash -c 'echo "{\"version\": \"3.7.3\"}" > /usr/share/nginx/html/version.json'
sudo firewall-cmd --permanent --add-service=http
sudo firewall-cmd --permanent --add-service=https
sudo firewall-cmd --reload
sudo yum install certbot python3-certbot-nginx -y
sudo certbot --nginx -d tallyapp.top
```

---

## 后续更新版本

每次发布新版 App 时：
```bash
sudo bash -c 'echo "{\"version\": \"3.8.0\"}" > /usr/share/nginx/html/version.json'
```

---

## 排查问题

| 问题 | 解决方案 |
|------|----------|
| `apt: command not found` | 你的系统是 CentOS，用 `yum` 代替 `apt` |
| `nginx.service failed` | 执行 `sudo journalctl -xe` 查看具体错误 |
| 浏览器访问域名无响应 | 检查阿里云安全组 + 服务器防火墙是否放行 80/443 |
| certbot 报错 | 确认域名 DNS 已解析到服务器 IP，且 80 端口可访问 |
| 权限不够 | 命令前加 `sudo` |

### 确认域名解析

```bash
# 在服务器上测试
ping tallyapp.top
```

如果解析不到 IP，去阿里云 DNS 控制台添加 A 记录指向服务器公网 IP。
