package io.agora.scenarioapi.sample;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import io.agora.rtm.ErrorInfo;
import io.agora.rtm.ResultCallback;
import io.agora.rtm.RtmClient;
import io.agora.rtm.RtmConfig;
import io.agora.rtm.RtmConstants;
import io.agora.rtm.RtmEventListener;
import io.agora.scenarioapi.CallApiConfig;
import io.agora.scenarioapi.CallApiImpl;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CallApiRtmClientSample {
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
                log.info("onConnectionStateChanged, channelName:{}, state:{}, reason:{}", channelName, state, reason);
            }

            // RTM token即将过期回调
            @Override
            public void onTokenPrivilegeWillExpire(String channelName) {
                log.info("onTokenPrivilegeWillExpire, channelName:{}", channelName);

                // Renew token
                while (true) {
                    CountDownLatch latch = new CountDownLatch(1);
                    AtomicBoolean res = new AtomicBoolean(false);
                    callApiImpl.renewToken(rtmToken, new ResultCallback<>() {
                        @Override
                        public void onSuccess(Void responseInfo) {
                            res.set(true);
                            latch.countDown();
                        }

                        @Override
                        public void onFailure(ErrorInfo errorInfo) {
                            latch.countDown();
                        }
                    });

                    if (res.get()) {
                        break;
                    }
                }
            }
        };

        // 设置参数
        CallApiConfig callApiConfig = new CallApiConfig();
        callApiConfig.setAppId(appId);
        // 传入自定义的rtmClient, 需要自行处理login/logout
        callApiConfig.setRtmClient(getRtmClient());
        callApiConfig.setRtmEventListener(rtmEventListener);
        callApiConfig.setRtmToken(rtmToken);
        callApiConfig.setUserId(userId);
        String callId = UUID.randomUUID().toString();

        // 初始化
        callApiImpl.initialize(callApiConfig, new ResultCallback<>() {
            @Override
            public void onSuccess(Void responseInfo) {
                log.info("initialize, onSuccess, responseInfo:{}", responseInfo);
            }

            @Override
            public void onFailure(ErrorInfo errorInfo) {
                log.error("initialize, onFailure, errorInfo:{}", errorInfo);
            }
        });

        // 调用呼叫接口
        callApiImpl.call(userIdA, userIdB, roomId, callId,
                new ResultCallback<>() {
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

        // 等待5s
        TimeUnit.SECONDS.sleep(10);


        // 调用挂断接口
        callApiImpl.hangup(userIdA, "reasonA", userIdB, "reasonB", roomId, callId,
                new ResultCallback<>() {
                    @Override
                    public void onSuccess(Void responseInfo) {
                        log.info("hangup, userIdA, onSuccess, responseInfo:{}", responseInfo);
                    }

                    @Override
                    public void onFailure(ErrorInfo errorInfo) {
                        log.error("hangup, userIdA, onFailure, errorInfo:{}", errorInfo);
                    }
                }, new ResultCallback<>() {
                    @Override
                    public void onSuccess(Void responseInfo) {
                        log.info("hangup, userIdB, onSuccess, responseInfo:{}", responseInfo);
                    }

                    @Override
                    public void onFailure(ErrorInfo errorInfo) {
                        log.error("hangup, userIdB, onFailure, errorInfo:{}", errorInfo);
                    }
                });


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

    public static RtmClient getRtmClient() {
        RtmConfig rtmConfig = new RtmConfig.Builder(appId, userId)
                .build();

        RtmClient rtmClient = null;
        try {
            rtmClient = RtmClient.create(rtmConfig);
        } catch (Exception e) {
            log.error("getRtmClient, error:{}, appId:{}, userId:{}", e.getMessage(), appId, userId);
            return null;
        }

        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean res = new AtomicBoolean(false);
        rtmClient.login(rtmToken, new ResultCallback<Void>() {
            @Override
            public void onSuccess(Void responseInfo) {
                res.set(true);
                latch.countDown();
            }

            @Override
            public void onFailure(ErrorInfo errorInfo) {
                log.error("getRtmClient, rtm login failed, error:{}, appId:{}", errorInfo, appId);
                latch.countDown();
            }
        });

        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            log.error("getRtmClient, login, await failed, error:{}, appId:{}", e, appId);
            return null;
        }

        return rtmClient;
    }
}
