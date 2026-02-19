/**
 * Flexible Payment Calculator — UI Controller
 */

let currentMode = 'inter';

// ─── Mode Switching ───
function switchMode(mode) {
    currentMode = mode;

    // Update tabs
    document.querySelectorAll('.mode-tab').forEach(tab => {
        tab.classList.toggle('active', tab.dataset.mode === mode);
    });

    // Show/hide config sections
    document.getElementById('interPaymentConfig').style.display = mode === 'inter' ? '' : 'none';
    document.getElementById('decreasingConfig').style.display = mode === 'decreasing' ? '' : 'none';
    document.getElementById('increasingConfig').style.display = mode === 'increasing' ? '' : 'none';

    // Hide results when switching modes
    document.getElementById('resultsSection').classList.remove('visible');
}

// ─── Toggle Rate/Amount ───
function toggleVariationType(prefix) {
    const type = document.getElementById(prefix + 'Type').value;
    document.getElementById(prefix + 'RateGroup').style.display = type === 'RATE' ? '' : 'none';
    document.getElementById(prefix + 'AmountGroup').style.display = type === 'AMOUNT' ? '' : 'none';
}

// ─── Get Form Values ───
function getVal(id) {
    return parseFloat(document.getElementById(id).value) || 0;
}

function getStr(id) {
    return document.getElementById(id).value;
}

// ─── Main Calculate ───
function calculate() {
    let result;

    const base = {
        amount: getVal('amount'),
        maturity: Math.round(getVal('maturity')),
        interestRate: getVal('interestRate'),
        loanType: getStr('loanType'),
        paymentFrequency: getStr('paymentFrequency')
    };

    try {
        switch (currentMode) {
            case 'inter':
                result = calculateInterPayment({
                    ...base,
                    interPaymentStart: Math.round(getVal('ipStart')),
                    interPaymentFrequency: Math.round(getVal('ipFrequency')),
                    interPaymentAmount: getVal('ipAmount'),
                    calculationMethod: getStr('calcMethod')
                });
                break;

            case 'decreasing': {
                const redType = getStr('redType');
                result = calculateDecreasing({
                    ...base,
                    reductionStart: Math.round(getVal('redStart')),
                    reductionFrequency: Math.round(getVal('redFrequency')),
                    reductionRate: redType === 'RATE' ? getVal('redRate') : null,
                    reductionAmount: redType === 'AMOUNT' ? getVal('redAmount') : null
                });
                break;
            }

            case 'increasing': {
                const incType = getStr('incType');
                result = calculateIncreasing({
                    ...base,
                    increaseStart: Math.round(getVal('incStart')),
                    increaseFrequency: Math.round(getVal('incFrequency')),
                    increaseRate: incType === 'RATE' ? getVal('incRate') : null,
                    increaseAmount: incType === 'AMOUNT' ? getVal('incAmount') : null
                });
                break;
            }
        }

        renderResults(result);
    } catch (e) {
        console.error(e);
        alert('Hesaplama hatası: ' + e.message);
    }
}

// ─── Render Results ───
function renderResults(result) {
    renderSummary(result);
    renderTable(result);
    const section = document.getElementById('resultsSection');
    section.classList.add('visible');
    section.scrollIntoView({ behavior: 'smooth', block: 'start' });
}

function renderSummary(result) {
    const grid = document.getElementById('summaryGrid');

    const modeLabel = {
        inter: 'İlk Taksit Tutarı',
        decreasing: 'İlk Taksit Tutarı',
        increasing: 'İlk Taksit Tutarı'
    }[currentMode];

    grid.innerHTML = `
    <div class="summary-card highlight">
      <div class="label">${modeLabel}</div>
      <div class="value accent">₺${formatTL(result.installmentAmount)}</div>
    </div>
    <div class="summary-card">
      <div class="label">Toplam Ödeme</div>
      <div class="value">₺${formatTL(result.totalPayment)}</div>
    </div>
    <div class="summary-card">
      <div class="label">Toplam Anapara</div>
      <div class="value green">₺${formatTL(result.totalPrincipal)}</div>
    </div>
    <div class="summary-card">
      <div class="label">Toplam Faiz</div>
      <div class="value">₺${formatTL(result.totalInterest)}</div>
    </div>
    ${result.totalKkdf > 0 ? `
    <div class="summary-card">
      <div class="label">Toplam KKDF</div>
      <div class="value">₺${formatTL(result.totalKkdf)}</div>
    </div>` : ''}
    ${result.totalBsmv > 0 ? `
    <div class="summary-card">
      <div class="label">Toplam BSMV</div>
      <div class="value">₺${formatTL(result.totalBsmv)}</div>
    </div>` : ''}
  `;
}

function renderTable(result) {
    const tbody = document.getElementById('tableBody');
    const rows = result.rows;

    document.getElementById('rowCount').textContent = `${rows.length} taksit`;

    let prevPayment = null;
    let html = '';

    for (const row of rows) {
        const isNonPayment = row.monthlyPayment === 0;
        const isLast = row.balance === 0 && row.monthlyPayment > 0;

        // Determine badge
        let badge = '';
        if (currentMode === 'inter' && row.isInterPayment) {
            badge = '<span class="badge star">ARA</span>';
        } else if (currentMode === 'decreasing' && row.isVariationPoint) {
            badge = '<span class="badge down">↓</span>';
        } else if (currentMode === 'increasing' && row.isVariationPoint) {
            badge = '<span class="badge up">↑</span>';
        }

        // Row classes
        const classes = [];
        if (isNonPayment) classes.push('non-payment');
        if (isLast) classes.push('last-row');
        if (row.isVariationPoint || row.isInterPayment) classes.push('change-row');

        html += `<tr class="${classes.join(' ')}">
      <td>${row.no}</td>
      <td>${formatTL(row.monthlyPayment)}${badge}</td>
      <td class="${row.principal < 0 ? 'negative' : ''}">${formatTL(row.principal)}</td>
      <td>${formatTL(row.interest)}</td>
      <td>${formatTL(row.kkdf)}</td>
      <td>${formatTL(row.bsmv)}</td>
      <td>${formatTL(row.balance)}</td>
    </tr>`;

        prevPayment = row.monthlyPayment;
    }

    tbody.innerHTML = html;
}

// ─── Enter key support ───
document.addEventListener('keydown', (e) => {
    if (e.key === 'Enter') {
        const active = document.activeElement;
        if (active && (active.tagName === 'INPUT' || active.tagName === 'SELECT')) {
            calculate();
        }
    }
});
