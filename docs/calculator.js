/**
 * Flexible Payment Calculator — JS Engine
 * Ports the Java BigDecimal logic using plain JS numbers with 2-decimal rounding.
 * Banking-grade: ROUND_HALF_UP on every intermediate step.
 */

// ─── Rounding Helper ───
function round2(val) {
    return Math.round((val + Number.EPSILON) * 100) / 100;
}

// ─── Tax Rates ───
function getTaxRates(loanType) {
    switch (loanType) {
        case 'MORTGAGE': return { kkdf: 0, bsmv: 0 };
        case 'SME': return { kkdf: 0, bsmv: 0.05 };
        default: return { kkdf: 0.15, bsmv: 0.15 };
    }
}

// ─── Payment Interval ───
function getPaymentInterval(freq) {
    switch (freq) {
        case 'THREE_MONTHLY': return 3;
        case 'SIX_MONTHLY': return 6;
        case 'YEARLY': return 12;
        default: return 1;
    }
}

function isPaymentMonth(month, interval) {
    return interval === 1 || month % interval === 0;
}

// ─── Format ───
function formatTL(val) {
    return val.toLocaleString('tr-TR', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

// ═══════════════════════════════════════════════
// MODE 1: INTER-PAYMENT (Ara Ödemeli)
// ═══════════════════════════════════════════════

function calculateInterPayment(params) {
    const { amount, maturity, interestRate, loanType, paymentFrequency,
        interPaymentStart, interPaymentFrequency, interPaymentAmount,
        calculationMethod } = params;

    const monthlyRate = interestRate / 100;
    const taxes = getTaxRates(loanType);
    const payInterval = getPaymentInterval(paymentFrequency);

    const installment = findInstallmentInterPayment(
        amount, monthlyRate, maturity, taxes,
        interPaymentStart, interPaymentFrequency, interPaymentAmount,
        calculationMethod, payInterval
    );

    const rows = generatePlanInterPayment(
        amount, monthlyRate, maturity, taxes,
        interPaymentStart, interPaymentFrequency, interPaymentAmount,
        calculationMethod, installment, payInterval
    );

    return buildResult(installment, rows);
}

function findInstallmentInterPayment(principal, monthlyRate, maturity, taxes,
    ipStart, ipFreq, ipAmount, calcMethod, payInterval) {

    let low = 1, high = principal * 5;
    for (let i = 0; i < 300; i++) {
        const mid = (low + high) / 2;
        const bal = simulateInterPayment(principal, monthlyRate, maturity, taxes,
            ipStart, ipFreq, ipAmount, calcMethod, mid, payInterval);
        if (bal > 0) low = mid; else high = mid;
        if (high - low < 0.0000001) break;
    }
    const raw = (low + high) / 2;
    const cDown = Math.floor(raw * 100) / 100;
    const cUp = cDown + 0.01;
    const bDown = simulateInterPayment(principal, monthlyRate, maturity, taxes,
        ipStart, ipFreq, ipAmount, calcMethod, cDown, payInterval);
    const bUp = simulateInterPayment(principal, monthlyRate, maturity, taxes,
        ipStart, ipFreq, ipAmount, calcMethod, cUp, payInterval);

    if (bDown >= 0 && bUp < 0) return cUp;
    if (bDown < 0 && bUp >= 0) return cDown;
    return Math.abs(bDown) <= Math.abs(bUp) ? cDown : cUp;
}

function simulateInterPayment(principal, monthlyRate, maturity, taxes,
    ipStart, ipFreq, ipAmount, calcMethod, installment, payInterval) {

    let balance = principal;
    let paymentCount = 0;

    for (let i = 1; i <= maturity; i++) {
        const interest = round2(balance * monthlyRate);
        const kkdf = round2(interest * taxes.kkdf);
        const bsmv = round2(interest * taxes.bsmv);

        if (!isPaymentMonth(i, payInterval)) {
            balance = round2(balance + interest + kkdf + bsmv);
        } else {
            paymentCount++;
            let curInstallment = installment;

            const isIP = paymentCount >= ipStart && (paymentCount - ipStart) % ipFreq === 0;
            if (isIP) {
                if (calcMethod === 'ADD_TO_INSTALLMENT') {
                    curInstallment = installment;
                    balance = round2(balance - curInstallment - ipAmount + interest + kkdf + bsmv);
                    continue;
                } else {
                    curInstallment = ipAmount;
                }
            }
            balance = round2(balance - curInstallment + interest + kkdf + bsmv);
        }
    }
    return balance;
}

function generatePlanInterPayment(principal, monthlyRate, maturity, taxes,
    ipStart, ipFreq, ipAmount, calcMethod, installment, payInterval) {

    const rows = [];
    let balance = principal;
    let paymentCount = 0;

    for (let i = 1; i <= maturity; i++) {
        const interest = round2(balance * monthlyRate);
        const kkdf = round2(interest * taxes.kkdf);
        const bsmv = round2(interest * taxes.bsmv);
        let principalPart, actualInstallment, isIP = false;

        if (!isPaymentMonth(i, payInterval)) {
            principalPart = round2(-(interest + kkdf + bsmv));
            balance = round2(balance + interest + kkdf + bsmv);
            actualInstallment = 0;
        } else {
            paymentCount++;
            isIP = paymentCount >= ipStart && (paymentCount - ipStart) % ipFreq === 0;
            let curInstallment = installment;
            let extraPrincipal = 0;

            if (isIP) {
                if (calcMethod === 'ADD_TO_INSTALLMENT') {
                    extraPrincipal = ipAmount;
                } else {
                    curInstallment = ipAmount;
                }
            }

            if (i === maturity) {
                principalPart = balance;
                actualInstallment = round2(balance + interest + kkdf + bsmv);
                balance = 0;
            } else {
                const newBalance = round2(balance - curInstallment - extraPrincipal + interest + kkdf + bsmv);
                principalPart = round2(balance - newBalance);
                balance = newBalance;
                actualInstallment = round2(principalPart + interest + kkdf + bsmv);

                if (balance < 0) {
                    principalPart = round2(principalPart + balance);
                    actualInstallment = round2(principalPart + interest + kkdf + bsmv);
                    balance = 0;
                }
            }
        }

        rows.push({
            no: i,
            monthlyPayment: round2(actualInstallment),
            principal: round2(principalPart),
            interest, kkdf, bsmv,
            balance: round2(balance),
            isInterPayment: isIP
        });

        if (balance === 0 && i < maturity) break;
    }
    return rows;
}

// ═══════════════════════════════════════════════
// MODE 2: DECREASING PAYMENT (Azalan Taksit)
// ═══════════════════════════════════════════════

function calculateDecreasing(params) {
    const { amount, maturity, interestRate, loanType, paymentFrequency,
        reductionStart, reductionFrequency, reductionAmount, reductionRate } = params;

    const monthlyRate = interestRate / 100;
    const taxes = getTaxRates(loanType);
    const payInterval = getPaymentInterval(paymentFrequency);

    const installment = findInstallmentVariable(
        amount, monthlyRate, maturity, taxes,
        reductionStart, reductionFrequency, reductionAmount, reductionRate,
        payInterval, 'DECREASE'
    );

    const rows = generatePlanVariable(
        amount, monthlyRate, maturity, taxes,
        reductionStart, reductionFrequency, reductionAmount, reductionRate,
        installment, payInterval, 'DECREASE'
    );

    return buildResult(installment, rows);
}

// ═══════════════════════════════════════════════
// MODE 3: INCREASING PAYMENT (Artan Taksit)
// ═══════════════════════════════════════════════

function calculateIncreasing(params) {
    const { amount, maturity, interestRate, loanType, paymentFrequency,
        increaseStart, increaseFrequency, increaseAmount, increaseRate } = params;

    const monthlyRate = interestRate / 100;
    const taxes = getTaxRates(loanType);
    const payInterval = getPaymentInterval(paymentFrequency);

    const installment = findInstallmentVariable(
        amount, monthlyRate, maturity, taxes,
        increaseStart, increaseFrequency, increaseAmount, increaseRate,
        payInterval, 'INCREASE'
    );

    const rows = generatePlanVariable(
        amount, monthlyRate, maturity, taxes,
        increaseStart, increaseFrequency, increaseAmount, increaseRate,
        installment, payInterval, 'INCREASE'
    );

    return buildResult(installment, rows);
}

// ═══════════════════════════════════════════════
// SHARED: Variable Payment (Decrease / Increase)
// ═══════════════════════════════════════════════

function findInstallmentVariable(principal, monthlyRate, maturity, taxes,
    varStart, varFreq, varAmount, varRate, payInterval, direction) {

    let low = 1, high = principal * 5;
    for (let i = 0; i < 300; i++) {
        const mid = (low + high) / 2;
        const bal = simulateVariable(principal, monthlyRate, maturity, taxes,
            varStart, varFreq, varAmount, varRate, mid, payInterval, direction);
        if (bal > 0) low = mid; else high = mid;
        if (high - low < 0.0000001) break;
    }
    const raw = (low + high) / 2;
    const cDown = Math.floor(raw * 100) / 100;
    const cUp = cDown + 0.01;
    const bDown = simulateVariable(principal, monthlyRate, maturity, taxes,
        varStart, varFreq, varAmount, varRate, cDown, payInterval, direction);
    const bUp = simulateVariable(principal, monthlyRate, maturity, taxes,
        varStart, varFreq, varAmount, varRate, cUp, payInterval, direction);

    if (bDown >= 0 && bUp < 0) return cUp;
    if (bDown < 0 && bUp >= 0) return cDown;
    return Math.abs(bDown) <= Math.abs(bUp) ? cDown : cUp;
}

function applyVariation(current, varAmount, varRate, direction) {
    if (varRate !== null && varRate !== undefined && varRate > 0) {
        const factor = direction === 'INCREASE' ? (1 + varRate) : (1 - varRate);
        return round2(current * factor);
    } else if (varAmount !== null && varAmount !== undefined && varAmount > 0) {
        return direction === 'INCREASE' ? round2(current + varAmount) : round2(current - varAmount);
    }
    return current;
}

function isVariationPoint(paymentNumber, start, frequency) {
    return paymentNumber >= start && (paymentNumber - start) % frequency === 0;
}

function simulateVariable(principal, monthlyRate, maturity, taxes,
    varStart, varFreq, varAmount, varRate, baseInstallment, payInterval, direction) {

    let balance = principal;
    let currentInstallment = baseInstallment;
    let paymentCount = 0;

    for (let i = 1; i <= maturity; i++) {
        const interest = round2(balance * monthlyRate);
        const kkdf = round2(interest * taxes.kkdf);
        const bsmv = round2(interest * taxes.bsmv);

        if (!isPaymentMonth(i, payInterval)) {
            balance = round2(balance + interest + kkdf + bsmv);
        } else {
            paymentCount++;
            if (isVariationPoint(paymentCount, varStart, varFreq)) {
                currentInstallment = applyVariation(currentInstallment, varAmount, varRate, direction);
            }
            balance = round2(balance - currentInstallment + interest + kkdf + bsmv);
        }
    }
    return balance;
}

function generatePlanVariable(principal, monthlyRate, maturity, taxes,
    varStart, varFreq, varAmount, varRate, baseInstallment, payInterval, direction) {

    const rows = [];
    let balance = principal;
    let currentInstallment = baseInstallment;
    let paymentCount = 0;

    for (let i = 1; i <= maturity; i++) {
        const interest = round2(balance * monthlyRate);
        const kkdf = round2(interest * taxes.kkdf);
        const bsmv = round2(interest * taxes.bsmv);
        let principalPart, actualInstallment;
        let isVarPoint = false;

        if (!isPaymentMonth(i, payInterval)) {
            principalPart = round2(-(interest + kkdf + bsmv));
            balance = round2(balance + interest + kkdf + bsmv);
            actualInstallment = 0;
        } else {
            paymentCount++;
            if (isVariationPoint(paymentCount, varStart, varFreq)) {
                currentInstallment = applyVariation(currentInstallment, varAmount, varRate, direction);
                isVarPoint = true;
            }

            actualInstallment = currentInstallment;

            if (i === maturity) {
                principalPart = balance;
                actualInstallment = round2(balance + interest + kkdf + bsmv);
                balance = 0;
            } else {
                const newBalance = round2(balance - actualInstallment + interest + kkdf + bsmv);
                principalPart = round2(balance - newBalance);
                balance = newBalance;

                if (balance < 0) {
                    principalPart = round2(principalPart + balance);
                    actualInstallment = round2(principalPart + interest + kkdf + bsmv);
                    balance = 0;
                }
            }
        }

        rows.push({
            no: i,
            monthlyPayment: round2(actualInstallment),
            principal: round2(principalPart),
            interest, kkdf, bsmv,
            balance: round2(balance),
            isVariationPoint: isVarPoint
        });

        if (balance === 0 && i < maturity) break;
    }
    return rows;
}

// ─── Build Result ───
function buildResult(installment, rows) {
    let totalPayment = 0, totalPrincipal = 0, totalInterest = 0, totalKkdf = 0, totalBsmv = 0;
    for (const row of rows) {
        totalPayment += row.monthlyPayment;
        totalPrincipal += row.principal;
        totalInterest += row.interest;
        totalKkdf += row.kkdf;
        totalBsmv += row.bsmv;
    }
    return {
        installmentAmount: round2(installment),
        totalPayment: round2(totalPayment),
        totalPrincipal: round2(totalPrincipal),
        totalInterest: round2(totalInterest),
        totalKkdf: round2(totalKkdf),
        totalBsmv: round2(totalBsmv),
        rows
    };
}
