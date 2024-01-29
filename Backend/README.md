# 场景化API, 1v1匹配玩法后端服务JDK

提供后端 CallApi JDK, 具体集成使用可参考 1v1匹配玩法后端服务演示Demo

## 环境准备
* 需要在声网 [Console 平台](https://console.shengwang.cn/)申请 App ID 和 App Certificate
* 本地具备 Docker 环境
* 运行在 Ubuntu 环境, 本地调试可使用 Visual Studio Code 编辑器, 安装 Dev Containers 容器开发插件

## 接口说明
* 主要提供以下 4 个接口, 参考接口定义文件 src/main/java/io/agora/scenarioapi/ICallApi.java
    * initialize - 初始化
    * deinitialize - 销毁/重置
    * call - 用户呼叫
    * renewToken - 刷新 Token

## 示例文件
* 可参考以下示例
```
src/main/java/io/agora/scenarioapi/sample/CallApiSample.java (自动生成 Rtm Client 对象)
src/main/java/io/agora/scenarioapi/sample/CallApiRtmClientSample.java (外部提供 Rtm Client 对象)
```

## 本地运行
* Visual Studio Code 编辑器打开项目源码, 进入容器开发模式, 安装 Extension Pack for Java 插件
* 运行 src/main/java/io/agora/scenarioapi/sample/CallApiSample.java 或 src/main/java/io/agora/scenarioapi/sample/CallApiRtmClientSample.java 文件

这里需要确保已启动1v1匹配玩法前端 App Demo, 用户 userIdA 和 userIdB 已启动成功
运行前修改 appId 和 rtmToken
* 运行成功后, 可根据输出的日志查看结果, 通过1v1匹配玩法前端 App Demo 观看效果

## 目录说明
```
├── src
│   ├── main
│   │   ├── java
│   │   │   └── io
│   │   │       └── agora
│   │   │           └── scenarioapi
│   │   │               ├── CallApiConfig.java                       CallApi JDK 配置类
│   │   │               ├── CallApiImpl.java                         CallApi JDK 实现类
│   │   │               ├── ICallApi.java                            CallApi JDK 接口
│   │   │               ├── sample                                   CallApi Sample示例
│   │   │               │   ├── CallApiRtmClientSample.java
│   │   │               │   └── CallApiSample.java
│   │   └── resources
│   │       ├── lib                                                  RTM依赖文件
│   │       │   ├── agora-rtm-sdk.jar
│   │       │   ├── include
│   │       │   │   ├── AgoraRtmBase.h
│   │       │   │   ├── IAgoraRtmClient.h
│   │       │   │   ├── IAgoraRtmLock.h
│   │       │   │   ├── IAgoraRtmPresence.h
│   │       │   │   ├── IAgoraRtmService.h
│   │       │   │   ├── IAgoraRtmStorage.h
│   │       │   │   ├── IAgoraService.h
│   │       │   │   └── IAgoraStreamChannel.h
│   │       │   ├── libagora-rtm-sdk-jni.so
│   │       │   └── libagora-rtm-sdk.so
│   │       └── logback-spring.xml                                   日志配置
```