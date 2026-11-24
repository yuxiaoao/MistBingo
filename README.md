# MistBingo

每日 3×3 集物（Bingo）+ 建材兑换券商店，Paper/Spigot 插件。

- 玩家每天获得一张 3×3 集物表，提交物品凑成任意一行、一列或对角线即可完成
- 完成获得建材兑换券，右键打开商店兑换建材
- 支持聊天栏可点击卡片、GUI 商店、每日刷新、未完成卡片累积
- 可选完成奖励：通过控制台命令发放金币（默认 `money give %player% 400`）

## 环境

- Paper/Spigot 1.20+
- Java 21+（编译目标 21）
- 无第三方运行时依赖

## 构建

```bash
mvn package
```

产物：`target/MistBingo-1.0.0.jar`

## 安装

1. 把 jar 放进 `plugins/`
2. 重启服务器
3. 首次启动生成：
   - `plugins/MistBingo/config.yml`
   - `plugins/MistBingo/data.yml`
   - `plugins/MistBingo/shops.yml`

## 玩家命令

```
/mistdaily                  查看今天的集物表
/mistdaily submit <序号>    提交指定卡片
/mistdaily pending          查看未完成卡片数量
```

聊天栏卡片支持鼠标悬停预览、点击提交。

## 管理员命令

```
/mistdaily reset                        重置自己的集物表与累计完成数
/mistdaily debug                        查看卡池、玩家状态、卡片进度
/mistdaily voucher [玩家] <数量>        发放兑换券（1–2304）

/mistshop                               打开商店
/mistshop add <价格>                    手持物品上架
/mistshop list                          列出商品
/mistshop remove <序号>                 下架商品
/mistshop open|close                    开关商店
/mistshop generate <价格>               重新生成默认建材目录
```

权限：

- `mistbingo.use`：使用 `/mistdaily`
- `mistbingo.shop`：打开商店
- `mistbingo.admin`：管理命令

## 配置要点

```yaml
refresh:
  hour: 0
  zone: Asia/Shanghai

max-pending: 7
data-retention-days: 90   # 0 = 不清理

# 完成一张表后执行的控制台命令
reward:
  commands:
    - 'money give %player% 400'

shop:
  style: gui              # gui 或 merchant
  catalog-on-start: true  # 仅在商店为空时生成默认目录
```

- 商店价格上限 2304；`merchant` 模式只支持 ≤64 的商品，超过会跳过
- `gui` 模式支持拖拽保护、点击购买和 300ms 防连点
- `data-retention-days` 控制长期不活跃玩家数据清理，设为 `0` 关闭

## 数据

- 玩家进度保存在 `data.yml`，写入使用临时文件 + 原子替换
- 商店商品保存在 `shops.yml`
- 清理逻辑按 `last-seen` 判断，启动时和每 6 小时运行一次
