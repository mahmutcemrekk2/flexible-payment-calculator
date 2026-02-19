package com.payment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Esnek Azalan Taksitli Ödeme Hesaplayıcı.
 * Taksit tutarı belirli aralıklarla miktar veya oran bazlı azaltılır.
 * BigDecimal + ROUND_HALF_UP(2 digit) kullanır.
 */
public class FlexibleDecreasingPaymentCalculator {

    private static final int SCALE = 2;
    private static final RoundingMode RM = RoundingMode.HALF_UP;

    // ─── Enum'ları mevcut calculator ile paylaşıyoruz ───
    public enum LoanType {
        CONSUMER, MORTGAGE, VEHICLE, SME
    }

    public enum PaymentFrequency {
        MONTHLY, THREE_MONTHLY, SIX_MONTHLY, YEARLY
    }

    public enum ReductionType {
        AMOUNT, RATE
    }

    // ─── Request modeli ───
    public static class DecreasingLoanRequest {
        public LoanType loanType;
        public BigDecimal amount;
        public int maturity;
        public BigDecimal interestRate; // ör: 4.15
        public PaymentFrequency paymentFrequency;
        public int reductionStartInstallmentNo;
        public int reductionFrequency;
        public BigDecimal reductionAmount; // miktar bazlı (nullable)
        public BigDecimal reductionRate; // oran bazlı (nullable, ör: 0.05)
    }

    // ─── Satır modeli ───
    public static class PaymentRow {
        public int no;
        public BigDecimal monthlyPayment;
        public BigDecimal principal;
        public BigDecimal interest;
        public BigDecimal kkdf; // RUSF
        public BigDecimal bsmv; // BITT
        public BigDecimal balance;
    }

    // ─── Sonuç modeli ───
    public static class CalculationResult {
        public BigDecimal installmentAmount; // İlk taksit tutarı
        public BigDecimal totalPayment;
        public BigDecimal totalPrincipal;
        public BigDecimal totalInterest;
        public BigDecimal totalKkdf;
        public BigDecimal totalBsmv;
        public List<PaymentRow> rows;
    }

    // ─── Yardımcı metodlar ───

    private static BigDecimal round(BigDecimal val) {
        return val.setScale(SCALE, RM);
    }

    private static BigDecimal[] getTaxRates(LoanType loanType) {
        return switch (loanType) {
            case MORTGAGE -> new BigDecimal[] { BigDecimal.ZERO, BigDecimal.ZERO };
            case SME -> new BigDecimal[] { BigDecimal.ZERO, new BigDecimal("0.05") };
            default -> new BigDecimal[] { new BigDecimal("0.15"), new BigDecimal("0.15") };
        };
    }

    private static int getPaymentInterval(PaymentFrequency freq) {
        return switch (freq) {
            case THREE_MONTHLY -> 3;
            case SIX_MONTHLY -> 6;
            case YEARLY -> 12;
            default -> 1;
        };
    }

    private static boolean isPaymentMonth(int month, int interval) {
        return interval == 1 || month % interval == 0;
    }

    /**
     * Azalma noktası mı kontrol eder.
     * paymentNumber taksit numarası (1-based, sadece ödeme yapılan aylar sayılır).
     */
    private static boolean isReductionPoint(int paymentNumber, int start, int frequency) {
        return paymentNumber >= start && (paymentNumber - start) % frequency == 0;
    }

    // ─── Ana hesaplama ───

