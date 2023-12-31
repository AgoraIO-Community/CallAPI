# CallAPI

*[English](README.md) | 中文*

## 概述

CallAPI是声网面向一对一秒开设计的场景化API解决方案，可以让开发者在直播场景下，获得极速秒开、丝滑切换体验。

## 场景描述
CallAPI支持如下场景的1v1呼叫。

### 纯1v1
#### 角色描述
纯1v1场景不需要指定角色，所有角色均可进行主叫和被叫。

### 秀场转1v1

#### 角色描述

| 角色         | 描述                          |
|------------|-----------------------------|
| 主叫(caller) | 直播房间的观众，可以通过呼叫被叫来进入1v1房间。    |
| 被叫(callee) | 直播房间创建者，只能够接受主叫的呼叫从而进入1v1房间。 |

#### CallAPI 提供的核心功能：
- **呼叫**：主叫发起呼叫。
- **取消呼叫**：主叫发起呼叫后可以在通话成功前发起取消呼叫来中断当前的呼叫。
- **接受呼叫**：被叫在接收到主叫的呼叫请求后可以接受当次呼叫。
- **拒绝呼叫**：被叫在接收到主叫的呼叫请求后可以拒绝当次呼叫。
- **挂断**：主叫/被叫在通话中时可以发起挂断请求来中断本次通话。

## 快速集成


| 平台     | Example                   |
|---------|------------------------|
| Android | [CallAPI(Android)](Android) |
| iOS     | [CallAPI(iOS)](iOS)   |

## Demo 体验     

| iOS                                                                              | Android                                                                          |  
|----------------------------------------------------------------------------------|----------------------------------------------------------------------------------|
| ![](https://fullapp.oss-cn-beijing.aliyuncs.com/scenario_api/1v1_qrcode_ios.png?x-oss-process=image/resize,w_200) | ![](https://fullapp.oss-cn-beijing.aliyuncs.com/scenario_api/1v1_qrcode_android.png?x-oss-process=image/resize,w_200) |  
|                                                                                  |                                                                                  |  


---

### 集成遇到困难，该如何联系声网获取协助

> 1.请发送邮件给 [support@agora.io](mailto:support@agora.io) 咨询
> 2.添加issue

---
