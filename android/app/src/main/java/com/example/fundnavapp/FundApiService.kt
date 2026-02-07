package com.example.fundnavapp

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

class FundApiService {

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    // 获取基金持仓数据
    fun getFundHoldings(fundCode: String): FundHoldingsResponse? {
        val url = "http://fundf10.eastmoney.com/FundArchivesDatas.aspx"
        val params = "type=jjcc&code=$fundCode&topline=10&year=&month="
        val fullUrl = "$url?$params"

        val request = Request.Builder()
            .url(fullUrl)
            .header("User-Agent", "Mozilla/5.0")
            .header("Referer", "http://fundf10.eastmoney.com/ccmx_$fundCode.html")
            .build()

        try {
            val response: Response = okHttpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                if (responseBody != null) {
                    return parseFundHoldings(responseBody, fundCode)
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }

        return null
    }

    // 解析基金持仓数据
    private fun parseFundHoldings(responseBody: String, fundCode: String): FundHoldingsResponse? {
        try {
            // 提取基金名称
            val nameMatch = Regex("title='(.*?)'").find(responseBody)
            val fundName = nameMatch?.groupValues?.get(1) ?: "基金名称未知"

            // 提取报告日期
            val dateMatch = Regex("截止至：<font class='px12'>(.*?)</font>").find(responseBody)
            val reportDate = dateMatch?.groupValues?.get(1) ?: "--"

            // 提取持仓数据
            var htmlTable = ""
            
            // 首先尝试使用正则表达式提取content字段，匹配正确的结束格式
            val contentMatch = Regex("content:\"(.*?)\",arryear:", RegexOption.DOT_MATCHES_ALL).find(responseBody)
            if (contentMatch != null) {
                htmlTable = contentMatch.groupValues[1]
            } else {
                // 如果失败，尝试使用字符串分割，找到正确的结束位置
                try {
                    val contentStart = responseBody.indexOf("content:")
                    if (contentStart != -1) {
                        val quoteStart = responseBody.indexOf('"', contentStart)
                        if (quoteStart != -1) {
                            // 查找与quoteStart匹配的结束引号，考虑转义情况
                            var quoteEnd = quoteStart + 1
                            var escaped = false
                            while (quoteEnd < responseBody.length) {
                                val char = responseBody[quoteEnd]
                                if (char == '\\') {
                                    escaped = !escaped
                                } else if (char == '"' && !escaped) {
                                    break
                                } else {
                                    escaped = false
                                }
                                quoteEnd++
                            }
                            if (quoteEnd < responseBody.length) {
                                htmlTable = responseBody.substring(quoteStart + 1, quoteEnd)
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // 如果基金名称缺失，尝试备份获取
            var finalFundName = fundName
            if (finalFundName == "基金名称未知") {
                val backupName = getFundNameBackup(fundCode)
                if (backupName != null) {
                    finalFundName = backupName
                }
            }

            // 检查是否为ETF联接基金
            val isFeederNamed = finalFundName.contains("联接") || finalFundName.contains("ETF")

            // 解析持仓数据
            val holdings = mutableListOf<Holding>()
            if (htmlTable.isNotEmpty() && "暂无数据" !in htmlTable && htmlTable.length > 50) {
                val rowsPattern = Regex("<tr>(.*?)</tr>", RegexOption.DOT_MATCHES_ALL)
                val rows = rowsPattern.findAll(htmlTable)

                for (rowMatch in rows) {
                    try {
                        val rowHtml = rowMatch.groupValues[1]
                        if ("th" in rowHtml) continue

                        // 提取股票代码和市场
                        val linkPattern = Regex("unify/r/(\\d+)\\.([a-zA-Z0-9]+)")
                        val linkMatch = linkPattern.find(rowHtml)
                        var stockCode = "Unknown"
                        var marketId: String? = null

                        if (linkMatch != null) {
                            marketId = linkMatch.groupValues[1]
                            stockCode = linkMatch.groupValues[2]
                        } else {
                            val colsPattern = Regex("<td.*?>(.*?)</td>", RegexOption.DOT_MATCHES_ALL)
                            val cols = colsPattern.findAll(rowHtml).toList()
                            if (cols.size > 1) {
                                val tagPattern = Regex("<.*?>")
                                stockCode = tagPattern.replace(cols[1].groupValues[1], "").trim()
                            }
                        }

                        // 提取股票名称和权重
                        val colsPattern = Regex("<td.*?>(.*?)</td>", RegexOption.DOT_MATCHES_ALL)
                        val cols = colsPattern.findAll(rowHtml).toList()
                        
                        // 检查列数是否足够
                        if (cols.size < 7) continue
                        
                        // 提取股票名称
                        val tagPattern = Regex("<.*?>")
                        val stockName = tagPattern.replace(cols[2].groupValues[1], "").trim()

                        // 提取权重（从第7列，索引6）- 与Python代码保持一致
                        val weightStr = tagPattern.replace(cols[6].groupValues[1], "").trim()
                            .replace("%", "").replace(",", "")
                        if (weightStr.isEmpty() || weightStr == "--") continue

                        val weight = weightStr.toDoubleOrNull() ?: continue

                        // 生成新浪财经的股票代码
                        val sinaCode = generateSinaCode(marketId, stockCode)

                        holdings.add(Holding(stockCode, stockName, weight, sinaCode))
                    } catch (e: Exception) {
                        // 跳过解析失败的行
                        e.printStackTrace()
                        continue
                    }
                }
            }

            // 计算持仓权重总和
            val totalWeight = holdings.sumOf { it.weight }
            val isAbnormalHighWeight = totalWeight > 100.0 // 数据问题指标
            val isSuspiciousLowWeight = totalWeight < 60.0 // 严格检查

            if (holdings.isEmpty() || (isAbnormalHighWeight && isFeederNamed) || (isSuspiciousLowWeight && isFeederNamed)) {
                if (isFeederNamed) {
                    // 尝试找到目标ETF
                    var targetName = finalFundName

                    // 1. 移除公司前缀
                    val commonPrefixes = listOf("南方", "华夏", "博时", "易方达", "嘉实", "富国", "广发", "汇添富", "招商", "工银", "中欧", "天弘", "华安", "鹏华", "国泰", "华宝", "银华", "大成", "景顺长城")
                    for (prefix in commonPrefixes) {
                        if (targetName.startsWith(prefix)) {
                            targetName = targetName.substring(prefix.length)
                            break
                        }
                    }

                    // 2. 移除类型/类别信息
                    targetName = targetName.replace("发起式", "")
                    targetName = targetName.replace("（QDII）", "").replace("(QDII)", "")
                    targetName = targetName.replace("人民币", "").replace("美元", "")

                    // 3. 移除"联接"后缀
                    targetName = targetName.replace(Regex("联接[A-Z]?$"), "")
                    targetName = targetName.replace("联接", "")

                    // 4. 移除类别后缀
                    targetName = targetName.replace(Regex("[A-E]$", RegexOption.IGNORE_CASE), "")

                    // 搜索ETF代码
                    val targetCode = searchEtfCode(targetName)
                    if (targetCode != null && targetCode != fundCode) {
                        // 直接使用ETF本身作为持仓，而不是尝试获取其持仓数据
                        // 这样可以避免无限递归和其他问题
                        val etfFetchCode = if (targetCode.startsWith('5')) "sh$targetCode" else "sz$targetCode"
                        val etfHoldings = mutableListOf<Holding>()
                        etfHoldings.add(Holding(targetCode, targetName, 95.0, etfFetchCode))
                        return FundHoldingsResponse(finalFundName, etfHoldings, "实时追踪")
                    }
                }
            }

            return FundHoldingsResponse(finalFundName, holdings, reportDate)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return null
    }

    // 生成新浪财经的股票代码
    private fun generateSinaCode(marketId: String?, stockCode: String): String {
        if (marketId != null) {
            when (marketId) {
                "0" -> return "sz$stockCode" // 深圳
                "1" -> return "sh$stockCode" // 上海
                "116" -> return "rt_hk${stockCode.padStart(5, '0')}" // 香港
                else -> if (marketId.toIntOrNull() ?: 0 >= 100) {
                    return "gb_${stockCode.toLowerCase()}" // 美国
                }
            }
        }

        //  fallback
        if (stockCode.any { it.isLetter() }) {
            return "gb_${stockCode.toLowerCase()}"
        } else if (stockCode.length < 6) {
            return "rt_hk${stockCode.padStart(5, '0')}"
        } else {
            return if (stockCode.startsWith('6') || stockCode.startsWith('5')) {
                "sh$stockCode"
            } else {
                "sz$stockCode"
            }
        }
    }

    // 搜索ETF代码
    private fun searchEtfCode(etfName: String): String? {
        try {
            val encodedName = java.net.URLEncoder.encode(etfName, "UTF-8")
            val url = "http://suggest3.sinajs.cn/suggest/type=&key=$encodedName"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0")
                .build()

            val response: Response = okHttpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                if (responseBody != null) {
                    if ("suggestvalue=" in responseBody) {
                        val valPart = responseBody.split("suggestvalue=")[1].trim('"').trim(';')
                        if (valPart.isNotEmpty()) {
                            val firstMatch = valPart.split(';')[0]
                            val parts = firstMatch.split(',')
                            if (parts.size >= 4) {
                                return parts[2]
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    // 获取基金名称备份
    private fun getFundNameBackup(fundCode: String): String? {
        // 1. 尝试从JBGK页面获取
        try {
            val url = "http://fundf10.eastmoney.com/jbgk_$fundCode.html"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0")
                .build()

            val response: Response = okHttpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                if (responseBody != null) {
                    val match = Regex("基金全称.*?<td>(.*?)</td>", RegexOption.DOT_MATCHES_ALL).find(responseBody)
                    if (match != null) {
                        return match.groupValues[1].trim()
                    }
                    val match2 = Regex("<th>基金全称</th>\\s*<td>(.*?)</td>").find(responseBody)
                    if (match2 != null) {
                        return match2.groupValues[1].trim()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. 尝试从ZQCC页面获取
        try {
            val url = "http://fundf10.eastmoney.com/FundArchivesDatas.aspx"
            val params = "type=zqcc&code=$fundCode&topline=10"
            val fullUrl = "$url?$params"
            val request = Request.Builder()
                .url(fullUrl)
                .header("User-Agent", "Mozilla/5.0")
                .header("Referer", "http://fundf10.eastmoney.com/ccmx_$fundCode.html")
                .build()

            val response: Response = okHttpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                if (responseBody != null) {
                    val match = Regex("fund.eastmoney.com/\\d+.html'>(.*?)</a>").find(responseBody)
                    if (match != null) {
                        return match.groupValues[1]
                    }
                    val match2 = Regex("title='(.*?)'").find(responseBody)
                    if (match2 != null) {
                        return match2.groupValues[1]
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return null
    }

    // 获取实时股票价格
    fun getRealtimeStockPrices(stockCodes: List<String>): Map<String, StockPrice> {
        val results = mutableMapOf<String, StockPrice>()
        if (stockCodes.isEmpty()) return results

        val listParam = stockCodes.joinToString(",")
        val url = "http://hq.sinajs.cn/list=$listParam"

        val request = Request.Builder()
            .url(url)
            .header("Referer", "http://finance.sina.com.cn/")
            .build()

        try {
            val response: Response = okHttpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                if (responseBody != null) {
                    parseStockPrices(responseBody, results)
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }

        return results
    }

    // 解析股票价格
    private fun parseStockPrices(responseBody: String, results: MutableMap<String, StockPrice>) {
        val lines = responseBody.split('\n')
        for (line in lines) {
            if (line.isEmpty() || !line.contains("=")) continue

            val parts = line.split('=')
            if (parts.size < 2) continue

            val key = parts[0].trim().removePrefix("var hq_str_").trim()
            val dataStr = parts[1].trim().removeSurrounding("\"")
            if (dataStr.isEmpty()) continue

            val data = dataStr.split(',')

            var name = "Unknown"
            var price = 0.0
            var changePct = 0.0

            if (key.startsWith("rt_hk")) {
                // 香港股票
                if (data.size >= 9) {
                    name = data[1]
                    price = data[6].toDoubleOrNull() ?: 0.0
                    changePct = data[8].toDoubleOrNull() ?: 0.0
                }
            } else if (key.startsWith("gb_")) {
                // 美国股票
                if (data.size >= 3) {
                    name = data[0]
                    price = data[1].toDoubleOrNull() ?: 0.0
                    changePct = data[2].toDoubleOrNull() ?: 0.0
                }
            } else {
                // A股
                if (data.size >= 4) {
                    name = data[0]
                    val preClose = data[2].toDoubleOrNull() ?: 0.0
                    val currentPrice = data[3].toDoubleOrNull() ?: 0.0
                    price = currentPrice
                    if (preClose > 0) {
                        changePct = ((currentPrice - preClose) / preClose) * 100
                    }
                }
            }

            results[key] = StockPrice(name, price, changePct)
        }
    }

    // 估算基金净值变化
    fun estimateNavChange(holdings: List<Holding>): NavEstimation {
        val stockCodes = holdings.mapNotNull { it.sinaCode }
        val stockPrices = getRealtimeStockPrices(stockCodes)

        var totalWeight = 0.0
        var weightedChange = 0.0
        val details = mutableListOf<HoldingDetail>()

        for (holding in holdings) {
            val stockPrice = stockPrices[holding.sinaCode]
            if (stockPrice != null) {
                totalWeight += holding.weight
                weightedChange += holding.weight * stockPrice.changePct
                details.add(HoldingDetail(
                    holding.code,
                    holding.name,
                    holding.weight,
                    stockPrice.price,
                    stockPrice.changePct
                ))
            }
        }

        val estimatedChange = if (totalWeight > 0) {
            // 使用BigDecimal进行精确计算，避免浮点数精度问题
            val bdWeightedChange = java.math.BigDecimal(weightedChange.toString())
            val bdTotalWeight = java.math.BigDecimal(totalWeight.toString())
            bdWeightedChange.divide(bdTotalWeight, 4, java.math.RoundingMode.HALF_UP).toDouble()
        } else {
            0.0
        }

        return NavEstimation(estimatedChange, totalWeight, details)
    }

    // 数据类
    data class FundHoldingsResponse(
        val fundName: String,
        val holdings: List<Holding>,
        val reportDate: String
    )

    data class Holding(
        val code: String,
        val name: String,
        val weight: Double,
        val sinaCode: String?
    )

    data class StockPrice(
        val name: String,
        val price: Double,
        val changePct: Double
    )

    data class NavEstimation(
        val estimatedChange: Double,
        val totalWeight: Double,
        val details: List<HoldingDetail>
    )

    data class HoldingDetail(
        val code: String,
        val name: String,
        val weight: Double,
        val price: Double,
        val change: Double
    )
}