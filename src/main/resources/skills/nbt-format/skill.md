---
name: "nbt-format"
description: "Minecraft NBT 和数据组件格式的完整指南，基于官方 Wiki 编写"
triggers:
  - "nbt"
  - "组件"
  - "data"
  - "component"
  - "item"
  - "format"
auto_trigger: true
author: "FancyHelper Team"
version: "1.0.0"
categories:
  - "vanilla"
  - "technical"
  - "item"
---

# 用 NBT 格式看我

## 基本概念

Minecraft 在 **1.20.5 版本**对物品数据进行了重大重构，从旧的 `{NBT}` 格式迁移到了新的 `[数据组件]` 格式。

### 数据组件是什么

数据组件（Data components）是用于存储信息和定义行为的结构化数据。它们的ID是命名空间标识符，值可以是任何数据类型。

当用于物品时，它们被称为物品组件或物品栈组件。它们可以存在于任何存储物品的地方，如玩家的物品栏、容器方块实体和结构文件。

### 命令格式

在使用 `/give` 等命令时，物品表示格式为：
```
item_id[component1=value,component2=value]
```

其中 component 是组件的命名空间ID，value 是以SNBT格式编写的组件值。

可以通过在组件前加上感叹号来移除组件，如 `item_id[!component3]`。

任何未指定的组件都会隐式设置为该物品类型的组件默认值。如果没有指定组件，可以省略方括号，只保留物品ID。

## 版本区分

- **1.20.5 及以后版本**：使用 `[数据组件]` 格式
  - 例如：`stone[custom_name='{"text":"石头"}']`

- **1.20.4 及以前版本**：使用 `{NBT}` 格式
  - 例如：`stone{display:{Name:'{"text":"石头"}'}}`

### 判断服务器版本

执行 `/version` 命令查看服务器版本，根据版本选择正确的格式。

## 常用数据组件列表

### 基础显示组件

#### custom_name - 自定义名称
- 格式：`[custom_name='{"text":"名称"}']`
- 说明：指定物品、方块或实体的自定义名称
- 示例：`/give @s stick[custom_name='{"text":"Magic Wand","color":"light_purple","italic":false}']`

#### lore - 物品叙述
- 格式：`[lore=['"第一行"','"第二行"']]`
- 说明：物品的多行说明

#### hide_additional_tooltip - 隐藏附加提示
- 格式：`[hide_additional_tooltip={}]`
- 说明：隐藏附魔、属性修饰符等信息

#### hide_tooltip - 完全隐藏提示
- 格式：`[hide_tooltip={}]`
- 说明：鼠标悬停时不显示任何信息

### 耐久与损坏

#### unbreakable - 无法破坏
- 格式：`[unbreakable={}]`

#### damage - 当前损坏值
- 格式：`[damage=500]`
- 说明：物品耐久度已消耗的使用次数（非剩余次数），必须是非负整数，默认为0

#### max_damage - 最大耐久
- 格式：`[max_damage=2000]`

#### max_stack_size - 最大堆叠数量
- 格式：`[max_stack_size=1]`

### 附魔相关

#### enchantments - 附魔
- 格式：`[enchantments={sharpness:3,knockback:2}]`
- 说明：包含物品每个附魔及其附魔等级的映射
- 示例：`/give @s wooden_sword[enchantments={sharpness:3,knockback:2}]`

#### stored_enchantments - 存储的附魔
- 格式：`[stored_enchantments={sharpness:3}]`
- 说明：用于附魔书等物品的非活动附魔

#### enchantable - 可附魔
- 格式：`[enchantable={value:15}]`
- 说明：物品的附魔能力值，值越高允许选择成本更高的附魔

#### enchantment_glint_override - 附魔光效覆盖
- 格式：`[enchantment_glint_override=false]`
- 说明：覆盖物品的附魔光效，true显示，false隐藏

### 属性修饰符

#### attribute_modifiers - 属性修饰符
- 格式：`[attribute_modifiers=[{type:"minecraft:scale",slot:"hand",id:"example:grow",amount:4,operation:"add_multiplied_base"}]]`
- 说明：属性修饰符列表，如果物品存在，装备该物品的玩家或生物可能会应用这些修饰符
- 示例：`/give @s stick[attribute_modifiers=[{type:"minecraft:scale",slot:"hand",id:"example:grow",amount:4,operation:"add_multiplied_base"}]]`

