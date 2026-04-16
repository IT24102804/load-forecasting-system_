function validateDateTime(dateTimeString) {
    if (!dateTimeString) {
        return { valid: false, message: 'Please select a date and time.' };
    }

    const dateTimePattern = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/;
    if (!dateTimePattern.test(dateTimeString)) {
        return { valid: false, message: 'Invalid format. Use YYYY-MM-DDTHH:MM.' };
    }

    try {
        const [datePart, timePart] = dateTimeString.split('T');
        const [year, month, day] = datePart.split('-').map(Number);
        const [hour, minute] = timePart.split(':').map(Number);

        if (year < 2020 || year > 2030) {
            return { valid: false, message: 'Year must be between 2020 and 2030.' };
        }
        if (month < 1 || month > 12) {
            return { valid: false, message: 'Month must be between 1 and 12.' };
        }

        const daysInMonth = [31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31];
        const isLeapYear = (year % 4 === 0 && year % 100 !== 0) || (year % 400 === 0);
        if (month === 2 && isLeapYear) {
            daysInMonth[1] = 29;
        }
        if (day < 1 || day > daysInMonth[month - 1]) {
            return { valid: false, message: 'Selected day is not valid for that month.' };
        }
        if (hour < 0 || hour > 23) {
            return { valid: false, message: 'Hour must be between 0 and 23.' };
        }
        if (minute < 0 || minute > 59) {
            return { valid: false, message: 'Minute must be between 0 and 59.' };
        }

        return { valid: true, message: '' };
    } catch (error) {
        return { valid: false, message: 'Invalid date and time format.' };
    }
}

function showTab(tabName, button) {
    document.querySelectorAll('.tab-content').forEach((tab) => tab.classList.remove('active'));
    document.querySelectorAll('.weather-tab-button').forEach((btn) => btn.classList.remove('active'));

    const target = document.getElementById(tabName);
    if (target) {
        target.classList.add('active');
    }
    if (button) {
        button.classList.add('active');
    }

    if (tabName === 'analytics') {
        setTimeout(() => {
            updateCharts();
            if (monthlyChart) {
                monthlyChart.resize();
            }
        }, 120);
    }
}

function setMessage(type, message) {
    const messageBox = document.getElementById('messageBox');
    const messageText = document.getElementById('messageText');
    if (!messageBox || !messageText) {
        return;
    }

    messageBox.className = 'weather-flash ' + type;
    messageText.textContent = message;
    messageBox.style.display = 'block';

    if (window.messageTimeout) {
        clearTimeout(window.messageTimeout);
    }
    window.messageTimeout = setTimeout(() => {
        messageBox.style.display = 'none';
    }, 5000);
}

function setBusyState(button, isBusy, busyHtml) {
    if (!button) {
        return;
    }
    if (!button.dataset.originalHtml) {
        button.dataset.originalHtml = button.innerHTML;
    }
    button.disabled = isBusy;
    button.innerHTML = isBusy ? busyHtml : button.dataset.originalHtml;
}

function formatNumber(value, suffix, decimals = 1) {
    if (value === null || value === undefined || Number.isNaN(Number(value))) {
        return 'N/A';
    }
    return Number(value).toFixed(decimals) + suffix;
}

function normalizeMonthlyTemps(monthlyTemps) {
    const values = new Array(12).fill(null);
    if (!Array.isArray(monthlyTemps)) {
        return values;
    }

    monthlyTemps.forEach((item, index) => {
        if (Array.isArray(item) && item.length >= 2) {
            const monthIndex = Number(item[0]) - 1;
            if (monthIndex >= 0 && monthIndex < 12) {
                values[monthIndex] = Number(item[1]);
            }
        } else if (item && typeof item === 'object') {
            const rawMonth = item.month ?? item[0] ?? index + 1;
            const rawValue = item.avgTemperature ?? item.value ?? item[1];
            const monthIndex = Number(rawMonth) - 1;
            if (monthIndex >= 0 && monthIndex < 12) {
                values[monthIndex] = Number(rawValue);
            }
        }
    });

    return values;
}