    public CalculationResult calculate(DecreasingLoanRequest req) {
        BigDecimal monthlyRate = req.interestRate.divide(BigDecimal.valueOf(100), 10, RM);
        BigDecimal[] taxRates = getTaxRates(req.loanType);
        BigDecimal kkdfRate = taxRates[0];
        BigDecimal bsmvRate = taxRates[1];
        int payInterval = getPaymentInterval(req.paymentFrequency);

        BigDecimal baseInstallment = findInstallment(
                req.amount, monthlyRate, req.maturity,
                kkdfRate, bsmvRate,
                req.reductionStartInstallmentNo,
                req.reductionFrequency,
                req.reductionAmount,
                req.reductionRate,
                payInterval);

        List<PaymentRow> rows = generatePlan(
                req.amount, monthlyRate, req.maturity,
                kkdfRate, bsmvRate,
                req.reductionStartInstallmentNo,
                req.reductionFrequency,
                req.reductionAmount,
                req.reductionRate,
                baseInstallment,
                payInterval);

        BigDecimal totalPayment = BigDecimal.ZERO;
        BigDecimal totalPrincipal = BigDecimal.ZERO;
        BigDecimal totalInterest = BigDecimal.ZERO;
        BigDecimal totalKkdf = BigDecimal.ZERO;
        BigDecimal totalBsmv = BigDecimal.ZERO;

        for (PaymentRow row : rows) {
            totalPayment = totalPayment.add(row.monthlyPayment);
            totalPrincipal = totalPrincipal.add(row.principal);
            totalInterest = totalInterest.add(row.interest);
            totalKkdf = totalKkdf.add(row.kkdf);
            totalBsmv = totalBsmv.add(row.bsmv);
        }

        CalculationResult result = new CalculationResult();
        result.installmentAmount = baseInstallment;
        result.totalPayment = round(totalPayment);
        result.totalPrincipal = round(totalPrincipal);
        result.totalInterest = round(totalInterest);
        result.totalKkdf = round(totalKkdf);
        result.totalBsmv = round(totalBsmv);
        result.rows = rows;
        return result;
    }

    // ─── Binary search ile ilk taksit tutarını bulma ───

    private BigDecimal findInstallment(
            BigDecimal principal, BigDecimal monthlyRate, int maturity,
            BigDecimal kkdfRate, BigDecimal bsmvRate,
            int redStart, int redFreq,
            BigDecimal redAmount, BigDecimal redRate,
            int payInterval) {

        BigDecimal low = BigDecimal.ONE;
        BigDecimal high = principal.multiply(BigDecimal.valueOf(5));

        // High-precision binary search
        for (int iter = 0; iter < 300; iter++) {
            BigDecimal mid = low.add(high).divide(BigDecimal.valueOf(2), 10, RM);
            BigDecimal finalBalance = simulateBalance(
                    principal, monthlyRate, maturity,
                    kkdfRate, bsmvRate,
                    redStart, redFreq, redAmount, redRate,
                    mid, payInterval);

            if (finalBalance.compareTo(BigDecimal.ZERO) > 0) {
                low = mid;
            } else {
                high = mid;
            }

            if (high.subtract(low).compareTo(new BigDecimal("0.0000001")) < 0)
                break;
        }

        BigDecimal rawMid = low.add(high).divide(BigDecimal.valueOf(2), 10, RM);

        // İki aday dene: floor ve ceil (2 ondalık)
        BigDecimal candidateDown = rawMid.setScale(SCALE, RoundingMode.FLOOR);
        BigDecimal candidateUp = candidateDown.add(new BigDecimal("0.01"));

        BigDecimal balDown = simulateBalance(principal, monthlyRate, maturity,
                kkdfRate, bsmvRate, redStart, redFreq, redAmount, redRate,
                candidateDown, payInterval);
        BigDecimal balUp = simulateBalance(principal, monthlyRate, maturity,
                kkdfRate, bsmvRate, redStart, redFreq, redAmount, redRate,
                candidateUp, payInterval);

        // API kuralı: büyük taksit seçilir (hafif fazla ödeme).
        // Son taksit düşük kalarak bakiyeyi sıfırlar.
        boolean downNeg = balDown.compareTo(BigDecimal.ZERO) < 0;
        boolean upNeg = balUp.compareTo(BigDecimal.ZERO) < 0;

        if (!downNeg && upNeg) {
            // down pozitif, up negatif → up tercih (API konvansiyonu)
            return candidateUp;
        } else if (downNeg && !upNeg) {
            return candidateDown;
        } else {
            return balDown.abs().compareTo(balUp.abs()) <= 0 ? candidateDown : candidateUp;
        }
    }

    // ─── Simülasyon (bakiye hesaplama) ───

