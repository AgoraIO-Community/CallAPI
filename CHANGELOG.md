# Change Log

*English | [中文](CHANGELOG.zh.md)* 

## [2.1.3](https://github.com/AgoraIO-Community/CallAPI/releases/tag/2.1.3)

- Upgraded RTM SDK to version 4.5.0.
- Localization.
- Add camera and microphone toggles to the Android and iOS demos.

## [2.1.2](https://github.com/AgoraIO-Community/CallAPI/releases/tag/2.1.2)

- Call connected state support is independent of the audio and video first frame reception status.

## [2.1.1](https://github.com/AgoraIO-Community/CallAPI/releases/tag/2.1.1)

- Optimized RTC and RTM token acquisition, allowing it to be completed in a single request.
- [iOS] Resolved issues with Objective-C calls.
- [Android] Removed invalid properties.

## [2.1.0](https://github.com/AgoraIO-Community/CallAPI/releases/tag/2.1.0)

- Upgraded RTM SDK to version 2.2.0.
- Added voice call functionality.
- Supported sending custom extension information during calls.
- Added returned `CallStateReason` and `CallEvent` types during the calling state.
- Improved event reporting and log writing to enhance exception localization capabilities.

## [2.0.0](https://github.com/AgoraIO-Community/CallAPI/releases/tag/2.0.0)

- Added custom signaling functionality, allowing messages to be sent without using RTM.
- Optimized event reporting.
- Improved exception handling in the demo layer.

## [1.1.3](https://github.com/AgoraIO-Community/CallAPI/releases/tag/1.1.3)

- Upgraded RTM SDK to version 2.1.10.
- Fixed audio issues caused by directly calling the accept method when receiving a calling notification.
- Optimized canvas clearing logic.
- Closed local audio and video capture at the end of the call.

## [1.1.2](https://github.com/AgoraIO-Community/CallAPI/releases/tag/1.1.2)

- Optimized mirroring issues after beauty filter integration.
- Optimized capture logic closure.
- Returned reasons for call cancellations.
- Supported coexistence of external local capture display and internal CallApi display.

## [1.1.1](https://github.com/AgoraIO-Community/CallAPI/releases/tag/1.1.1)

- Added `canJoinRTC` method for external control of joining RTC timing.
- Added event type: when the other party cancels the call due to timeout, the event notification changes from `callingTimeout` to `remoteCallingTimeout`.

## [1.1.0](https://github.com/AgoraIO-Community/CallAPI/releases/tag/1.1.0)

- Added callbacks for call start/end.
- Added `eventReason` parameter to `onCallEventChanged` callback method.

## [1.0.0](https://github.com/AgoraIO-Community/CallAPI/releases/tag/1.0.0)

- Optimized API interface for improved usability.
- Adapted to RTM 2.1.8, using peer-to-peer messaging.
- Optimized log reporting strategy for more accurate call quality localization.
- Optimized expired message filtering strategy.
- Removed `autoAccept` configuration option.
- Added exception error callbacks.
- Added backend service module for matching gameplay.

## [0.3.1](https://github.com/AgoraIO-Community/CallAPI/releases/tag/0.3.1)

- Fixed timestamp retrieval exceptions.

## [0.3.0](https://github.com/AgoraIO-Community/CallAPI/releases/tag/0.3.0)

- Updated RTM SDK to version 2.1.7.
- `CallConfig` supports externally passing in `AgoraRtmClientKit` instances.
- In pure 1v1 mode, the `initialize` method implicitly calls `prepareForCall`.
- Added RTM disconnection callbacks.

## [0.2.2](https://github.com/AgoraIO-Community/CallAPI/releases/tag/0.2.2)

- Updated message sending and subscription's `channelName` from channel ID to user ID.
- Removed RTM Presence and related properties and callbacks.
- Updated some interface names.
- [Android] Callbacks synchronized to the main thread.
- Other bug fixes.

## [0.2.1](https://github.com/AgoraIO-Community/CallAPI/releases/tag/0.2.1)

- [Android] Updated RTM's Gradle download script.

## [0.2.0](https://github.com/AgoraIO-Community/CallAPI/releases/tag/0.2.0)

- Added RTC callback interfaces.
- Updated RTM to an independent version.
- Optimized event reporting.
- Bug fixes.

## [0.1.0](https://github.com/AgoraIO-Community/CallAPI/releases/tag/0.1.0)

