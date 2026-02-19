package com.payment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * BigDecimal + ROUND_HALF_UP(2 digit) kullanır.
 */
public class FlexiblePaymentCalculator {

    private static final int SCALE = 2;
    private static final RoundingMode RM = RoundingMode.HALF_UP;

    public enum LoanType {
        CONSUMER, MORTGAGE, VEHICLE, SME
    }

    public enum PaymentFrequency {
        MONTHLY, THREE_MONTHLY, SIX_MONTHLY, YEARLY
    }

    public enum CalculationMethod {
        ADD_TO_INSTALLMENT, SET_AS_INSTALLMENT
    }

    public static class LoanRequest {
        public LoanType loanType;
        public BigDecimal amount;
        public int maturity;
        public BigDecimal interestRate; // ör: 3.55
        public PaymentFrequency paymentFrequency;
        public int interPaymentStartInstallmentNo;
        public int interPaymentFrequency;
        public BigDecimal interPaymentAmount;
        public CalculationMethod calculationMethod;
    }

    public static class PaymentRow {
        public int no;
        public BigDecimal monthlyPayment;
        public BigDecimal principal;
        public BigDecimal interest;
        public BigDecimal kkdf;
        public BigDecimal bsmv;
        public BigDecimal balance;
    }

    public static class CalculationResult {
        public BigDecimal installmentAmount;
        public BigDecimal interPaymentAmount;
        public BigDecimal totalPayment;
        public BigDecimal totalPrincipal;
        public BigDecimal totalInterest;
        public BigDecimal totalKkdf;
        public BigDecimal totalBsmv;
        public List<PaymentRow> rows;
    }

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

