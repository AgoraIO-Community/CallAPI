//
//  UIDevice+CallAPI.swift
//  CallAPI_Example
//
//  Created by wushengtao on 2023/6/1.
//  Copyright © 2023 Agora. All rights reserved.
//

import UIKit

public func getWindow()-> UIWindow? {
    if #available(iOS 13.0, *) {
        return UIApplication.shared.connectedScenes
        // Keep only active scenes, onscreen and visible to the user
            .filter { $0.activationState == .foregroundActive }
        // Keep only the first `UIWindowScene`
            .first(where: { $0 is UIWindowScene })
        // Get its associated windows
            .flatMap({ $0 as? UIWindowScene })?.windows
        // Finally, keep only the key window
            .first(where: \.isKeyWindow)
    } else {
        return UIApplication.shared.keyWindow
    }
}

extension UIDevice {
    public var safeDistanceTop: CGFloat {
        let window = getWindow()
        
        return window?.safeAreaInsets.top ?? 0
    }
    
    public var safeDistanceBottom: CGFloat {
        let window = getWindow()
        
        return window?.safeAreaInsets.bottom ?? 0
    }
}
