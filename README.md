# CallAPI

*English | [中文](README_zh.md)*

## Overview

CallAPI is a scenario based API solution for one-on-one, one second open design of the Agora, It can provide developers with an extremely fast and smooth switching experience in live streaming scenarios.

## Scenario Description

### Pure 1v1

#### Role Description
Pure 1v1 scenarios do not require specifying roles, all roles can be called and called

### Live to 1v1

#### Role Description
| Role       | Description                 |
|------------|-----------------------------|
| caller | Audiences in the live broadcast room can be called to enter the 1v1 room.  |
| callee | The creator of the live room can only accept calls from the caller to enter the 1v1 room. |



#### The core functions provided by the CallAPI：
- **Call**: The caller initiates the call.
- **Cancel Call**: After the caller initiates the call, they can initiate a cancellation call before the call is successful to interrupt the current call.
- **Accept**: The called person can accept the current call after receiving the calling request from the caller.
- **Reject**: The called can reject the current call after receiving the calling request from the caller.
- **Hangup**: The caller/callee can initiate a hang up request to interrupt this call while in progress.



## Demo

| iOS                                                          | Android                                                      |
| ------------------------------------------------------------ | ------------------------------------------------------------ |
| ![](https://fullapp.oss-cn-beijing.aliyuncs.com/scenario_api/1v1_qrcode_ios.png?x-oss-process=image/resize,w_200) | ![](https://fullapp.oss-cn-beijing.aliyuncs.com/scenario_api/1v1_qrcode_android.png?x-oss-process=image/resize,w_200) |

## Quick Start

| Platform     | Example                   |
|---------|------------------------|
| Android | [CallAPI(Android)](Android) |
| iOS     | [CallAPI(iOS)](iOS)   |


---


### How to Contact Agora for Support

> 1.Send an email to support@agora.io for consultation when you encounter integration difficulties.
> 2.Add issue
 

---
