package io.agora.scenarioapi;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import com.alibaba.fastjson2.JSON;

import io.agora.rtm.PublishOptions;
import io.agora.rtm.ResultCallback;
import io.agora.rtm.RtmClient;
import io.agora.rtm.RtmConfig;
import io.agora.rtm.RtmConstants.RtmChannelType;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CallApiImpl implements ICallApi {
    private CallApiConfig callApiConfig;
    private AtomicBoolean isExternalRtmClient = new AtomicBoolean(false);
    private AtomicBoolean isRtmLogined = new AtomicBoolean(false);

    /**
     * Initialize
     *
     * @param callApiConfig
     * @param resultCallbackLogin
     * @return
     */
    @Override
    public Void initialize(CallApiConfig callApiConfig, ResultCallback<Void> resultCallbackLogin) throws Exception {
        if (callApiConfig.getRtmClient() == null) {
            RtmClient rtmClient = _createClient(callApiConfig);
            callApiConfig.setRtmClient(rtmClient);

            _login(callApiConfig, resultCallbackLogin);
        } else {
            isExternalRtmClient.set(true);
        }

        this.callApiConfig = callApiConfig;
        return null;
    }

    /**
     * Deinitialize
     *
     * @param resultCallback
     * @return
     */
    @Override
    public Void deinitialize(ResultCallback<Void> resultCallbackLogout) {
        if (!isExternalRtmClient.get()) {
            _logout(resultCallbackLogout);
        }

        isRtmLogined.set(false);
        isExternalRtmClient.set(false);
        return null;
    }

    /**
     * Call
     *
     * @param userIdA
     * @param userIdB
     * @param roomId
     * @param callId
     * @param resultCallbackUserIdA
     * @param resultCallbackUserIdB
     * @return
     */
    @Override
    public Void call(Integer userIdA, Integer userIdB, String roomId, String callId,
                     ResultCallback<Void> resultCallbackUserIdA,
                     ResultCallback<Void> resultCallbackUserIdB) {
        log.info("call, start, userIdA:{}, userIdB:{}, roomId:{}", userIdA, userIdB, roomId);

        Integer messageAction = 0;
        String messageVersion = "1.0";
        long messageTimestamp = System.currentTimeMillis();

        Map<String, Object> messageMap = new HashMap<>();
        messageMap.put("message_action", messageAction);
        messageMap.put("message_version", messageVersion);
        messageMap.put("message_timestamp", messageTimestamp);
        messageMap.put("fromUserId", userIdA);
        messageMap.put("remoteUserId", userIdB);
        messageMap.put("fromRoomId", roomId);
        messageMap.put("callId", callId);
        if (callApiConfig.getFromUserExtension() != null) {
            messageMap.put("fromUserExtension", callApiConfig.getFromUserExtension());
        }

        byte[] jsonByte = JSON.toJSONBytes(messageMap);

        _publish(userIdB.toString(), jsonByte, resultCallbackUserIdA);

        messageMap.put("fromUserId", userIdB);
        messageMap.put("remoteUserId", userIdA);
        jsonByte = JSON.toJSONBytes(messageMap);

        _publish(userIdA.toString(), jsonByte, resultCallbackUserIdB);

        log.info("call, end, userIdA:{}, userIdB:{}, roomId:{}", userIdA, userIdB, roomId);
        return null;
    }


    /**
     * hangup
     *
     * @param userIdA
     * @param reasonA
     * @param userIdB
     * @param reasonB
     * @param roomId
     * @param callId
     * @param resultCallbackUserIdA
     * @param resultCallbackUserIdB
     * @return
     */
    @Override
    public Void hangup(Integer userIdA, String reasonA, Integer userIdB, String reasonB, String roomId, String callId,
                       ResultCallback<Void> resultCallbackUserIdA,
                       ResultCallback<Void> resultCallbackUserIdB) {
        Integer messageAction = 4;  //挂断的命令
        String messageVersion = "1.0";
        long messageTimestamp = System.currentTimeMillis();

        Map<String, Object> messageMap = new HashMap<>();
        messageMap.put("message_action", messageAction);
        messageMap.put("message_version", messageVersion);
        messageMap.put("message_timestamp", messageTimestamp);
        messageMap.put("fromUserId", userIdA);
        messageMap.put("fromRoomId", roomId);
        messageMap.put("callId", callId);
        messageMap.put("hangupReason", reasonB);
        if (callApiConfig.getFromUserExtension() != null) {
            messageMap.put("fromUserExtension", callApiConfig.getFromUserExtension());
        }

        byte[] jsonByte = JSON.toJSONBytes(messageMap);

        _publish(userIdB.toString(), jsonByte, resultCallbackUserIdA);

        messageMap.put("fromUserId", userIdB);
        messageMap.put("hangupReason", reasonA);
        jsonByte = JSON.toJSONBytes(messageMap);

        _publish(userIdA.toString(), jsonByte, resultCallbackUserIdB);

        log.info("hangup, end, userIdA:{}, userIdB:{}, roomId:{}, callId:{}", userIdA, userIdB, roomId, callId);

        return null;
    }

    /**
     * Create client
     *
     * @param callApiConfig
     * @return
     */
    private RtmClient _createClient(CallApiConfig callApiConfig) throws Exception {
        RtmConfig rtmConfig = new RtmConfig.Builder(callApiConfig.getAppId(), callApiConfig.getUserId())
                .eventListener(callApiConfig.getRtmEventListener())
                .build();

        return RtmClient.create(rtmConfig);
    }

    /**
     * Login
     *
     * @param callApiConfig
     * @param resultCallback
     * @return
     */
    private Void _login(CallApiConfig callApiConfig, ResultCallback<Void> resultCallback) {
        if (isRtmLogined.get()) {
            resultCallback.onSuccess(null);
            return null;
        }

        callApiConfig.getRtmClient().login(callApiConfig.getRtmToken(), resultCallback);
        return null;
    }

    /**
     * Logout
     *
     * @param resultCallback
     * @return
     */
    private Void _logout(ResultCallback<Void> resultCallback) {
        callApiConfig.getRtmClient().logout(resultCallback);
        return null;
    }

    /**
     * Publish
     *
     * @param userId
     * @param message
     * @param resultCallback
     * @return
     */
    private Void _publish(String userId, byte[] message, ResultCallback<Void> resultCallback) {
        PublishOptions options = new PublishOptions();
        options.setChannelType(RtmChannelType.USER);

        callApiConfig.getRtmClient().publish(userId, message, options, resultCallback);
        return null;
    }

    /**
     * Renew token
     *
     * @param resultCallback
     * @return
     */
    @Override
    public Void renewToken(String rtmToken, ResultCallback<Void> resultCallback) {
        callApiConfig.getRtmClient().renewToken(rtmToken, resultCallback);
        return null;
    }
}
