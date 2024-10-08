//
//  VideoScenarios.swift
//  CallAPI
//
//  Created by wushengtao on 2024/3/20.
//

import AgoraRtcKit

public func optimize1v1Video(engine: AgoraRtcEngineKit) {
    //3.API 开启音视频首帧加速渲染
//    engine.enableInstantMediaRendering()
    
    // 4.私有参数或配置下发开启首帧 FEC
    engine.setParameters("{\"rtc.video.quickIntraHighFec\": true}")
    
    //5.私有参数或配置下发设置 AUT CC mode
    engine.setParameters("{\"rtc.network.e2e_cc_mode\": 3}")  //(4.3.0及以后版本不需要设置此项，默认值已改为3)
    
    //6.私有参数或配置下发设置VQC分辨率调节的灵敏度
    engine.setParameters("{\"che.video.min_holdtime_auto_resize_zoomin\": 1000}")
    engine.setParameters("{\"che.video.min_holdtime_auto_resize_zoomout\": 1000}")
}
