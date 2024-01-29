package io.agora.scenarioapi;

import io.agora.rtm.RtmClient;
import io.agora.rtm.RtmEventListener;
import lombok.Data;

import java.util.Map;

@Data
public class CallApiConfig {
    // 声网App Id
    private String appId;
    // [可选]rtm client实例, 如果设置则需要负责rtmClient的login和logout, 需要使用appId和userId创建
    private RtmClient rtmClient;
    // 事件监听
    private RtmEventListener rtmEventListener;
    // RTM token
    private String rtmToken;
    // 用户Id, 通过该用户Id来发送信令消息
    private String userId;
    // [可选]用户自定义的扩展信息, 会在信令消息中携带
    private Map<String, Object> fromUserExtension;
}