字段说明：
- `id`: 命名空间ID，用于识别此修饰符，必须与同一属性的其他修饰符唯一
- `type`: 此修饰符要作用的属性的命名空间ID
- `slot`: 物品必须在哪个装备槽位才能生效，可以是 any, hand, armor, mainhand, offhand, head, chest, legs, feet, body, saddle，默认为 any
- `operation`: 修饰符操作，可以是 add_value, add_multiplied_base, add_multiplied_total
- `amount`: 修饰符操作中使用的值
- `display`: 修饰符在物品提示中的显示方式（可选）

### 药水与效果

#### potion_contents - 药水内容
- 格式：`[potion_contents={...}]`

### 食物相关

#### food - 食物属性
- 格式：`[food={nutrition:3,saturation:1,can_always_eat:true}]`
- 说明：食用此物品时应用于生物或玩家的食物统计
- 示例：`/give @s melon_slice[food={nutrition:3,saturation:1,can_always_eat:true}]`

字段：
- `nutrition`: 食用时恢复的食物点数
- `saturation`: 食用时恢复的饱和度
- `can_always_eat`: 如果为true，即使玩家不饿也可以食用

#### consumable - 消耗品属性
- 格式：`[consumable={consume_seconds:1.6,animation:"eat",sound:"minecraft:entity.generic.eat",has_consume_particles:true}]`
- 说明：物品可以被消耗

字段：
- `consume_seconds`: 玩家消耗物品所需的秒数，默认为1.6
- `animation`: 消耗物品时使用的动画，必须是 none, eat, drink, block, bow, spear, crossbow, spyglass, toot_horn, brush, bundle, trident 之一，默认为 eat
- `sound`: 物品消耗期间和完成时使用的声音事件
- `has_consume_particles`: 消耗此物品时是否发出消耗粒子，默认为 true
- `on_consume_effects`: 消耗此物品产生的效果列表（可选）

### 工具与武器

#### tool - 工具属性
- 格式：`[tool={...}]`

#### trim - 盔甲纹饰
- 格式：`[trim={pattern:"minecraft:sentry",material:"minecraft:diamond"}]`

### 烟花与烟火

#### fireworks - 烟花火箭
- 格式：`[fireworks={flight_duration:2,explosions:[...]}]`

字段：
- `flight_duration`: 烟花火箭的飞行持续时间（和制作它使用的火药数量），必须是-128到127之间的整数，默认为1
- `explosions`: 烟花火箭造成的爆炸效果列表，最多256个爆炸

#### firework_explosion - 烟花爆炸效果
- 格式：`[firework_explosion={shape:"small_ball",colors:[...],fade_colors:[...],has_trail:false,has_twinkle:false}]`

字段：
- `shape`: 爆炸的形状，可以是 small_ball, large_ball, star, creeper, burst
- `colors`: 爆炸初始粒子的颜色，从中随机选择
- `fade_colors`: 爆炸渐隐粒子的颜色，从中随机选择
- `has_trail`: 爆炸是否有拖尾效果（钻石）
- `has_twinkle`: 爆炸是否有闪烁效果（萤石粉）

### 特殊物品

#### bundle_contents - 收纳袋内容
- 格式：`[bundle_contents=[{id:"diamond",count:2}]]`
- 说明：存储在此收纳袋中的物品
- 示例：`/give @s bundle[bundle_contents=[{id:"diamond",count:2}]]`

#### charged_projectiles - 弩箭装填
- 格式：`[charged_projectiles=[{id:"spectral_arrow"}]]`
- 说明：作为弹射物加载到此弩中的物品
- 示例：`/give @s crossbow[charged_projectiles=[{id:"spectral_arrow"}]]`

#### written_book_content - 成书内容
- 格式：`[written_book_content={...}]`

#### writable_book_content - 书与笔内容
- 格式：`[writable_book_content={...}]`

#### map_color - 地图颜色
- 格式：`[map_color=16711680]`

#### map_id - 地图ID
- 格式：`[map_id=1]`

### 容器与存储

#### container - 容器内容
- 格式：`[container=[{slot:0,item:{id:apple}}]]`
- 说明：此容器槽位中包含的物品
- 示例：`/give @s barrel[container=[{slot:0,item:{id:apple}}]]`

#### container_loot - 战利品容器
- 格式：`[container_loot={loot_table:"chests/desert_pyramid"}]`
- 说明：此容器物品的未解决战利品表和种子
- 示例：`/give @s chest[container_loot={loot_table:"chests/desert_pyramid"}]`

### 旗帜与盾牌

#### banner_patterns - 旗帜图案
- 格式：`[banner_patterns=[{pattern:"triangle_top",color:"red"},{pattern:"cross",color:"white"}]]`
- 说明：应用于旗帜或盾牌的所有图案列表
- 示例：`/give @s black_banner[banner_patterns=[{pattern:"triangle_top",color:"red"},{pattern:"cross",color:"white"}]]`

