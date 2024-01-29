package io.agora.scenarioapi.sample;

import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import io.agora.rtm.ErrorInfo;
import io.agora.rtm.ResultCallback;
import io.agora.rtm.RtmConstants;
import io.agora.rtm.RtmEventListener;
import io.agora.scenarioapi.CallApiConfig;
import io.agora.scenarioapi.CallApiImpl;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CallApiSample {
    // 声网 App Id
    static String appId = "";
    // 房间Id
    static String roomId = "room_id_test";
    // 根据 userId 生成 RTM token
    static String rtmToken = "";
    // 呼叫用户Id
    static Integer userIdA = 4111;
    static Integer userIdB = 4222;
    // 用户Id, 通过该用户Id来发送信令消息
    static String userId = "test_user_id";

    public static void main(String[] args) throws Exception {
        // 创建实例
        CallApiImpl callApiImpl = new CallApiImpl();

        // 设置事件监听
        RtmEventListener rtmEventListener = new RtmEventListener() {
            // 连接状态变化回调
            @Override
            public void onConnectionStateChanged(String channelName, RtmConstants.RtmConnectionState state,
                                                 RtmConstants.RtmConnectionChangeReason reason) {
                log.info("onConnectionStateChanged, channelName:{}, state:{}, reason:{}", channelName, state,
                        reason);
            }

            // RTM token即将过期回调
            @Override
            public void onTokenPrivilegeWillExpire(String channelName) {
                log.info("onTokenPrivilegeWillExpire, channelName:{}", channelName);

                // Renew token
                while (true) {
                    log.info("onTokenPrivilegeWillExpire, renewToken");

                    CountDownLatch latch = new CountDownLatch(1);
                    AtomicBoolean res = new AtomicBoolean(false);

                    callApiImpl.renewToken(rtmToken, new ResultCallback<Void>() {
                        @Override
                        public void onSuccess(Void responseInfo) {
                            log.info("onTokenPrivilegeWillExpire, renewToken, onSuccess");

                            res.set(true);
                            latch.countDown();
                        }

                        @Override
                        public void onFailure(ErrorInfo errorInfo) {
                            log.error("onTokenPrivilegeWillExpire, renewToken, onFailure");

                            latch.countDown();
                        }
                    });

                    if (res.get()) {
                        log.info("onTokenPrivilegeWillExpire, break");
                        break;
                    }
                }
            }
        };

        // 设置参数
        CallApiConfig callApiConfig = new CallApiConfig();
        callApiConfig.setAppId(appId);
        callApiConfig.setRtmEventListener(rtmEventListener);
        callApiConfig.setRtmToken(rtmToken);
        callApiConfig.setUserId(userId);
        var fromUserExtension = new HashMap<String, Object>();
        fromUserExtension.put("videoType", "match");
        callApiConfig.setFromUserExtension(fromUserExtension);

        // 初始化
        try {
            callApiImpl.initialize(callApiConfig, new ResultCallback<Void>() {
                @Override
                public void onSuccess(Void responseInfo) {
                    log.info("initialize, onSuccess, responseInfo:{}", responseInfo);
                }

                @Override
                public void onFailure(ErrorInfo errorInfo) {
                    log.error("initialize, onFailure, errorInfo:{}", errorInfo);
                }
            });
        } catch (Exception e) {
            log.error("initialize, error:{}", e.getMessage());
        }

        // 多次调用
        for (int i = 0; i < 10; i++) {
            log.info("call, i:{}", i);

            String callId = UUID.randomUUID().toString();
            // 调用呼叫接口
            callApiImpl.call(userIdA, userIdB, roomId, callId, new ResultCallback<>() {
                @Override
                public void onSuccess(Void responseInfo) {
                    log.info("call, userIdA, onSuccess, responseInfo:{}", responseInfo);
                }

                @Override
                public void onFailure(ErrorInfo errorInfo) {
                    log.error("call, userIdA, onFailure, errorInfo:{}", errorInfo);
                }
            }, new ResultCallback<>() {
                @Override
                public void onSuccess(Void responseInfo) {
                    log.info("call, userIdB, onSuccess, responseInfo:{}", responseInfo);
                }

                @Override
                public void onFailure(ErrorInfo errorInfo) {
                    log.error("call, userIdB, onFailure, errorInfo:{}", errorInfo);
                }
            });

            // 等待10s
            TimeUnit.SECONDS.sleep(10);
        }

        // 等待5s
        TimeUnit.SECONDS.sleep(5);

        // 销毁实例
        callApiImpl.deinitialize(new ResultCallback<>() {
            @Override
            public void onSuccess(Void responseInfo) {
                log.info("deinitialize, onSuccess, responseInfo:{}", responseInfo);
            }

            @Override
            public void onFailure(ErrorInfo errorInfo) {
                log.error("deinitialize, onFailure, errorInfo:{}", errorInfo);
            }
        });
    }
}