    public CalculationResult calculate(LoanRequest req) {
        BigDecimal monthlyRate = req.interestRate.divide(BigDecimal.valueOf(100), 10, RM);
        BigDecimal[] taxRates = getTaxRates(req.loanType);
        BigDecimal kkdfRate = taxRates[0];
        BigDecimal bsmvRate = taxRates[1];
        int payInterval = getPaymentInterval(req.paymentFrequency);

        BigDecimal installment = findInstallment(
                req.amount, monthlyRate, req.maturity,
                kkdfRate, bsmvRate,
                req.interPaymentStartInstallmentNo,
                req.interPaymentFrequency,
                req.interPaymentAmount,
                req.calculationMethod,
                payInterval);

        List<PaymentRow> rows = generatePlan(
                req.amount, monthlyRate, req.maturity,
                kkdfRate, bsmvRate,
                req.interPaymentStartInstallmentNo,
                req.interPaymentFrequency,
                req.interPaymentAmount,
                req.calculationMethod,
                installment,
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
        result.installmentAmount = installment;
        result.interPaymentAmount = req.interPaymentAmount;
        result.totalPayment = round(totalPayment);
        result.totalPrincipal = round(totalPrincipal);
        result.totalInterest = round(totalInterest);
        result.totalKkdf = round(totalKkdf);
        result.totalBsmv = round(totalBsmv);
        result.rows = rows;
        return result;
    }

    private BigDecimal findInstallment(
            BigDecimal principal, BigDecimal monthlyRate, int maturity,
            BigDecimal kkdfRate, BigDecimal bsmvRate,
            int interStart, int interFreq, BigDecimal interAmount,
            CalculationMethod method, int payInterval) {

        BigDecimal low = BigDecimal.ONE;
        BigDecimal high = principal.multiply(BigDecimal.valueOf(5));

        for (int iter = 0; iter < 200; iter++) {
            BigDecimal mid = low.add(high).divide(BigDecimal.valueOf(2), 10, RM);
            BigDecimal finalBalance = simulateBalance(
                    principal, monthlyRate, maturity,
                    kkdfRate, bsmvRate, interStart, interFreq, interAmount,
                    method, mid, payInterval);

            if (finalBalance.abs().compareTo(new BigDecimal("0.005")) < 0)
                break;

            if (finalBalance.compareTo(BigDecimal.ZERO) > 0) {
                low = mid;
            } else {
                high = mid;
            }
        }

        return round(low.add(high).divide(BigDecimal.valueOf(2), 10, RM));
    }

    private BigDecimal simulateBalance(
            BigDecimal principal, BigDecimal monthlyRate, int maturity,
            BigDecimal kkdfRate, BigDecimal bsmvRate,
            int interStart, int interFreq, BigDecimal interAmount,
            CalculationMethod method, BigDecimal installment, int payInterval) {

        BigDecimal balance = principal;
        int paymentCount = 0;

        for (int i = 1; i <= maturity; i++) {
            BigDecimal interest = round(balance.multiply(monthlyRate));
            BigDecimal kkdf = round(interest.multiply(kkdfRate));
            BigDecimal bsmv = round(interest.multiply(bsmvRate));

            if (!isPaymentMonth(i, payInterval)) {
                // Ödeme yapılmayan ay: faiz + vergiler bakiyeye eklenir
                balance = round(balance.add(interest).add(kkdf).add(bsmv));
            } else {
                paymentCount++;
                BigDecimal principalPart;
                BigDecimal extra = BigDecimal.ZERO;

                if (method == CalculationMethod.ADD_TO_INSTALLMENT) {
                    principalPart = round(installment.subtract(interest).subtract(kkdf).subtract(bsmv));
                    if (isInterPaymentByCount(paymentCount, interStart, interFreq)) {
                        extra = interAmount;
                    }
                } else {
                    BigDecimal currentInst = isInterPaymentByCount(paymentCount, interStart, interFreq)
                            ? interAmount
                            : installment;
                    principalPart = round(currentInst.subtract(interest).subtract(kkdf).subtract(bsmv));
                }

                balance = round(balance.subtract(principalPart).subtract(extra));
            }
        }

        return balance;
    }

    private List<PaymentRow> generatePlan(
            BigDecimal principal, BigDecimal monthlyRate, int maturity,
            BigDecimal kkdfRate, BigDecimal bsmvRate,
            int interStart, int interFreq, BigDecimal interAmount,
            CalculationMethod method, BigDecimal installment, int payInterval) {

        List<PaymentRow> rows = new ArrayList<>();
        BigDecimal balance = principal;
        int paymentCount = 0;

        for (int i = 1; i <= maturity; i++) {
            BigDecimal interest = round(balance.multiply(monthlyRate));
            BigDecimal kkdf = round(interest.multiply(kkdfRate));
            BigDecimal bsmv = round(interest.multiply(bsmvRate));

            BigDecimal principalPart;
            BigDecimal extra = BigDecimal.ZERO;
            BigDecimal currentInstallment;

            if (!isPaymentMonth(i, payInterval)) {
                // Ödeme yapılmayan ay: faiz bakiyeye eklenir (kapitalize)
                principalPart = round(interest.add(kkdf).add(bsmv).negate());
                balance = round(balance.add(interest).add(kkdf).add(bsmv));
                currentInstallment = BigDecimal.ZERO;
            } else {
                paymentCount++;
                currentInstallment = installment;

                if (method == CalculationMethod.ADD_TO_INSTALLMENT) {
                    principalPart = round(installment.subtract(interest).subtract(kkdf).subtract(bsmv));
                    if (isInterPaymentByCount(paymentCount, interStart, interFreq)) {
                        extra = interAmount;
                    }
                } else {
                    if (isInterPaymentByCount(paymentCount, interStart, interFreq)) {
                        currentInstallment = interAmount;
                    }
                    principalPart = round(currentInstallment.subtract(interest).subtract(kkdf).subtract(bsmv));
                }

                balance = round(balance.subtract(principalPart).subtract(extra));

                if (balance.compareTo(BigDecimal.ZERO) < 0) {
                    principalPart = principalPart.add(balance);
                    balance = BigDecimal.ZERO;
                }
            }

            PaymentRow row = new PaymentRow();
            row.no = i;
            row.interest = interest;
            row.kkdf = kkdf;
            row.bsmv = bsmv;
            row.principal = round(principalPart.add(extra));
            row.monthlyPayment = round(principalPart.add(extra).add(interest).add(kkdf).add(bsmv));
            row.balance = balance;
            rows.add(row);

            if (balance.compareTo(BigDecimal.ZERO) == 0 && i < maturity) {
                break;
            }
        }

        return rows;
    }

    private boolean isInterPaymentByCount(int paymentNumber, int start, int frequency) {
        return paymentNumber >= start && (paymentNumber - start) % frequency == 0;
    }
}
