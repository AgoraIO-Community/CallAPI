//
//  CallBaseSignalClient.swift
//  CallAPI
//
//  Created by wushengtao on 2024/2/28.
//

import Foundation

@objcMembers open class CallBaseSignalClient: NSObject {
    public let delegates:NSHashTable<ISignalClientListener> = NSHashTable<ISignalClientListener>.weakObjects()
    
    @objc public func addListener(listener: ISignalClientListener) {
        if delegates.contains(listener) { return }
        delegates.add(listener)
    }
    
    @objc public func removeListener(listener: ISignalClientListener) {
        delegates.remove(listener)
    }
}
