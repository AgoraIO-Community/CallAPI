//
//  KeyCenter.swift
//  OpenLive
//
//  Created by GongYuhua on 6/25/16.
//  Copyright © 2016 Agora. All rights reserved.
//

struct KeyCenter {
    
    /**
     Agora APP ID.
     Agora assigns App IDs to app developers to identify projects and organizations.
     If you have multiple completely separate apps in your organization, for example built by different teams,
     you should use different App IDs.
     If applications need to communicate with each other, they should use the same App ID.
     In order to get the APP ID, you can open the agora console (https://console.agora.io/) to create a project,
     then the APP ID can be found in the project detail page.
     声网APP ID
     Agora 给应用程序开发人员分配 App ID，以识别项目和组织。如果组织中有多个完全分开的应用程序，例如由不同的团队构建，
     则应使用不同的 App ID。如果应用程序需要相互通信，则应使用同一个App ID。
     进入声网控制台(https://console.shengwang.cn/)，创建一个项目，进入项目配置页，即可看到APP ID。
     */
    static let AppId: String = "925dd81d763a42919862fee9f3f204a7"
    
    /**
     Certificate.
     Agora provides App certificate to generate Token. You can deploy and generate a token on your server,
     or use the console to generate a temporary token.
     In order to get the APP ID, you can open the agora console (https://console.agora.io/) to create a project with the App Certificate enabled,
     then the APP Certificate can be found in the project detail page.
     PS: If the project does not have certificates enabled, leave this field blank.
     声网APP证书
     Agora 提供 App certificate 用以生成 Token。您可以在您的服务器部署并生成，或者使用控制台生成临时的 Token。
     进入声网控制台(https://console.shengwang.cn/)，创建一个带证书鉴权的项目，进入项目配置页，即可看到APP证书。
     注意：如果项目没有开启证书鉴权，这个字段留空。
     */
    
    static let Certificate: String? = "69fbf5bbd8594fa0a6348798eeae35d0"
    
    /**
     Easemob APPKEY
     The application name entered when creating an application in the Easemob Instant Messaging Cloud Console.
     If you need to use Easemob's custom signaling scenarios, this parameter must be set. If you only want to experience the default RTM signaling, you can fill it in with "".
     For more details, see how to obtain information about Easemob Instant Messaging (https://docs.agora.io/en/agora-chat/get-started/enable?platform=ios).
     环信APPKEY
     在环信即时通讯云控制台创建应用时填入的应用名称。
     如需使用环信自定义信令场景，需要设置该参数，如只需要体验默认的Rtm信令，可以填写 ""。
     详见获取环信即时通讯IM的信息(http://docs-im-beta.easemob.com/product/enable_and_configure_IM.html#%E8%8E%B7%E5%8F%96%E7%8E%AF%E4%BF%A1%E5%8D%B3%E6%97%B6%E9%80%9A%E8%AE%AF-im-%E7%9A%84%E4%BF%A1%E6%81%AF)。
     */
    
    static var IMAppKey: String = "1129210531094378#auikit-voiceroom"
}
