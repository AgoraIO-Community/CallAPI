//
//  VideoScenarios.swift
//  CallAPI
//
//  Created by wushengtao on 2024/3/20.
//

import AgoraRtcKit

public func optimize1v1Video(engine: AgoraRtcEngineKit) {
    // 3. API to enable accelerated rendering of the first frame of audio and video
    // 用于加速音视频首帧渲染的API
    // engine.enableInstantMediaRendering()
    
    // 4. Private parameters or configuration to enable first frame FEC
    // 开启首帧FEC的私有参数或配置
    engine.setParameters("{\"rtc.video.quickIntraHighFec\": true}")
    
    // 5. Private parameters or configuration to set AUT CC mode
    // 设置AUT CC模式的私有参数或配置
    engine.setParameters("{\"rtc.network.e2e_cc_mode\": 3}")  // (No need to set this in versions 4.3.0 and later; the default value has been changed to 3)
                                                             // (4.3.0及以上版本无需设置,默认值已改为3)
    
    // 6. Private parameters or configuration to set the sensitivity for VQC resolution adjustment
    // 设置VQC分辨率调整灵敏度的私有参数或配置
    engine.setParameters("{\"che.video.min_holdtime_auto_resize_zoomin\": 1000}")
    engine.setParameters("{\"che.video.min_holdtime_auto_resize_zoomout\": 1000}")
}
