# 🇨🇳 China Mutual Fund Real-time NAV Estimator
(中国公募基金实时净值估算系统)

[![Python 3.8+](https://img.shields.io/badge/python-3.8+-blue.svg)](https://www.python.org/downloads/)
[![Streamlit](https://img.shields.io/badge/Streamlit-FF4B4B?logo=streamlit&logoColor=white)](https://streamlit.io)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

> **实时追踪，告别盲猜。**  
> 基于 Python 与 Streamlit 构建的现代化基金净值估算工具，支持 A 股、港股、美股及 ETF 联接基金的实时穿透估值。

---

## ✨ 核心功能 (Features)

*   **📊 实时净值估算**
    *   **股票型基金**：自动抓取前十大重仓股，支持 **A股/港股/美股** 混合持仓的实时加权估算。
    *   **ETF 联接基金**：🚀 **智能识别引擎**，自动穿透联接基金，直接追踪底层 ETF 的实时行情 (如黄金、纳指、红利等)。

*   **⚡️ 高频数据更新**
    *   **秒级响应**：多线程并发架构，支持 60秒 自动刷新。
    *   **双重图表**：
        *   📈 **分时走势**：可视化日内波动曲线。
        *   📅 **历史净值**：官方净值历史回溯。

*   **💪 鲁棒性设计**
    *   **自动容错**：当标准持仓数据缺失时，自动降级抓取备用数据源 (Base Info)。
    *   **智能清洗**：自动处理基金名称中的干扰字符，精准匹配目标资产。
    *   **异常检测**：自动识别数据异常（如权重溢出），防止错误估值。

## 🧠 估算原理 (Estimation Algorithm)

本系统采用 **归一化加权平均法** 进行净值估算，核心逻辑如下：

### 1. 股票型基金 (Stock Funds)
对于主动管理型权益基金，系统通过爬虫获取最新的季度持仓报告（前十大重仓股）。

$$
\text{Est. Change} = \frac{\sum_{i=1}^{N} (w_i \times r_i)}{\sum_{i=1}^{N} w_i}
$$

*   $w_i$: 第 $i$ 只重仓股的占净值权重
*   $r_i$: 第 $i$ 只重仓股的实时涨跌幅
*   **假设**：前十大重仓股（通常占 40%-60% 仓位）的走势能够代表基金整体持仓的风格与方向。系统对权重进行了归一化处理，相当于假设剩余仓位的表现与重仓股一致。

### 2. ETF 联接基金 (ETF Feeder Funds)
对于 ETF 联接基金，系统不使用重仓股估算，而是采用 **"穿透式"** 追踪：

1.  **识别**：检测到基金名称包含 "联接" 或持仓结构异常（如股票权重极低）。
2.  **映射**：解析基金名称（如 "博时黄金ETF联接C"），自动匹配对应的场内 ETF 代码（518880）。
3.  **追踪**：直接获取场内 ETF 的实时涨跌幅作为该基金的估值。

$$
\text{Est. Change} = \text{Target ETF Real-time Change} \times 95\%
$$

*(注：系统默认假设 95% 仓位追踪 ETF，实际上直接显示 ETF 涨跌更为直观)*

## 🚀 快速开始 (Quick Start)

### 1. 环境依赖 (Prerequisites)
确保已安装 Python 3.8 或以上版本。

```bash
git clone https://github.com/your-username/Fund_nav.git
cd Fund_nav
pip install -r requirements.txt
```

### 2. 启动应用 (Run)
一行命令启动 Web 界面：

```bash
streamlit run app.py
```

终端将显示访问地址 `http://localhost:8501`，会自动在浏览器打开。

## 📖 使用指南 (Usage)

1.  **添加基金**：
    在左侧侧边栏输入基金代码（逗号分隔）。
    *   *混合示例*: `110011, 000001` (易方达蓝筹 / 华夏成长)
    *   *ETF 联接*: `002611` (博时黄金), `006479` (广发纳指100)
    
2.  **观察看板**：
    *   **红色** 🔴 代表上涨，**绿色** 🟢 代表下跌。
    *   点击 **"查看详情"** 展开重仓股的单日详细表现。

3.  **自动盯盘**：
    勾选侧边栏 `自动刷新`，系统将每分钟自动拉取最新行情。

## 🛠 技术栈 (Tech Stack)

*   **Frontend**: [Streamlit](https://streamlit.io/) - 极速构建数据应用
*   **Data Processing**: [Pandas](https://pandas.pydata.org/) - 金融数据处理
*   **Networking**: `Requests` + `Concurrent.Futures` - 高并发爬虫
*   **Visualization**: `Streamlit Charts` - 交互式图表

## ⚠️ 免责声明 (Disclaimer)

*   **数据来源**：本系统数据来源于东财、新浪等公开免费接口，不保证数据的实时性与准确性。
*   **估值偏差**：估值仅基于公开持仓数据（滞后性）计算，无法实时反映基金经理的调仓操作。**估算结果仅供参考，不作为投资依据。**
*   本项目仅供编程学习交流，请勿用于商业用途。

## 📱 Android 应用 (Android App)

本项目同时提供了基于 Kotlin 开发的 Android 原生应用，支持离线使用和本地数据存储。

### 应用截图 (Screenshot)

<img src="images/20260206174652_8_1.jpg" alt="Android App Screenshot" width="500" style="display: block; margin: 0 auto;">

### 构建与打包步骤 (Build & Package)

#### 1. 环境依赖 (Prerequisites)
确保已安装以下软件：
- JDK 1.8 或以上版本（推荐 JDK 25.0.2）
- Android SDK（包含 Android 34 平台）
- Gradle 9.3.1 或以上版本

#### 2. 配置环境变量 (Environment Variables)
在 `android/gradle.properties` 文件中配置以下路径：

```properties
org.gradle.java.home=D:/Program Files/Java/jdk-25.0.2
```

#### 3. 构建调试版本 (Build Debug Version)

```bash
# 进入 android 目录
cd android

# 清理构建缓存
./gradlew clean

# 构建调试版 APK
./gradlew assembleDebug
```

构建完成后，APK 文件将生成在：`build/app/outputs/apk/debug/app-debug.apk`

#### 4. 构建发布版本 (Build Release Version)

```bash
# 进入 android 目录
cd android

# 生成签名密钥（首次构建时需要）
"D:\Program Files\Java\jdk-25.0.2\bin\keytool.exe" -genkey -v -keystore my-release-key.keystore -alias alias_name -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android, OU=Android, O=Android, L=Android, ST=Android, C=CN" -storepass android -keypass android

# 复制密钥文件到 app 目录
copy my-release-key.keystore app/

# 构建发布版 APK
./gradlew assembleRelease
```

构建完成后，APK 文件将生成在：`build/app/outputs/apk/release/app-release.apk`

### 主要功能 (Main Features)

- **基金管理**：添加、删除基金，本地 SQLite 存储
- **实时净值估算**：基于持仓股票的实时行情计算
- **数据可视化**：净值趋势图和实时分时走势图
- **网络安全**：支持 HTTP 接口访问（配置了网络安全策略）
- **中国时间**：使用 Asia/Shanghai 时区显示时间

### 技术栈 (Tech Stack)

- **语言**：Kotlin
- **网络**：Retrofit + OkHttp
- **JSON 解析**：Gson
- **数据库**：SQLite
- **图表**：MPAndroidChart
- **UI**：Material Design Components

---
Made with ❤️ by Desmond