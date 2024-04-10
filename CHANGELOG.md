# Change Log

## [2.0.0](https://github.com/AgoraIO-Community/CallAPI/releases/tag/2.0.0)

- 新增自定义信令功能，支持使用非 Rtm 来发送消息。
- 优化事件上报
- 优化 Demo 层的异常处理。


## [1.1.3](https://github.com/AgoraIO-Community/CallAPI/releases/tag/1.1.3)

- 升级 Rtm SDK 至 2.1.10。
- 修复收到 calling 时直接调用 accpet 方法建立通话导致的音频异常问题。
- 优化画布清理逻辑。
  
## [1.1.2](https://github.com/AgoraIO-Community/CallAPI/releases/tag/1.1.2)

- 优化美颜接入后镜像问题。
- 关闭采集逻辑优化。
- 呼叫取消返回原因。
- 支持外部本地采集画面展示与CallApi内部展示共存。

## [1.1.1](https://github.com/AgoraIO-Community/CallAPI/releases/tag/1.1.1)

- 新增 canJoinRTC 方法，用于外部控制加入 RTC 的时机。
- 新增事件类型，当对端因为超时取消呼叫时事件通知从 callingTimeout 变更为 remoteCallingTimeout

## [1.1.0](https://github.com/AgoraIO-Community/CallAPI/releases/tag/1.1.0)

- 增加通话开始/结束的回调。
- onCallEventChanged 回调方法增加 eventReason 参数。

## [1.0.0](https://github.com/AgoraIO-Community/CallAPI/releases/tag/1.0.0)

- 优化API接口，增加易用性。
- 适配 RTM 2.1.8，使用点对点消息。
- 优化日志上报策略，通话质量定位更准确。
- 优化过期消息过滤策略。
- 移除 autoAccept 配置项。
- 新增异常错误回调
- 新增匹配玩法的后端服务模块

## [0.3.1](https://github.com/AgoraIO-Community/CallAPI/releases/tag/0.3.1)

- 修复时间戳获取异常。

## [0.3.0](https://github.com/AgoraIO-Community/CallAPI/releases/tag/0.3.0)

- 更新 RTM SDK 至2.1.7。
- CallConfig 支持外部传入 AgoraRtmClientKit 实例。
- 纯1v1模式下 initialize 方法隐式调用 prepareForCall。
- 增加 RTM 断连回调。

## [0.2.2](https://github.com/AgoraIO-Community/CallAPI/releases/tag/0.2.2)

- 更新消息发送和订阅的 channelName 从频道id改为用户id。
- 移除 RTM Presence 及接口相关属性和回调。
- 更新部分接口名称。
- [Android]回调同步到主线程。
- 其他Bug修复。

## [0.2.1](https://github.com/AgoraIO-Community/CallAPI/releases/tag/0.2.1)

- [Android]更新RTM的 gradle 下载脚本。

## [0.2.0](https://github.com/AgoraIO-Community/CallAPI/releases/tag/0.2.0)

- 增加 RTC 回调接口。
- 更新 RTM 为独立版本。
- 优化事件上报。
- Bug修复。

## [0.1.0](https://github.com/AgoraIO-Community/CallAPI/releases/tag/0.1.0)


