# CallAPI Example

本文档主要介绍如何快速跑通 CallAPI 示例工程
- [CallAPI Example](#callapi-example)
  - [1. 开通服务](#1-开通服务)
  - [2. 运行示例](#2-运行示例)
  - [3. 项目介绍](#3-项目介绍)
  - [4. 快速集成](#4-快速集成)
  - [5. 进阶集成](#5-进阶集成)
  - [6. API说明](#6-api说明)
  - [7. 实现原理](#7-实现原理)
    - [7.1 优化呼叫性能和可靠性](#71-优化呼叫性能和可靠性)
      - [7.1.1 加快出图速度](#711-加快出图速度)
      - [7.1.2 提升消息送达率](#712-提升消息送达率)
    - [7.2 影响通话速度的指标](#72-影响通话速度的指标)

## 1. 开通服务
请参考官网文档 [开通服务](https://doc.shengwang.cn/doc/one-to-one-live/android/rtm/get-started/enable-service)

## 2. 运行示例

- 克隆或者直接下载项目源码
- 打开 Android Studio，并用它来打开项目的 [Android](../Android) 目录。IDE 会自动开始构建项目
- 在项目根目录找到 [gradle.properties](gradle.properties)，填入声网的 AppId 和 Certificate 以及环信 AppKey(如果不需要体验环信自定义信令流程，IM_APP_KEY 可以设置为`""`)
  ```
  AG_APP_ID=
  AG_APP_CERTIFICATE=
  IM_APP_KEY=
  ```
- 在 Android Studio 顶部工具栏中，单击“File”->选择“Sync Project With Gradle File”，等待 Gradle 同步完成，即可运行项目并进行调试


## 3. 项目介绍
请参考官网文档 [场景介绍](https://doc.shengwang.cn/doc/one-to-one-live/android/rtm/overview/product-overview)

>  **CallApi是一套开源的纯业务逻辑的呼叫邀请模块，您可以自由定制和修改，而不会限制您的业务流程。**
> 
> **CallApi不涉及任何UI，您可以根据自己的需求灵活地自定义UI。**
  
## 4. 快速集成
请参考官网文档 [集成 CallAPI](https://doc.shengwang.cn/doc/one-to-one-live/android/rtm/get-started/integrate-callapi)


## 5. 进阶集成
请参考官网文档 [进阶集成指引](https://doc.shengwang.cn/doc/one-to-one-live/android/rtm/advanced-features/integration-guideline)

## 6. API说明
  
请参考官网文档 [场景化 API](https://doc.shengwang.cn/doc/one-to-one-live/android/rtm/api/call-api)
  
## 7. 实现原理
### 7.1 优化呼叫性能和可靠性
#### 7.1.1 加快出图速度
- 1.使用[万能 Token](https://doc.shengwang.cn/doc/rtc/ios/best-practice/wildcard-token)
  - 为了提高通话质量和稳定性，我们采用万能 Token，可以节省因加入不同频道获取 Token 的时间，这意味着，在使用我们的服务时，您无需频繁获取 Token，而只需使用一个固定的 Token 即可。这样不仅可以提高您的使用效率，还可以让您更加专注于通话内容本身。
  - > 注意：为了保障通话的私密性和安全性，推荐每次呼叫都采用**不同的RTC频道号**。
  - **为了保障通话的私密性和安全性，推荐每次呼叫都采用不同的 RTC 频道号**。
- 2.加快主叫出图速度
  - 2.1 **`[可选]`** 初始化时，可以提前加入自己的 RTC 频道。**`请注意，这种行为可能会导致额外的费用。如果对费用比较在意，您可以选择忽略此步骤`**。
  - 2.2 在向被叫发起呼叫时
    - 2.2.1 加入自己的 RTC 频道。
    - 2.2.2 往自己的 RTC 频道发送音视频流。
    - 2.2.3 订阅远端的视频流，不订阅音频流。
    - 2.2.4 同时，为了避免错过首个 I 帧解码导致可能的首帧渲染慢，您需要创建一个临时的画布，并使用 `setupRemoteVideoEx` 方法将被叫用户的视频流渲染到该画布中。
  - 2.3 当收到收到被叫的接受消息后，开始订阅远端音频流。
  - 2.4 当收到被叫方的首帧并且已经接收到被叫方的同意后，即可认为连接成功。此时，您可以将之前创建的临时画布添加到视图中，完成视频的渲染。
- 3.加快被叫出图速度
  - 3.1 **`[可选][推荐]`** 当收到主叫呼叫后
    - 3.1.1 立即加入主叫的 RTC 频道。
    - 3.1.2 往主叫 RTC 频道推送音视频流。
    - 3.1.3 然后订阅远端的视频流，不订阅音频流。
    - 3.1.4 同时，为了避免错过首个 I 帧解码导致可能的首帧渲染慢，您需要创建一个临时的画布，并使用 `setupRemoteVideoEx` 方法将主叫用户的视频流渲染到该画布中。
      **`请注意，[步骤3.1]会导致额外费用。如果对费用比较敏感，您可以选择忽略此步骤`**。
  - 3.2 当点击接受后
    - 3.2.1 如果收到呼叫时没有执行 **`[步骤3.1]`** ，那么需要在此处执行 **`[步骤3.1]`** 。
    - 3.2.2 开始订阅远端音频流。
  - 3.3 当收到主叫方的首帧后，即可确认连接成功。此时，您可以将之前创建的临时画布添加到可视化视图中，从而完成视频渲染的过程。
- 4.时序图
  <br><br><img src="https://fullapp.oss-cn-beijing.aliyuncs.com/scenario_api/callapi/diagram/200/sequence_solution_1v1.zh.png" width="500px"><br><br>

#### 7.1.2 提升消息送达率
- 增加消息回执(如果信令通道有则忽略)
- 增加超时重试(如果信令通道有则忽略)
- 选择送达率高的信令通道，例如声网RTM

### 7.2 影响通话速度的指标
- 主叫
  - 呼叫-被叫收到呼叫的耗时
  - 呼叫-收到被叫接受呼叫的耗时
  - 呼叫-被叫加入频道的耗时
  - 呼叫-主叫自己加入频道的耗时
  - 呼叫-收到被叫首帧的耗时
- 被叫
  - 收到呼叫-接受呼叫的耗时
  - 收到呼叫-被叫自己加入频道的耗时
  - 收到呼叫-主叫加入频道的耗时
  - 收到呼叫-收到主叫首帧的耗时