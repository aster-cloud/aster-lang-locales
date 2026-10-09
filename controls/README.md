# @aster-cloud/controls

控制注册表（ADR 0045）的唯一真相源：`registry.json` 定义 `@control` 的受控词表、法规框架，以及各控制点下的法规对照条款与证据来源。编译器与证据导出共用同一份数据，避免各处各写一套。

新增控制点的步骤：修改 `registry.json`，运行 `./gradlew exportControls` 校验并导出到 `build/controls/registry.json`，再把注册表同步到其余三处副本（见 ADR 0045 §2.2）。`registry.json` 是源文件，必须入库，这一点不同于 ui-messages 的构建产物。

npm 包的发布需先在 `release-plan.json` 中登记（见 ADR 0045 §7），登记前不要手工发布。

校验规则的负向样例在 `src/test-fixtures/`，每个都必须让校验失败：`./gradlew exportControls -PcontrolsSource=controls/src/test-fixtures/<文件>.json`。`bad-unknown-control.json` 是条款引用未登记控制键，`bad-duplicate-clause.json` 是同一控制点下条款 id 重复，`bad-missing-framework.json` 是框架缺 id 且控制点缺 framework。