    private BigDecimal simulateBalance(
            BigDecimal principal, BigDecimal monthlyRate, int maturity,
            BigDecimal kkdfRate, BigDecimal bsmvRate,
            int redStart, int redFreq,
            BigDecimal redAmount, BigDecimal redRate,
            BigDecimal baseInstallment, int payInterval) {

        BigDecimal balance = principal;
        BigDecimal currentInstallment = baseInstallment;
        int paymentCount = 0;

        for (int i = 1; i <= maturity; i++) {
            BigDecimal interest = round(balance.multiply(monthlyRate));
            BigDecimal kkdf = round(interest.multiply(kkdfRate));
            BigDecimal bsmv = round(interest.multiply(bsmvRate));

            if (!isPaymentMonth(i, payInterval)) {
                balance = round(balance.add(interest).add(kkdf).add(bsmv));
            } else {
                paymentCount++;

                if (isReductionPoint(paymentCount, redStart, redFreq)) {
                    if (redRate != null) {
                        currentInstallment = round(currentInstallment.multiply(
                                BigDecimal.ONE.subtract(redRate)));
                    } else if (redAmount != null) {
                        currentInstallment = round(currentInstallment.subtract(redAmount));
                    }
                }

                // Tek yuvarlama: bakiye = round(bakiye - taksit + faiz + kkdf + bsmv)
                balance = round(balance.subtract(currentInstallment).add(interest).add(kkdf).add(bsmv));
            }
        }

        return balance;
    }

    // ─── Ödeme planı oluşturma ───

    private List<PaymentRow> generatePlan(
            BigDecimal principal, BigDecimal monthlyRate, int maturity,
            BigDecimal kkdfRate, BigDecimal bsmvRate,
            int redStart, int redFreq,
            BigDecimal redAmount, BigDecimal redRate,
            BigDecimal baseInstallment, int payInterval) {

        List<PaymentRow> rows = new ArrayList<>();
        BigDecimal balance = principal;
        BigDecimal currentInstallment = baseInstallment;
        int paymentCount = 0;

        for (int i = 1; i <= maturity; i++) {
            BigDecimal interest = round(balance.multiply(monthlyRate));
            BigDecimal kkdf = round(interest.multiply(kkdfRate));
            BigDecimal bsmv = round(interest.multiply(bsmvRate));

            BigDecimal principalPart;
            BigDecimal actualInstallment;

            if (!isPaymentMonth(i, payInterval)) {
                principalPart = round(interest.add(kkdf).add(bsmv).negate());
                balance = round(balance.add(interest).add(kkdf).add(bsmv));
                actualInstallment = BigDecimal.ZERO;
            } else {
                paymentCount++;

                if (isReductionPoint(paymentCount, redStart, redFreq)) {
                    if (redRate != null) {
                        currentInstallment = round(currentInstallment.multiply(
                                BigDecimal.ONE.subtract(redRate)));
                    } else if (redAmount != null) {
                        currentInstallment = round(currentInstallment.subtract(redAmount));
                    }
                }

                actualInstallment = currentInstallment;

                if (i == maturity) {
                    principalPart = balance;
                    actualInstallment = round(balance.add(interest).add(kkdf).add(bsmv));
                    balance = BigDecimal.ZERO;
                } else {
                    // Tek yuvarlama ile bakiye hesabı (double-rounding önlenir)
                    BigDecimal newBalance = round(
                            balance.subtract(actualInstallment).add(interest).add(kkdf).add(bsmv));
                    principalPart = balance.subtract(newBalance);
                    balance = newBalance;

                    if (balance.compareTo(BigDecimal.ZERO) < 0) {
                        principalPart = principalPart.add(balance);
                        actualInstallment = round(principalPart.add(interest).add(kkdf).add(bsmv));
                        balance = BigDecimal.ZERO;
                    }
                }
            }

            PaymentRow row = new PaymentRow();
            row.no = i;
            row.interest = interest;
            row.kkdf = kkdf;
            row.bsmv = bsmv;
            row.principal = round(principalPart);
            row.monthlyPayment = round(principalPart.add(interest).add(kkdf).add(bsmv));
            row.balance = balance;
            rows.add(row);

            if (balance.compareTo(BigDecimal.ZERO) == 0 && i < maturity) {
                break;
            }
        }

        return rows;
    }
}
