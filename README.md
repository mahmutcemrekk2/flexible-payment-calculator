# 💰 Flexible Payment Calculator (Esnek Kredi Ödeme Hesaplayıcı)

A terminal-based **interactive loan payment calculator** built with Java. Simulates real banking API logic with precision-grade financial calculations.

Designed for **QA Engineers and Test Automation specialists** working in the finance/banking domain who need to independently verify loan calculation APIs.

---

## 🎯 Why This Tool?

When testing banking APIs, you often need to verify complex loan calculations — installment amounts, interest breakdowns, tax calculations (KKDF/BSMV), and remaining balances. This tool lets you:

- **Reproduce API results locally** without calling the actual API
- **Verify edge cases** with different parameters (loan types, frequencies, reduction/increase scenarios)
- **Understand the math** behind loan amortization with transparent step-by-step output

---

## ✨ Features

### 3 Calculation Modes

| Mode | Description |
|------|-------------|
| 🔄 **Ara Ödemeli** (Inter-Payment) | Fixed installments with periodic lump-sum payments |
| 📉 **Azalan Taksitli** (Decreasing) | Installments decrease over time by rate or amount |
| 📈 **Artan Taksitli** (Increasing) | Installments increase over time by rate or amount |

### Core Capabilities

- **Interactive CLI** — Arrow key navigation with JLine integration
- **Precision Math** — `BigDecimal` with `ROUND_HALF_UP(2)` matching banking standards
- **Binary Search** — Finds the exact installment amount that zeros out the balance
- **Tax Calculation** — Automatic KKDF & BSMV based on loan type
- **Multiple Frequencies** — Monthly, quarterly, semi-annual, annual payments
- **4 Loan Types** — Consumer, Mortgage, Vehicle, SME (each with different tax rules)

---

## 🚀 Quick Start

### 🌐 Web UI (Tarayıcıda Kullan — Kurulum Gerektirmez)

Canlı demo: **[https://mahmutcemrekk2.github.io/flexible-payment-calculator](https://mahmutcemrekk2.github.io/flexible-payment-calculator)**

1. Yukarıdaki linke tıklayın
2. Hesaplama modunu seçin (Ara Ödemeli / Azalan Taksit / Artan Taksit)
3. Kredi parametrelerini girin (tutar, vade, faiz oranı, kredi türü vb.)
4. **Hesapla** butonuna basın — taksit planı anında görüntülenir

> 💡 Hiçbir kurulum gerekmez. Tarayıcınızda tamamen yerel çalışır, verileriniz hiçbir sunucuya gönderilmez.

**Yerel kullanım** — Repoyu klonlayıp tarayıcıda açabilirsiniz:
```bash
git clone https://github.com/mahmutcemrekk2/flexible-payment-calculator.git
cd flexible-payment-calculator
open docs/index.html        # macOS
# veya: start docs/index.html   (Windows)
# veya: xdg-open docs/index.html (Linux)
```

### 🖥️ CLI (Terminal — Java Gerektirir)

Terminalde interaktif şekilde kullanmak için:

**Gereksinimler:** Java 17+, Maven 3.x

```bash
git clone https://github.com/mahmutcemrekk2/flexible-payment-calculator.git
cd flexible-payment-calculator
mvn compile -q
mvn -q exec:java -Dexec.mainClass="com.payment.Main"
```

Ok tuşlarıyla menüde gezinin, Enter ile seçin.

### VS Code
1. Open the project in VS Code
2. Go to **Run and Debug** → **"Esnek Kredi Hesapla (Terminal)"**
3. Press ▶️ Play (or `F5`)

> ⚠️ Use the Run and Debug panel, not the inline Code Lens buttons — those may open Debug Console which doesn't support arrow keys.

---

## 📸 Demo

```
╔═══════════════════════════════════════════════╗
║   💰 ESNEK KREDİ ÖDEMESİ HESAPLAMA ARACI    ║
╠═══════════════════════════════════════════════╣
║  1. Ara Ödemeli   (Inter-Payment)            ║
║  2. Azalan Taksit (Decreasing Payment)       ║
║  3. Artan Taksit  (Increasing Payment)       ║
╚═══════════════════════════════════════════════╝
```

### Sample Output — Decreasing Payment (Azalan Taksit)
```
─────────┼────────────────┼────────────────┼──────────────┼───────────────
 Taksit  │    Aylık Ödeme │        Anapara │         Faiz │         Bakiye
─────────┼────────────────┼────────────────┼──────────────┼───────────────
    1    │      34.684,01 │      10.084,01 │   20.750,00  │    489.915,99
    2    │      34.684,01 │      10.502,51 │   20.331,51  │    479.413,48
    ...
    6  ↓ │      32.949,81 │      13.730,78 │   18.362,35  │    424.006,88
    ...
   36    │      28.181,04 │      28.181,04 │    1.169,51  │          0,00
─────────┼────────────────┼────────────────┼──────────────┼───────────────
```

---

## 🧮 How It Works

The calculator simulates the bank's amortization logic:

```
For each month:
  1. Interest    = Balance × Monthly Rate
  2. KKDF        = Interest × KKDF Rate (15% for Consumer, 0% for Mortgage)
  3. BSMV        = Interest × BSMV Rate (15% for Consumer, 0% for Mortgage)
  4. Principal   = Installment - Interest - KKDF - BSMV
  5. New Balance = Balance - Principal
```

The initial installment amount is determined via **binary search** — finding the exact value where the final balance reaches zero.

### Tax Rates by Loan Type

| Loan Type | KKDF | BSMV |
|-----------|------|------|
| Consumer  | 15%  | 15%  |
| Mortgage  | 0%   | 0%   |
| Vehicle   | 15%  | 15%  |
| SME       | 0%   | 5%   |

---

## 📁 Project Structure

```
src/main/java/com/payment/
├── Main.java                                  # CLI interface & menu system
├── FlexiblePaymentCalculator.java             # Inter-payment calculation engine
├── FlexibleDecreasingPaymentCalculator.java   # Decreasing installment engine
└── FlexibleIncreasingPaymentCalculator.java   # Increasing installment engine
```

---

## 🤝 Who Is This For?

- **QA Engineers** testing banking/loan APIs
- **Test Automation Engineers** building financial test data validators
- **Developers** implementing or debugging loan calculation services
- **Anyone** curious about how bank loan amortization actually works

---

## 📄 License

MIT License — feel free to use, modify, and distribute.

---

## 🙋‍♂️ Author

**Mahmut Cemrek**

If you find this useful, give it a ⭐ on GitHub!
