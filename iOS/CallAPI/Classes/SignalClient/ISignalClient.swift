//
//  ISignalClient.swift
//  CallAPI
//
//  Created by wushengtao on 2024/2/27.
//

import Foundation

/// Signaling callback protocol
@objc public protocol ISignalClientListener: NSObjectProtocol {

    /// Callback for receiving messages
    /// - Parameter message: Content of the received message
    func onMessageReceive(message: String)
    
    /// Signaling log callback
    /// - Parameters:
    ///   - message: Content of the log message
    ///   - logLevel: Log priority level
    @objc optional func debugInfo(message: String, logLevel: Int)
}

/// Signaling abstract protocol
@objc public protocol ISignalClient: NSObjectProtocol {
    
    /// CallApi uses this method to send messages to a specified user through the signaling system
    /// - Parameters:
    ///   - userId: Target user's ID
    ///   - message: Message object
    ///   - completion: Callback upon completion of sending
    func sendMessage(userId: String,
                     message: String,
                     completion: ((NSError?)-> Void)?)
    
    /// Adds a signaling listener to the signaling system
    /// - Parameter listener: Signaling listener for handling message reception and logging
    func addListener(listener: ISignalClientListener)
    
    /// Removes the specified signaling listener from the signaling system
    /// - Parameter listener: Signaling listener to be removed
    func removeListener(listener: ISignalClientListener)
}
