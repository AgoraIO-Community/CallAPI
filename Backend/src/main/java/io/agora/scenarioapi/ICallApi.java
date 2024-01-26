package io.agora.scenarioapi;

import io.agora.rtm.ResultCallback;

public interface ICallApi {
    /**
     * Initialize
     *
     * @param callApiConfig
     * @param resultCallbackLogin
     * @return
     */
    public Void initialize(CallApiConfig callApiConfig, ResultCallback<Void> resultCallbackLogin) throws Exception;

    /**
     * Deinitialize
     *
     * @param resultCallbackLogout
     * @return
     */
    public Void deinitialize(ResultCallback<Void> resultCallbackLogout);

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
    public Void call(Integer userIdA, Integer userIdB, String roomId, String callId,
                     ResultCallback<Void> resultCallbackUserIdA,
                     ResultCallback<Void> resultCallbackUserIdB);


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
    public Void hangup(Integer userIdA, String reasonA, Integer userIdB, String reasonB,
                       String roomId, String callId,
                       ResultCallback<Void> resultCallbackUserIdA,
                       ResultCallback<Void> resultCallbackUserIdB);

    /**
     * Renew token
     *
     * @param resultCallback
     * @return
     */
    public Void renewToken(String rtmToken, ResultCallback<Void> resultCallback);
}