function displayPrediction(prediction) {
    const resultCard = document.getElementById('resultCard');
    if (!resultCard || !prediction) {
        return;
    }

    resultCard.innerHTML = `
        <div class="weather-forecast-grid">
            <div class="weather-forecast-item"><div class="weather-forecast-label">Date</div><div class="weather-forecast-value">${prediction.predictionDate}</div></div>
            <div class="weather-forecast-item"><div class="weather-forecast-label">Time</div><div class="weather-forecast-value">${prediction.predictionTime}</div></div>
            <div class="weather-forecast-item"><div class="weather-forecast-label">Temperature</div><div class="weather-forecast-value">${Number(prediction.temperature).toFixed(2)} degC</div></div>
            <div class="weather-forecast-item"><div class="weather-forecast-label">Humidity</div><div class="weather-forecast-value">${Number(prediction.humidity).toFixed(2)}%</div></div>
            <div class="weather-forecast-item"><div class="weather-forecast-label">Wind Speed</div><div class="weather-forecast-value">${Number(prediction.windSpeed).toFixed(2)} m/s</div></div>
            <div class="weather-forecast-item"><div class="weather-forecast-label">Rainfall</div><div class="weather-forecast-value">${Number(prediction.rainfall).toFixed(2)} mm</div></div>
            <div class="weather-forecast-item"><div class="weather-forecast-label">Solar Irradiance</div><div class="weather-forecast-value">${Number(prediction.solarIrradiance).toFixed(2)} W/m2</div></div>
        </div>
    `;
}

function refreshTable(history) {
    const tbody = document.querySelector('#historyTable tbody');
    if (!tbody) {
        return;
    }

    if (!history || history.length === 0) {
        tbody.innerHTML = '<tr><td colspan="9" class="text-center py-4 text-muted"><i class="fas fa-inbox me-2"></i>No predictions yet. Make your first prediction.</td></tr>';
        return;
    }

    tbody.innerHTML = history.map((record) => `
        <tr>
            <td>${record.id}</td>
            <td>${record.predictionDate}</td>
            <td>${record.predictionTime}</td>
            <td>${Number(record.temperature).toFixed(2)}</td>
            <td>${Number(record.humidity).toFixed(2)}</td>
            <td>${Number(record.windSpeed).toFixed(2)}</td>
            <td>${Number(record.rainfall).toFixed(2)}</td>
            <td>${Number(record.solarIrradiance).toFixed(2)}</td>
            <td>
                <div class="weather-action-buttons">
                    <button type="button" class="btn btn-sm btn-outline-primary rounded-pill fw-bold" data-id="${record.id}" data-date="${record.predictionDate}" data-time="${record.predictionTime}" onclick="updatePrediction(this)">
                        <i class="fas fa-edit me-1"></i>Update
                    </button>
                    <button type="button" class="btn btn-sm btn-outline-danger rounded-pill fw-bold" data-id="${record.id}" onclick="deletePrediction(this)">
                        <i class="fas fa-trash-alt me-1"></i>Delete
                    </button>
                </div>
            </td>
        </tr>
    `).join('');
}

function refreshStatistics(stats) {
    if (!stats) {
        return;
    }

    document.getElementById('statTotal').textContent = stats.total ?? '0';
    document.getElementById('statAvgTemperature').textContent = formatNumber(stats.avgTemperature, ' degC');
    document.getElementById('statAvgHumidity').textContent = formatNumber(stats.avgHumidity, '%');
    document.getElementById('statMaxTemperature').textContent = formatNumber(stats.maxTemperature, ' degC');
    document.getElementById('statMinTemperature').textContent = formatNumber(stats.minTemperature, ' degC');
    document.getElementById('statTotalRainfall').textContent = formatNumber(stats.totalRainfall, ' mm');

    if (monthlyChart) {
        monthlyChart.data.datasets[0].data = normalizeMonthlyTemps(stats.monthlyTemps);
        monthlyChart.update();
    }
}

function deletePrediction(button) {
    const id = button.getAttribute('data-id');
    if (!confirm('Are you sure you want to delete this prediction?')) {
        return;
    }

    setBusyState(button, true, '<i class="fas fa-spinner fa-spin me-1"></i>Deleting...');

    fetch(`/api/predictions/${id}`, {
        method: 'DELETE',
        headers: { 'Content-Type': 'application/json' }
    })
        .then((response) => response.json())
        .then((data) => {
            if (data.success) {
                setMessage('success', data.message || 'Prediction deleted successfully.');
                refreshTable(data.history);
                refreshStatistics(data.statistics);
            } else {
                setBusyState(button, false);
                setMessage('error', data.message || 'Failed to delete prediction.');
            }
        })
        .catch(() => {
            setBusyState(button, false);
            setMessage('error', 'Failed to delete prediction.');
        });
}

