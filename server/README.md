# sinomed

[使用文档](https://cuihairu.github.io/sinomed/)

## 开发文档

[![Java CI with Maven](https://github.com/cuihairu/sinomed/actions/workflows/maven.yml/badge.svg)](https://github.com/cuihairu/sinomed/actions/workflows/maven.yml)

技术栈： 
- Java 21 LTS
- Spring Boot 4.1.1 
- React 18
- Ant Pro 6
- Nginx

### 目录

```
loader - 电脑客户端
miniapp - 小程序客户端
borker - 小程序后端
web - 网页客户端
src - 服务端
```


查看本地文档docs:

- [本地加强文档](http://127.0.0.1:2347/doc.html)
### 环境依赖

#### 服务端

- Java 21 LTS
- Maven 3.10.0（仓库自带 `./mvnw` 已钉此版，无需另装）

### 项目初始化

假设已经clone项目，并进入了项目根目录:

```bash
server/mvnw install #初始化依赖包
```

#### 客户端

- NodeJs 24
- React 18
- Electron 23

```
npm install -g pnpm
pnpm install
```








