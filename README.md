
# Regime-Aware Backtester (BankNifty / NIFTY)

A Java-17, Maven-built framework that computes a **daily market regime** from day bars (BankNifty + NIFTY), then runs an intraday (1-min) strategy that can be **gated by regime**. Outputs are fully CSV-driven.

---

## **Key Features**
| Area | Highlights |
|------|------------|
| **Markets** | BankNifty 1-min + day, NIFTY day (benchmark) |
| **Regime Detection** | V (volatility), T (trend), C (BN–NF correlation) with rolling windows |
| **Live-Faithful** | **Start-of-day regime** uses only **previous N days**; no look-ahead |
| **Date Alignment** | Two-cursor merge: skips missing days; stops when either stream ends |
| **Strategy** | EMA/RSI + optional floor pivots; overlap & time-window controls; Thu 15:15 exit |
| **Outputs** | `Outputs/RegimeByDay.csv` + trade/day/overall metrics CSVs |
| **Config-Driven** | All paths/thresholds in `BaseStrategy.properties` and `keystore.csv` |

---

## **Regime Data Flow **
```
flowchart TB
  props[Load BaseStrategy.properties] --> openBN[Open BankNifty Day CSV]
  props --> openNF[Open NIFTY Day CSV]

  openBN --> align{Align by Date (two cursors)}
  openNF --> align

  align -->|BN date earlier| stepBN[Advance BN only → push BN close & (if available) BN return into rolling windows]
  align -->|NF date earlier| stepNF[Advance NF only → update prevCloseNF (returns used only when dates match)]
  align -->|Dates match| compute[Compute Start-of-Day (SOD) Regime using previous N days]

  compute --> write[Write row to Outputs/RegimeByDay.csv (date, code, name, C, T, V, R)]
  write --> advanceToday[Advance windows with TODAY’s BN & NF returns (for tomorrow’s SOD)]
  advanceToday --> align

  subgraph Rolling Windows (size = N)
    priceQ[BN closes → T]
    retQ[BN returns → V]
    corrRetQ[BN returns (matched) → C]
    benchRetQ[NF returns (matched) → C]
  end
```
## **Data Flow (high-level) **
```
ValarTrade  ──▶  Strategy  ──▶  StrategyImpl  ──▶  RegimeService  ──▶  RegimeByDay.csv
   │              │                │                │
   │              │                │                └─▶ Rolling windows (BN closes/returns, NF returns)
   │              │                └─▶ TradeEntity ──▶ TradeMetric (OrderInfo)
   │              └─▶ OverAllMetric (OverallInfo)
   └─▶ (creates keystore batches & hands them to Strategy)

           ┌─────────── State hierarchy ────────────┐
           │ IndexState │ StockState │ OptionState │
           └─────────────────────────────────────────┘
```


## **Bits & Code**
```
	•	V (volatility): std( BN returns over last N ) > regimeVolThreshold
	•	T (trend): lastClose_BN > SMA_N( BN closes )
	•	C (correlation): |corr( BN returns, NF returns )| > regimeCorrThreshold (matched dates only)
	•	Regime code: R = 4*C + 2*T + V → 0..7
0 Calm-NonTrending-Uncorrelated … 7 Volatile-Trending-Correlated
```

Output file: Outputs/RegimeByDay.csv
```
date,regime_code,regime_name,C,T,V,R
YYYY-MM-DD,<int>,<string>,<0/1>,<0/1>,<0/1>,<int>
```

keystore.csv Example
```
sno,indexType,tradeType,costPercent,hpCostPercent,startTime,cutOffTime,endTime,positional,candlePeriod,emaPeriod,rsiPeriod,usePivots,maxOverlap,tradeGap,rsiLong,rsiShort,useRegime,regimeWindow,regimeVolThreshold,regimeCorrThreshold
1,0,l,0.05,0.01,09:15,15:10,15:29,false,1,20,14,true,500,0,60,40,true,10,0.012,0.50
```
## **Output CSV Headers**
<details>
<summary><code>Outputs/RegimeByDay.csv</code></summary>
```
date,regime_code,regime_name,C,T,V,R
```
</details>

<details>
<summary><code>Outputs/OrderInfo[overAll].csv</code></summary>
```
S.no,Symbol,Date,ID,Holding Period,TradeType,Event,EntryDate,EntryTime,EntryClose,
EntryEMA,EntryRSI,EntryPivot,
Event,ExitDate,ExitTime,ExitClose,
ExitEMA,ExitRSI,ExitPivot,
Reason,ReasonInfo,Profit,Profit%,tradeMaxProfit,ProfitWith(Cost),Profit%With(Cost),
DayAtrPercentile,DayAtrPercent,candlesWaited,IndexCloseAtExit,HoldingCost
  ```
</details>
<details>
<summary><code>Outputs/DayWise[overAll].csv</code></summary>
  ```
sno,date,TotalTrades,profit,profit%,ProfitWithcost,Profit%WithCost
  ```
</details>
<details>
<summary><code>Outputs/overAllDetails[serialWise].csv</code></summary>
(aggregate stats)
</details>

## **Quickstart**

1. clone
```
git clone https://github.com/sanjeevprasanna/Backtester-Regime_Detection.git
cd Backtester-Regime_Detection
```
2. build
```
mvn clean package dependency:copy-dependencies
```
3.edit BaseStrategy.properties to match your file paths & params
```
$EDITOR BaseStrategy.properties
```
4.run back-test
```
java -Xms512m -Xmx2g \
  -cp "target/classes:target/dependency/*" \
  com.valar.basestrategy.application.ValarTrade
```