function updatePrediction(button) {
    const id = button.getAttribute('data-id');
    const currentDate = button.getAttribute('data-date');
    const currentTime = button.getAttribute('data-time');

    const modal = document.createElement('div');
    modal.className = 'weather-modal';
    modal.innerHTML = `
        <div class="weather-modal-content">
            <h5><i class="fas fa-edit me-2" style="color: var(--cobalt);"></i>Update Prediction</h5>
            <p class="text-muted mb-3">Choose a new timestamp for prediction ID ${id}.</p>
            <input type="datetime-local" id="updateDateTime" class="form-control" value="${currentDate}T${currentTime}" />
            <div class="weather-modal-actions">
                <button type="button" class="btn btn-outline-secondary rounded-pill px-3" onclick="closeModal()">Cancel</button>
                <button type="button" class="btn-primary-custom px-4" onclick="confirmUpdate(this, ${id})">Update</button>
            </div>
        </div>
    `;
    document.body.appendChild(modal);
    modal.classList.add('active');
    window.currentModal = modal;
}

function confirmUpdate(button, id) {
    const dateTime = document.getElementById('updateDateTime').value;
    const validation = validateDateTime(dateTime);
    if (!validation.valid) {
        setMessage('error', validation.message);
        return;
    }

    setBusyState(button, true, '<i class="fas fa-spinner fa-spin me-2"></i>Updating...');

    fetch(`/api/predictions/${id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ dateTime })
    })
        .then((response) => response.json())
        .then((data) => {
            if (data.success) {
                setMessage('success', data.message || 'Prediction updated successfully.');
                refreshTable(data.history);
                refreshStatistics(data.statistics);
                closeModal();
                if (data.prediction) {
                    displayPrediction(data.prediction);
                }
            } else {
                setBusyState(button, false);
                setMessage('error', data.message || 'Failed to update prediction.');
            }
        })
        .catch(() => {
            setBusyState(button, false);
            setMessage('error', 'Failed to update prediction.');
        });
}

function closeModal() {
    if (window.currentModal) {
        window.currentModal.remove();
        window.currentModal = null;
    }
}

let monthlyChart;

function initCharts() {
    const monthlyCanvas = document.getElementById('monthlyTempChart');
    if (!monthlyCanvas || monthlyChart) {
        return;
    }

    monthlyChart = new Chart(monthlyCanvas, {
        type: 'bar',
        data: {
            labels: ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'],
            datasets: [{
                label: 'Average Temperature (degC)',
                data: normalizeMonthlyTemps(monthlyData),
                backgroundColor: 'rgba(0, 71, 171, 0.85)',
                borderRadius: 8,
                maxBarThickness: 34
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: {
                legend: { display: false }
            },
            scales: {
                y: {
                    beginAtZero: false,
                    grid: { color: 'rgba(109, 129, 150, 0.15)' }
                },
                x: {
                    grid: { display: false }
                }
            }
        }
    });
}

function updateCharts() {
    if (monthlyChart) {
        monthlyChart.data.datasets[0].data = normalizeMonthlyTemps(monthlyData);
        monthlyChart.update();
    }
}

document.addEventListener('DOMContentLoaded', function () {
    const datetimeInput = document.getElementById('dateTime');
    const form = document.getElementById('predictionForm');
    const messageBox = document.getElementById('messageBox');

    if (datetimeInput && !datetimeInput.value) {
        const now = new Date();
        const year = now.getFullYear();
        const month = String(now.getMonth() + 1).padStart(2, '0');
        const day = String(now.getDate()).padStart(2, '0');
        const hours = String(now.getHours()).padStart(2, '0');
        const minutes = String(now.getMinutes()).padStart(2, '0');
        datetimeInput.value = `${year}-${month}-${day}T${hours}:${minutes}`;
    }

    if (messageBox && messageBox.textContent.trim()) {
        messageBox.style.display = 'block';
        window.messageTimeout = setTimeout(() => {
            messageBox.style.display = 'none';
        }, 5000);
    }

    if (form && datetimeInput) {
        form.addEventListener('submit', function (event) {
            const validation = validateDateTime(datetimeInput.value);
            if (!validation.valid) {
                event.preventDefault();
                setMessage('error', validation.message);
                datetimeInput.classList.add('is-invalid');
                datetimeInput.focus();
                return false;
            }

            datetimeInput.classList.remove('is-invalid');
            const button = this.querySelector('.btn-primary-custom');
            if (button) {
                button.dataset.originalText = button.innerHTML;
                button.innerHTML = '<i class="fas fa-spinner fa-spin me-2"></i>Predicting...';
                button.disabled = true;
            }
        });

        datetimeInput.addEventListener('input', function () {
            const validation = validateDateTime(this.value);
            this.classList.toggle('is-invalid', !validation.valid && this.value.length > 0);
        });
    }

    initCharts();
    updateCharts();
});

document.addEventListener('click', function (event) {
    if (window.currentModal && event.target === window.currentModal) {
        closeModal();
    }
});
