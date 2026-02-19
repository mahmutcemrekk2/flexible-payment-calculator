package com.payment;

import com.payment.FlexiblePaymentCalculator.*;
import com.payment.FlexibleDecreasingPaymentCalculator.DecreasingLoanRequest;
import com.payment.FlexibleIncreasingPaymentCalculator.IncreasingLoanRequest;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.io.PrintWriter;
import java.util.List;
import java.util.Locale;

import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.NonBlockingReader;
import org.jline.terminal.Attributes;

/**
 * Esnek Kredi Ödemesi - Terminalden çalışan hesaplama aracı.
 * İki mod destekler:
 * 1. Ara Ödemeli (Inter-Payment)
 * 2. Azalan Taksitli (Decreasing Payment)
 */
public class Main {

    private static final DecimalFormat TL_FORMAT;

    static {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.forLanguageTag("tr-TR"));
        symbols.setGroupingSeparator('.');
        symbols.setDecimalSeparator(',');
        TL_FORMAT = new DecimalFormat("#,##0.00", symbols);
    }

    // ─── Hesaplama modları ───
    enum CalculationMode {
        INTER_PAYMENT,
        DECREASING_PAYMENT,
        INCREASING_PAYMENT
    }

    public static void main(String[] args) {
        Locale.setDefault(Locale.ENGLISH);

        try (Terminal terminal = TerminalBuilder.builder().system(true).build()) {

            LineReader lineReader = LineReaderBuilder.builder()
                    .terminal(terminal)
                    .build();

            PrintWriter w = terminal.writer();

            printBanner(w);

            // ─── Mod seçimi ───
            CalculationMode mode = readEnumInteractive(terminal, "Hesaplama Modu",
                    CalculationMode.values(), CalculationMode.INTER_PAYMENT);

            w.println();
            w.flush();

            if (mode == CalculationMode.INTER_PAYMENT) {
                runInterPaymentFlow(terminal, lineReader, w);
            } else if (mode == CalculationMode.DECREASING_PAYMENT) {
                runDecreasingPaymentFlow(terminal, lineReader, w);
            } else {
                runIncreasingPaymentFlow(terminal, lineReader, w);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ═══════════════════════════════════════════════════
    // ARA ÖDEME AKIŞI (mevcut akış)
    // ═══════════════════════════════════════════════════

    private static void runInterPaymentFlow(Terminal terminal, LineReader lineReader, PrintWriter w) {
        LoanRequest req = new LoanRequest();

        req.loanType = readEnumInteractive(terminal, "Kredi Türü",
                LoanType.values(), LoanType.CONSUMER);
        w.println();
        w.flush();

        req.amount = readBigDecimal(lineReader, "Kredi Tutarı (₺)",
                new BigDecimal("100000"));
        req.maturity = readInt(lineReader, "Vade (taksit sayısı)", 12);
        req.interestRate = readBigDecimal(lineReader, "Aylık Faiz Oranı (%)",
                new BigDecimal("3.55"));

        req.paymentFrequency = readEnumInteractive(terminal, "Ödeme Sıklığı",
                PaymentFrequency.values(), PaymentFrequency.MONTHLY);
        w.println();
        w.flush();

        req.interPaymentStartInstallmentNo = readInt(lineReader,
                "Ara Ödeme Başlangıç Taksit No", 3);
        req.interPaymentFrequency = readInt(lineReader,
                "Ara Ödeme Sıklığı (kaç taksitte bir)", 3);
        req.interPaymentAmount = readBigDecimal(lineReader,
                "Ara Ödeme Tutarı (₺)", new BigDecimal("15000"));

        req.calculationMethod = readEnumInteractive(terminal, "Hesaplama Yöntemi",
                CalculationMethod.values(), CalculationMethod.ADD_TO_INSTALLMENT);
        w.println();
        w.flush();

        w.println();
        w.println("⏳ Hesaplanıyor...");
        w.println();
        w.flush();

        FlexiblePaymentCalculator calculator = new FlexiblePaymentCalculator();
        CalculationResult result = calculator.calculate(req);

        printInterPaymentSummary(w, result);
        printInterPaymentTable(w, result.rows);
        printInterPaymentTotals(w, result);
    }

    // ═══════════════════════════════════════════════════
    // AZALAN TAKSİT AKIŞI (yeni akış)
    // ═══════════════════════════════════════════════════

    private static void runDecreasingPaymentFlow(Terminal terminal, LineReader lineReader, PrintWriter w) {
        DecreasingLoanRequest req = new DecreasingLoanRequest();

        req.loanType = readEnumInteractive(terminal, "Kredi Türü",
                FlexibleDecreasingPaymentCalculator.LoanType.values(),
                FlexibleDecreasingPaymentCalculator.LoanType.CONSUMER);
        w.println();
        w.flush();

        req.amount = readBigDecimal(lineReader, "Kredi Tutarı (₺)",
                new BigDecimal("500000"));
        req.maturity = readInt(lineReader, "Vade (taksit sayısı)", 36);
        req.interestRate = readBigDecimal(lineReader, "Aylık Faiz Oranı (%)",
                new BigDecimal("4.15"));

        req.paymentFrequency = readEnumInteractive(terminal, "Ödeme Sıklığı",
                FlexibleDecreasingPaymentCalculator.PaymentFrequency.values(),
                FlexibleDecreasingPaymentCalculator.PaymentFrequency.MONTHLY);
        w.println();
        w.flush();

        req.reductionStartInstallmentNo = readInt(lineReader,
                "Azalma Başlangıç Taksit No", 6);
        req.reductionFrequency = readInt(lineReader,
                "Azalma Sıklığı (kaç taksitte bir)", 6);

        // Azalma tipi seçimi
        FlexibleDecreasingPaymentCalculator.ReductionType reductionType = readEnumInteractive(terminal, "Azalma Tipi",
                FlexibleDecreasingPaymentCalculator.ReductionType.values(),
                FlexibleDecreasingPaymentCalculator.ReductionType.RATE);
        w.println();
        w.flush();

        if (reductionType == FlexibleDecreasingPaymentCalculator.ReductionType.AMOUNT) {
            req.reductionAmount = readBigDecimal(lineReader,
                    "Azalma Miktarı (₺)", new BigDecimal("1000"));
            req.reductionRate = null;
        } else {
            req.reductionRate = readBigDecimal(lineReader,
                    "Azalma Oranı (ör: 0.05 = %5)", new BigDecimal("0.05"));
            req.reductionAmount = null;
        }

        w.println();
        w.println("⏳ Hesaplanıyor...");
        w.println();
        w.flush();

        FlexibleDecreasingPaymentCalculator calculator = new FlexibleDecreasingPaymentCalculator();
        FlexibleDecreasingPaymentCalculator.CalculationResult result = calculator.calculate(req);

        printDecreasingPaymentSummary(w, result, reductionType);
        printDecreasingPaymentTable(w, result.rows, result.installmentAmount);
        printDecreasingPaymentTotals(w, result);
    }

    // ═══════════════════════════════════════════════════
    // ARTAN TAKSİT AKIŞI (yeni akış)
    // ═══════════════════════════════════════════════════

    private static void runIncreasingPaymentFlow(Terminal terminal, LineReader lineReader, PrintWriter w) {
        IncreasingLoanRequest req = new IncreasingLoanRequest();

        req.loanType = readEnumInteractive(terminal, "Kredi Türü",
                FlexibleIncreasingPaymentCalculator.LoanType.values(),
                FlexibleIncreasingPaymentCalculator.LoanType.CONSUMER);
        w.println();
        w.flush();

        req.amount = readBigDecimal(lineReader, "Kredi Tutarı (₺)",
                new BigDecimal("500000"));
        req.maturity = readInt(lineReader, "Vade (taksit sayısı)", 36);
        req.interestRate = readBigDecimal(lineReader, "Aylık Faiz Oranı (%)",
                new BigDecimal("4.15"));

        req.paymentFrequency = readEnumInteractive(terminal, "Ödeme Sıklığı",
                FlexibleIncreasingPaymentCalculator.PaymentFrequency.values(),
                FlexibleIncreasingPaymentCalculator.PaymentFrequency.MONTHLY);
        w.println();
        w.flush();

        req.increaseStartInstallmentNo = readInt(lineReader,
                "Artış Başlangıç Taksit No", 6);
        req.increaseFrequency = readInt(lineReader,
                "Artış Sıklığı (kaç taksitte bir)", 6);

        // Artış tipi seçimi
        FlexibleIncreasingPaymentCalculator.IncreaseType increaseType = readEnumInteractive(terminal, "Artış Tipi",
                FlexibleIncreasingPaymentCalculator.IncreaseType.values(),
                FlexibleIncreasingPaymentCalculator.IncreaseType.RATE);
        w.println();
        w.flush();

        if (increaseType == FlexibleIncreasingPaymentCalculator.IncreaseType.AMOUNT) {
            req.increaseAmount = readBigDecimal(lineReader,
                    "Artış Miktarı (₺)", new BigDecimal("1000"));
            req.increaseRate = null;
        } else {
            req.increaseRate = readBigDecimal(lineReader,
                    "Artış Oranı (ör: 0.05 = %5)", new BigDecimal("0.05"));
            req.increaseAmount = null;
        }

        w.println();
        w.println("⏳ Hesaplanıyor...");
        w.println();
        w.flush();

        FlexibleIncreasingPaymentCalculator calculator = new FlexibleIncreasingPaymentCalculator();
        FlexibleIncreasingPaymentCalculator.CalculationResult result = calculator.calculate(req);

        printIncreasingPaymentSummary(w, result, increaseType);
        printIncreasingPaymentTable(w, result.rows, result.installmentAmount);
        printIncreasingPaymentTotals(w, result);
    }

    // ═══════════════════════════════════════════════════
    // BANNER
    // ═══════════════════════════════════════════════════

    private static void printBanner(PrintWriter w) {
        w.println();
        w.println("╔═══════════════════════════════════════════════╗");
        w.println("║   💰 ESNEK KREDİ ÖDEMESİ HESAPLAMA ARACI    ║");
        w.println("╠═══════════════════════════════════════════════╣");
        w.println("║  1. Ara Ödemeli   (Inter-Payment)            ║");
        w.println("║  2. Azalan Taksit (Decreasing Payment)       ║");
        w.println("║  3. Artan Taksit  (Increasing Payment)       ║");
        w.println("╚═══════════════════════════════════════════════╝");
        w.println();
        w.println("  Ok tuşlarıyla seçim yapabilir, Enter ile onaylayabilirsiniz.");
        w.println();
        w.flush();
    }

    // ═══════════════════════════════════════════════════
    // ARA ÖDEME - Ekran çıktıları
    // ═══════════════════════════════════════════════════

    private static void printInterPaymentSummary(PrintWriter w, CalculationResult result) {
        w.println("╔═══════════════════════════════════════════════╗");
        w.println("║      📋 ARA ÖDEMELİ HESAPLAMA SONUCU        ║");
        w.println("╠═══════════════════════════════════════════════╣");
        w.printf("║  Taksit Tutarı     : ₺%-22s  ║%n", formatTL(result.installmentAmount));
        w.printf("║  Ara Ödeme Tutarı  : ₺%-22s  ║%n", formatTL(result.interPaymentAmount));
        w.printf("║  Toplam Ödeme      : ₺%-22s  ║%n", formatTL(result.totalPayment));
        w.println("╚═══════════════════════════════════════════════╝");
        w.println();
        w.flush();
    }

    private static void printInterPaymentTable(PrintWriter w, List<PaymentRow> rows) {
        String header = String.format(
                "%-8s │ %14s │ %14s │ %14s │ %12s │ %12s │ %14s",
                " Taksit", "Aylık Ödeme", "  Anapara", "    Faiz",
                "   KKDF", "   BSMV", "   Bakiye");

        String separator = "─".repeat(8) + "─┼─" +
                "─".repeat(14) + "─┼─" +
                "─".repeat(14) + "─┼─" +
                "─".repeat(14) + "─┼─" +
                "─".repeat(12) + "─┼─" +
                "─".repeat(12) + "─┼─" +
                "─".repeat(14);

        w.println(separator);
        w.println(header);
        w.println(separator);

        for (PaymentRow row : rows) {
            boolean isInterPayment = row.monthlyPayment.compareTo(
                    rows.get(0).monthlyPayment) > 0;
            String marker = isInterPayment ? " *" : "  ";

            w.printf(
                    " %4d %s │ %14s │ %14s │ %14s │ %12s │ %12s │ %14s%n",
                    row.no,
                    marker,
                    formatTL(row.monthlyPayment),
                    formatTL(row.principal),
                    formatTL(row.interest),
                    formatTL(row.kkdf),
                    formatTL(row.bsmv),
                    formatTL(row.balance));
        }
        w.println(separator);
        w.println("  * = Ara ödeme içeren taksit");
        w.println();
        w.flush();
    }

    private static void printInterPaymentTotals(PrintWriter w, CalculationResult result) {
        w.println("╔═══════════════════════════════════════════════╗");
        w.println("║                📊 TOPLAMLAR                   ║");
        w.println("╠═══════════════════════════════════════════════╣");
        w.printf("║  Toplam Ödeme      : ₺%-22s  ║%n", formatTL(result.totalPayment));
        w.printf("║  Toplam Anapara    : ₺%-22s  ║%n", formatTL(result.totalPrincipal));
        w.printf("║  Toplam Faiz       : ₺%-22s  ║%n", formatTL(result.totalInterest));
        w.printf("║  Toplam KKDF       : ₺%-22s  ║%n", formatTL(result.totalKkdf));
        w.printf("║  Toplam BSMV       : ₺%-22s  ║%n", formatTL(result.totalBsmv));
        w.println("╚═══════════════════════════════════════════════╝");
        w.flush();
    }

    // ═══════════════════════════════════════════════════
    // AZALAN TAKSİT - Ekran çıktıları
    // ═══════════════════════════════════════════════════

    private static void printDecreasingPaymentSummary(PrintWriter w,
            FlexibleDecreasingPaymentCalculator.CalculationResult result,
            FlexibleDecreasingPaymentCalculator.ReductionType reductionType) {
        w.println("╔═══════════════════════════════════════════════╗");
        w.println("║    📋 AZALAN TAKSİTLİ HESAPLAMA SONUCU        ║");
        w.println("╠═══════════════════════════════════════════════╣");
        w.printf("║  İlk Taksit Tutarı : ₺%-22s  ║%n", formatTL(result.installmentAmount));
        w.printf("║  Azalma Tipi       : %-24s  ║%n",
                reductionType == FlexibleDecreasingPaymentCalculator.ReductionType.AMOUNT
                        ? "Miktar Bazlı"
                        : "Oran Bazlı");
        w.printf("║  Toplam Ödeme      : ₺%-22s  ║%n", formatTL(result.totalPayment));
        w.println("╚═══════════════════════════════════════════════╝");
        w.println();
        w.flush();
    }

    private static void printDecreasingPaymentTable(PrintWriter w,
            List<FlexibleDecreasingPaymentCalculator.PaymentRow> rows,
            BigDecimal baseInstallment) {

        String header = String.format(
                "%-8s │ %14s │ %14s │ %14s │ %12s │ %12s │ %14s",
                " Taksit", "Aylık Ödeme", "  Anapara", "    Faiz",
                "   KKDF", "   BSMV", "   Bakiye");

        String separator = "─".repeat(8) + "─┼─" +
                "─".repeat(14) + "─┼─" +
                "─".repeat(14) + "─┼─" +
                "─".repeat(14) + "─┼─" +
                "─".repeat(12) + "─┼─" +
                "─".repeat(12) + "─┼─" +
                "─".repeat(14);

        w.println(separator);
        w.println(header);
        w.println(separator);

        BigDecimal prevPayment = null;
        for (FlexibleDecreasingPaymentCalculator.PaymentRow row : rows) {
            // Azalma işareti: taksit tutarı bir öncekinden düştüyse
            String marker = "  ";
            if (prevPayment != null
                    && row.monthlyPayment.compareTo(prevPayment) < 0
                    && row.monthlyPayment.compareTo(BigDecimal.ZERO) > 0) {
                marker = " ↓";
            }
            prevPayment = row.monthlyPayment;

            w.printf(
                    " %4d %s │ %14s │ %14s │ %14s │ %12s │ %12s │ %14s%n",
                    row.no,
                    marker,
                    formatTL(row.monthlyPayment),
                    formatTL(row.principal),
                    formatTL(row.interest),
                    formatTL(row.kkdf),
                    formatTL(row.bsmv),
                    formatTL(row.balance));
        }
        w.println(separator);
        w.println("  ↓ = Taksit azalma noktası");
        w.println();
        w.flush();
    }

    private static void printDecreasingPaymentTotals(PrintWriter w,
            FlexibleDecreasingPaymentCalculator.CalculationResult result) {
        w.println("╔═══════════════════════════════════════════════╗");
        w.println("║                📊 TOPLAMLAR                   ║");
        w.println("╠═══════════════════════════════════════════════╣");
        w.printf("║  Toplam Ödeme      : ₺%-22s  ║%n", formatTL(result.totalPayment));
        w.printf("║  Toplam Anapara    : ₺%-22s  ║%n", formatTL(result.totalPrincipal));
        w.printf("║  Toplam Faiz       : ₺%-22s  ║%n", formatTL(result.totalInterest));
        w.printf("║  Toplam KKDF       : ₺%-22s  ║%n", formatTL(result.totalKkdf));
        w.printf("║  Toplam BSMV       : ₺%-22s  ║%n", formatTL(result.totalBsmv));
        w.println("╚═══════════════════════════════════════════════╝");
        w.flush();
    }

    // ═══════════════════════════════════════════════════
    // ARTAN TAKSİT - Ekran çıktıları
    // ═══════════════════════════════════════════════════

    private static void printIncreasingPaymentSummary(PrintWriter w,
            FlexibleIncreasingPaymentCalculator.CalculationResult result,
            FlexibleIncreasingPaymentCalculator.IncreaseType increaseType) {
        w.println("╔═══════════════════════════════════════════════╗");
        w.println("║    📋 ARTAN TAKSİTLİ HESAPLAMA SONUCU        ║");
        w.println("╠═══════════════════════════════════════════════╣");
        w.printf("║  İlk Taksit Tutarı : ₺%-22s  ║%n", formatTL(result.installmentAmount));
        w.printf("║  Artış Tipi        : %-24s  ║%n",
                increaseType == FlexibleIncreasingPaymentCalculator.IncreaseType.AMOUNT
                        ? "Miktar Bazlı"
                        : "Oran Bazlı");
        w.printf("║  Toplam Ödeme      : ₺%-22s  ║%n", formatTL(result.totalPayment));
        w.println("╚═══════════════════════════════════════════════╝");
        w.println();
        w.flush();
    }

    private static void printIncreasingPaymentTable(PrintWriter w,
            List<FlexibleIncreasingPaymentCalculator.PaymentRow> rows,
            BigDecimal baseInstallment) {

        String header = String.format(
                "%-8s │ %14s │ %14s │ %14s │ %12s │ %12s │ %14s",
                " Taksit", "Aylık Ödeme", "  Anapara", "    Faiz",
                "   KKDF", "   BSMV", "   Bakiye");

        String separator = "─".repeat(8) + "─┼─" +
                "─".repeat(14) + "─┼─" +
                "─".repeat(14) + "─┼─" +
                "─".repeat(14) + "─┼─" +
                "─".repeat(12) + "─┼─" +
                "─".repeat(12) + "─┼─" +
                "─".repeat(14);

        w.println(separator);
        w.println(header);
        w.println(separator);

        BigDecimal prevPayment = null;
        for (FlexibleIncreasingPaymentCalculator.PaymentRow row : rows) {
            // Artış işareti: taksit tutarı bir öncekinden arttıysa
            String marker = "  ";
            if (prevPayment != null
                    && row.monthlyPayment.compareTo(prevPayment) > 0
                    && prevPayment.compareTo(BigDecimal.ZERO) > 0) {
                marker = " ↑";
            }
            prevPayment = row.monthlyPayment;

            w.printf(
                    " %4d %s │ %14s │ %14s │ %14s │ %12s │ %12s │ %14s%n",
                    row.no,
                    marker,
                    formatTL(row.monthlyPayment),
                    formatTL(row.principal),
                    formatTL(row.interest),
                    formatTL(row.kkdf),
                    formatTL(row.bsmv),
                    formatTL(row.balance));
        }
        w.println(separator);
        w.println("  ↑ = Taksit artış noktası");
        w.println();
        w.flush();
    }

    private static void printIncreasingPaymentTotals(PrintWriter w,
            FlexibleIncreasingPaymentCalculator.CalculationResult result) {
        w.println("╔═══════════════════════════════════════════════╗");
        w.println("║                📊 TOPLAMLAR                   ║");
        w.println("╠═══════════════════════════════════════════════╣");
        w.printf("║  Toplam Ödeme      : ₺%-22s  ║%n", formatTL(result.totalPayment));
        w.printf("║  Toplam Anapara    : ₺%-22s  ║%n", formatTL(result.totalPrincipal));
        w.printf("║  Toplam Faiz       : ₺%-22s  ║%n", formatTL(result.totalInterest));
        w.printf("║  Toplam KKDF       : ₺%-22s  ║%n", formatTL(result.totalKkdf));
        w.printf("║  Toplam BSMV       : ₺%-22s  ║%n", formatTL(result.totalBsmv));
        w.println("╚═══════════════════════════════════════════════╝");
        w.flush();
    }

    // ═══════════════════════════════════════════════════
    // INPUT HELPERS
    // ═══════════════════════════════════════════════════

    private static BigDecimal readBigDecimal(LineReader reader, String label, BigDecimal defaultVal) {
        String prompt = String.format("  %s [%s]: ", label, defaultVal.toPlainString());
        reader.getTerminal().writer().flush();
        String input = reader.readLine(prompt).trim();
        if (input.isEmpty())
            return defaultVal;
        try {
            return new BigDecimal(input.replace(",", "."));
        } catch (NumberFormatException e) {
            reader.getTerminal().writer().println("  ⚠ Geçersiz değer, varsayılan kullanılıyor: " + defaultVal);
            return defaultVal;
        }
    }

    private static int readInt(LineReader reader, String label, int defaultVal) {
        String prompt = String.format("  %s [%d]: ", label, defaultVal);
        reader.getTerminal().writer().flush();
        String input = reader.readLine(prompt).trim();
        if (input.isEmpty())
            return defaultVal;
        try {
            return Integer.parseInt(input);
        } catch (NumberFormatException e) {
            reader.getTerminal().writer().println("  ⚠ Geçersiz değer, varsayılan kullanılıyor: " + defaultVal);
            return defaultVal;
        }
    }

    private static <E extends Enum<E>> E readEnumInteractive(Terminal terminal, String label, E[] values,
            E defaultVal) {
        PrintWriter w = terminal.writer();
        w.println("  " + label + ": (Seçim için ↑/↓ oklarını kullanın, Enter ile seçin)");
        w.flush();

        w.print("\033[?25l");
        w.flush();

        Attributes originalAttributes = terminal.enterRawMode();

        int selectedIndex = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i] == defaultVal) {
                selectedIndex = i;
                break;
            }
        }

        try {
            NonBlockingReader reader = terminal.reader();
            boolean firstDraw = true;

            while (true) {
                if (!firstDraw) {
                    w.print("\033[" + values.length + "A");
                }

                for (int i = 0; i < values.length; i++) {
                    w.print("\033[2K\r");

                    String displayName = getEnumDisplayName(values[i]);
                    if (i == selectedIndex) {
                        w.println("\u001B[32m> " + displayName + " \u001B[0m");
                    } else {
                        w.println("  " + displayName + " ");
                    }
                }
                w.flush();
                firstDraw = false;

                int c = reader.read();

                if (c == 13) {
                    break;
                } else if (c == 27) {
                    int c2 = reader.read();
                    if (c2 == 91) {
                        int c3 = reader.read();
                        if (c3 == 65) {
                            if (selectedIndex > 0)
                                selectedIndex--;
                        } else if (c3 == 66) {
                            if (selectedIndex < values.length - 1)
                                selectedIndex++;
                        }
                    }
                }
            }
        } catch (java.io.IOException e) {
            e.printStackTrace();
        } finally {
            terminal.setAttributes(originalAttributes);
            w.print("\033[?25h");
            w.flush();
        }

        return values[selectedIndex];
    }

    /**
     * Enum değerlerini kullanıcı dostu Türkçe gösterim adlarına çevirir.
     */
    private static <E extends Enum<E>> String getEnumDisplayName(E value) {
        String name = value.name();
        return switch (name) {
            case "INTER_PAYMENT" -> "ARA ÖDEME (Inter-Payment)";
            case "DECREASING_PAYMENT" -> "AZALAN TAKSİT (Decreasing Payment)";
            case "INCREASING_PAYMENT" -> "ARTAN TAKSİT (Increasing Payment)";
            case "AMOUNT" -> "MİKTAR BAZLI";
            case "RATE" -> "ORAN BAZLI";
            default -> name;
        };
    }

    private static String formatTL(BigDecimal value) {
        return TL_FORMAT.format(value);
    }
}
