---
name: "ProtocolLib 协议库"
description: "ProtocolLib 协议库插件的使用指南"
triggers:
  - "protocollib"
  - "protocol"
  - "协议"
  - "数据包"
auto_trigger: true
author: "FancyHelper Team"
version: "1.0.0"
categories:
  - "plugin"
  - "protocol"
  - "api"
---

# ProtocolLib

## 插件检查

在使用以下命令前，请先检查服务器是否已安装 ProtocolLib 插件。

检查方法：尝试执行 `/protocol version` 命令，如果返回版本信息则说明插件已安装。

如果插件未安装，请告知玩家无法使用数据包修改功能。

## 常用命令

- `/protocol version` - 查看插件版本
- `/protocol clients` - 查看当前连接的客户端
- `/protocol clients <name>` - 查看特定客户端信息
- `/protocol packets [in|out]` - 查看当前发送/接收的数据包
- `/protocol info <packet>` - 查看数据包详细信息
- `/protocol filter <packet> <add|remove> <type>` - 添加/移除数据包过滤器

## 注意

ProtocolLib 是一个底层协议库，用于拦截和修改 Minecraft 网络数据包。通常作为其他插件的依赖使用，普通玩家很少直接使用。
