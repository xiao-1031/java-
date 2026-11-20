# Logisim 时序测试插件

Java 课程项目：自动时序测试、参考电路对拍、CSV 脚本与失败波形复现。

## 构建与启动
使用 JDK 9 或更新版本（编译目标 Java 8），以及 Logisim 2.7.1。

```powershell
./build.ps1 -LogisimJar 'C:/path/to/logisim-generic-2.7.1.jar'
java -jar dist/logisim-learner.jar
```

build.ps1 每次使用独立构建目录；构建失败立即停止，成功后将插件与运行依赖放到 dist。

## 本轮验证
全部源码使用 --release 8 编译成功；8 项数值边界和 12 项 CSV 集成检查通过。
尚未完成真实电路 GUI 端到端测试，不能据此宣称所有功能正确。

## 改动
修复 32 位输入溢出截断；拒绝非法位宽、负数默认 ticks、闭合引号后的多余字符。

## 协作
源码通过分支与 Pull Request 协作。报告：https://www.overleaf.com/project/6afe84e1662cc9446ea9b89a
第三方 Logisim 不提交到源码仓库；应补充其来源和许可记录。dist/ 和 build/ 已忽略。