#### base_color - 基础颜色
- 格式：`[base_color="lime"]`
- 说明：应用在盾牌上的旗帜的基础染料颜色
- 示例：`/give @s shield[base_color="lime"]`

### 头颅相关

#### profile - 玩家头颅
- 格式：`[profile={...}]`

#### note_block_sound - 音符盒声音
- 格式：`[note_block_sound="..."]`

### 其他组件

#### custom_data - 自定义数据
- 格式：`[custom_data={foo:1}]`
- 说明：包含游戏未使用的任何自定义数据的键值对
- 示例：`/give @s iron_sword[custom_data={foo:1}]`

#### repair_cost - 修复成本
- 格式：`[repair_cost=10]`

#### custom_model_data - 自定义模型数据
- 格式：`[custom_model_data={...}]`
- 说明：用于物品模型定义的值列表，用于模型选择和着色
- 示例：`/give @s bone[custom_model_data={floats:[4.0, 5.6, 99.1],strings:["foo:bar"],colors:[8323327, [0.5,0,1], 0x7F00FF]}]`

#### can_break - 可破坏（冒险模式）
- 格式：`[can_break={blocks:['black_concrete','coal_ore']}]`
- 说明：在冒险模式中，持有此物品的玩家可以破坏指定的方块
- 示例：`/give @s netherite_pickaxe[can_break={blocks:['black_concrete','coal_ore','iron_ore','gold_ore','diamond_ore','emerald_ore']}]`

#### can_place_on - 可放置在（冒险模式）
- 格式：`[can_place_on={blocks:'sandstone'}]`
- 说明：在冒险模式中，持有此物品的玩家可以在指定方块的任何面上放置此方块
- 示例：`/give @s target[can_place_on={blocks:'sandstone'}]`

## 示例

基于官方Wiki，请根据用户具体需求执行命令（在部分版本可能不生效）

### 魔法棒 - 自定义名称
```
/give @s stick[custom_name='{"text":"Magic Wand","color":"light_purple","italic":false}']
```

### 变大木棍 - 属性修饰符
```
/give @s stick[attribute_modifiers=[{type:"minecraft:scale",slot:"hand",id:"example:grow",amount:4,operation:"add_multiplied_base"}]]
```

### 蜜蜂巢 - 包含蜜蜂
```
/give @s bee_nest[bees=[{entity_data:{id:"bee",CustomName:"Maya"},min_ticks_in_hive:60,ticks_in_hive:0}]]
```

### 蜘蛛刷怪笼
```
/give @s spawner[block_entity_data={id:"mob_spawner",SpawnData:{entity:{id:"spider"}}}]
```

### 顶部半砖
```
/give @s bamboo_slab[block_state={type:"top"}]
```

### 限定镐子 - 冒险模式
```
/give @s netherite_pickaxe[can_break={blocks:['black_concrete','coal_ore','iron_ore','gold_ore','diamond_ore','emerald_ore']}]
```

### 装填了光灵箭的弩
```
/give @s crossbow[charged_projectiles=[{id:"spectral_arrow"}]]
```

### 可食用金锭
```
/give @s gold_ingot[consumable={consume_seconds:3.0, animation:'eat', sound:'entity.generic.eat', has_consume_particles:true, on_consume_effects:[{type:'minecraft:clear_all_effects'}]}]
```

### 装有苹果的木桶
```
/give @s barrel[container=[{slot:0,item:{id:apple}}]]
```

### 沙漠神殿战利品箱
```
/give @s chest[container_loot={loot_table:"chests/desert_pyramid"}]
```

### 带自定义数据的铁剑
```
/give @s iron_sword[custom_data={foo:1}]
```

### 损坏500点的钻石斧
```
/give @s diamond_axe[damage=500]
```

### 锋利III 击退II 木剑
```
/give @s wooden_sword[enchantments={sharpness:3,knockback:2}]
```

### 小型护甲架
```
/give @s armor_stand[entity_data={id:"armor_stand",Small:1b}]
```

### 可装备在头盔槽的玻璃块
```
/give @s glass[equippable={slot:"head",equip_sound:"block.glass.break",dispensable:true}]
```

### 穿着像钻石护腿的皮革护腿
```
/give @s leather_leggings[equippable={slot:legs,asset_id:"minecraft:diamond"}]
```

### 可随时食用的西瓜片
```
/give @s melon_slice[food={nutrition:3,saturation:1,can_always_eat:true}]
```

### 可食用海绵
```
/give @s minecraft:sponge[consumable={consume_seconds:2.4},food={nutrition:5,saturation:5,can_always_eat:true}]
```
