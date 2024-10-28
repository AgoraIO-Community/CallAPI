//
//  VideoScenarios.swift
//  CallAPI
//
//  Created by wushengtao on 2024/3/20.
//

import AgoraRtcKit

public func optimize1v1Video(engine: AgoraRtcEngineKit) {
    // 3. API to enable accelerated rendering of the first frame of audio and video
    // engine.enableInstantMediaRendering()
    
    // 4. Private parameters or configuration to enable first frame FEC
    engine.setParameters("{\"rtc.video.quickIntraHighFec\": true}")
    
    // 5. Private parameters or configuration to set AUT CC mode
    engine.setParameters("{\"rtc.network.e2e_cc_mode\": 3}")  // (No need to set this in versions 4.3.0 and later; the default value has been changed to 3)
    
    // 6. Private parameters or configuration to set the sensitivity for VQC resolution adjustment
    engine.setParameters("{\"che.video.min_holdtime_auto_resize_zoomin\": 1000}")
    engine.setParameters("{\"che.video.min_holdtime_auto_resize_zoomout\": 1000}")
}
