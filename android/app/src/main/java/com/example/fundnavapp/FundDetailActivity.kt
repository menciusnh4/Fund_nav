package com.example.fundnavapp

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FundDetailActivity : AppCompatActivity() {

    private lateinit var fundCode: String
    private lateinit var fundName: String
    private var currentAmount: Double = 0.0

    private var dailyChange: Double = 0.0
    private var dailyProfit: Double = 0.0

    private lateinit var fundCodeTextView: android.widget.TextView
    private lateinit var fundNameTextView: android.widget.TextView
    private lateinit var currentAmountTextView: android.widget.TextView

    private lateinit var dailyChangeTextView: android.widget.TextView
    private lateinit var dailyProfitTextView: android.widget.TextView
    private lateinit var estimatedNavTextView: android.widget.TextView
    private lateinit var estimatedChangeTextView: android.widget.TextView
    private lateinit var reportDateTextView: android.widget.TextView
    private lateinit var navUpdateTimeTextView: android.widget.TextView
    private lateinit var loadingProgressBar: android.widget.ProgressBar
    private lateinit var holdingsRecyclerView: RecyclerView
    private lateinit var navTrendChart: LineChart
    private lateinit var realtimeChart: LineChart
    private lateinit var refreshButton: android.widget.Button
    private val fundApiService = FundApiService()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fund_detail)

        // Get fund data from intent
        fundCode = intent.getStringExtra("fundCode") ?: ""
        fundName = intent.getStringExtra("fundName") ?: ""
        currentAmount = intent.getDoubleExtra("currentAmount", 0.0)
        dailyChange = intent.getDoubleExtra("dailyChange", 0.0)
        dailyProfit = intent.getDoubleExtra("dailyProfit", 0.0)

        // Initialize views
        fundCodeTextView = findViewById(R.id.fundCodeTextView)
        fundNameTextView = findViewById(R.id.fundNameTextView)
        currentAmountTextView = findViewById(R.id.currentAmountTextView)
        dailyChangeTextView = findViewById(R.id.dailyChangeTextView)
        dailyProfitTextView = findViewById(R.id.dailyProfitTextView)
        estimatedNavTextView = findViewById(R.id.estimatedNavTextView)
        estimatedChangeTextView = findViewById(R.id.estimatedChangeTextView)
        reportDateTextView = findViewById(R.id.reportDateTextView)
        navUpdateTimeTextView = findViewById(R.id.navUpdateTimeTextView)
        loadingProgressBar = findViewById(R.id.loadingProgressBar)
        holdingsRecyclerView = findViewById(R.id.holdingsRecyclerView)
        holdingsRecyclerView.layoutManager = LinearLayoutManager(this@FundDetailActivity)
        navTrendChart = findViewById(R.id.navTrendChart)
        realtimeChart = findViewById(R.id.realtimeChart)
        refreshButton = findViewById(R.id.refreshButton)

        // Set initial data
        fundCodeTextView.text = fundCode
        fundNameTextView.text = fundName
        currentAmountTextView.text = "当前金额: ¥${String.format("%.2f", currentAmount)}"
        dailyChangeTextView.text = "当日涨幅: ${String.format("%.2f", dailyChange)}%"
        dailyProfitTextView.text = "当日收益: ¥${String.format("%.2f", dailyProfit)}"

        // Set daily change text color
        dailyChangeTextView.setTextColor(
            if (dailyChange >= 0) {
                getColor(R.color.red)
            } else {
                getColor(R.color.green)
            }
        )

        // Set daily profit text color
        dailyProfitTextView.setTextColor(
            if (dailyProfit >= 0) {
                getColor(R.color.red)
            } else {
                getColor(R.color.green)
            }
        )

        // Refresh button click event
        refreshButton.setOnClickListener {
            loadFundDetail()
        }

        // Load fund detail
        loadFundDetail()
    }

    private fun loadFundDetail() {
        loadingProgressBar.visibility = android.view.View.VISIBLE

        GlobalScope.launch(Dispatchers.IO) {
            try {
                // 从API获取基金持仓数据
                val holdingsResponse = fundApiService.getFundHoldings(fundCode)
                if (holdingsResponse != null) {
                    // 估算净值变化
                    val estimation = fundApiService.estimateNavChange(holdingsResponse.holdings)
                    val estimatedChange = estimation.estimatedChange
                    val estimatedNav = 1.2 * (1 + estimatedChange / 100) // 基础净值1.2

                    // 准备持仓详情数据
                    val holdingsDetails = estimation.details

                    // 模拟净值趋势数据
                    val navEntries = mutableListOf<Entry>()
                    for (i in 0..9) {
                        navEntries.add(Entry(i.toFloat(), (1.2 + i * 0.01).toFloat()))
                    }

                    // 模拟实时趋势数据
                    val realtimeEntries = mutableListOf<Entry>()
                    for (i in 0..9) {
                        realtimeEntries.add(Entry(i.toFloat(), (0 + i * estimatedChange / 900).toFloat()))
                    }

                    withContext(Dispatchers.Main) {
                        // Update UI with data
                        estimatedNavTextView.text = "估算净值: ${String.format("%.4f", estimatedNav)}"
                        estimatedChangeTextView.text = "估算涨跌: ${String.format("%.2f", estimatedChange)}%"
                        reportDateTextView.text = "持仓日期: ${holdingsResponse.reportDate}"
                        
                        // Set nav update time to current time with China timezone
                        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                        sdf.timeZone = java.util.TimeZone.getTimeZone("Asia/Shanghai")
                        val currentTime = sdf.format(java.util.Date())
                        navUpdateTimeTextView.text = "净值更新时间: $currentTime"

                        // Set change text color
                        estimatedChangeTextView.setTextColor(
                            if (estimatedChange >= 0) {
                                getColor(R.color.red)
                            } else {
                                getColor(R.color.green)
                            }
                        )

                        // Update holdings list
                        holdingsRecyclerView.adapter = HoldingsAdapter(holdingsDetails)

                        // Setup nav trend chart
                        setupChart(navTrendChart, navEntries, "净值趋势")

                        // Setup realtime chart
                        setupChart(realtimeChart, realtimeEntries, "实时趋势")

                        loadingProgressBar.visibility = android.view.View.GONE
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@FundDetailActivity, "获取基金数据失败", Toast.LENGTH_SHORT).show()
                        loadingProgressBar.visibility = android.view.View.GONE
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@FundDetailActivity, "网络错误，请稍后重试", Toast.LENGTH_SHORT).show()
                    loadingProgressBar.visibility = android.view.View.GONE
                }
            }
        }
    }

    private fun setupChart(chart: LineChart, entries: List<Entry>, label: String) {
        val dataSet = LineDataSet(entries, label)
        dataSet.setDrawCircles(true)
        dataSet.setCircleColor(getColor(R.color.blue))
        dataSet.lineWidth = 2f
        dataSet.setColor(getColor(R.color.blue))

        val lineData = LineData(dataSet)
        chart.data = lineData
        chart.invalidate() // Refresh chart

        // Configure chart
        chart.description.text = ""
        val legend = chart.legend
        legend.setDrawInside(false)
        chart.animateXY(1000, 1000)
    }

    class HoldingsAdapter(private val holdings: List<FundApiService.HoldingDetail>) : RecyclerView.Adapter<HoldingsAdapter.ViewHolder>() {
        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val codeTextView: TextView = view.findViewById(R.id.codeTextView)
            val nameTextView: TextView = view.findViewById(R.id.nameTextView)
            val weightTextView: TextView = view.findViewById(R.id.weightTextView)
            val priceTextView: TextView = view.findViewById(R.id.priceTextView)
            val changeTextView: TextView = view.findViewById(R.id.changeTextView)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_holding, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val holding = holdings[position]
            holder.codeTextView.text = holding.code
            holder.nameTextView.text = holding.name
            holder.weightTextView.text = String.format("%.2f%%", holding.weight)
            holder.priceTextView.text = String.format("%.2f", holding.price)
            holder.changeTextView.text = String.format("%.2f%%", holding.change)

            // Set change text color
            holder.changeTextView.setTextColor(
                if (holding.change >= 0) {
                    holder.itemView.context.getColor(R.color.red)
                } else {
                    holder.itemView.context.getColor(R.color.green)
                }
            )
        }

        override fun getItemCount(): Int {
            return holdings.size
        }
    }
}
