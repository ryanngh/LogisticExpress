(function (global) {
    'use strict';

    const ZONE_META = {
        INTRA_PROVINCE: { label: 'Nội tỉnh', cls: 'le-badge-teal' },
        SAME_REGION: { label: 'Nội miền', cls: 'le-badge-info' },
        INTER_REGION: { label: 'Liên miền', cls: 'le-badge-warning' }
    };

    const FAILED_STATUSES = new Set([
        'DELIVERY_FAILED', 'REFUSED', 'ON_HOLD_FOR_RETURN',
        'RETURN_INITIATED', 'IN_RETURN_TRANSIT', 'RETURNED', 'CANCELLED'
    ]);

    let serviceTypes = [];
    let rateCards = [];
    let shipments = [];
    let provinces = [];

    let isAdmin = false;
    let canViewRates = false;

    let rateCardModalInstance = null;
    let serviceTypeModalInstance = null;

    function refreshIcons() {
        if (global.lucide && typeof global.lucide.createIcons === 'function') {
            global.lucide.createIcons();
        }
    }

    function showPageAlert(type, message) {
        const el = document.getElementById('finance-alert');
        if (!el) return;
        el.className = 'alert alert-' + type + ' alert-dismissible fade show mb-4';
        el.innerHTML = global.opsCommon.escapeHtml(message) +
            '<button type="button" class="btn-close" data-bs-dismiss="alert" aria-label="Đóng"></button>';
    }

    function setBoxError(id, msg) {
        const el = document.getElementById(id);
        if (!el) return;
        if (!msg) {
            el.classList.add('d-none');
            el.textContent = '';
            return;
        }
        el.textContent = msg;
        el.classList.remove('d-none');
    }

    async function extractErrorMessage(res, fallback) {
        try {
            const data = await res.json();
            if (data && data.message) return data.message;
        } catch (_) { /* ignore */ }
        return fallback;
    }

    function renderZoneBadge(zone) {
        const meta = ZONE_META[zone] || { label: zone || '—', cls: 'le-badge-neutral' };
        return '<span class="le-badge ' + meta.cls + '">' + global.opsCommon.escapeHtml(meta.label) + '</span>';
    }

    function formatRatioPercent(ratio) {
        if (ratio == null || isNaN(Number(ratio))) return '0%';
        return Math.round(Number(ratio) * 100) + '%';
    }

    function computeNetPayout(s) {
        const cod = Number(s.codAmount || 0);
        const shipFee = Number(s.shippingFee || 0);
        return s.feePayer === 'RECEIVER' ? cod : (cod - shipFee);
    }

    function updateKpis() {
        const c = global.opsCommon;
        const set = function (id, txt) {
            const el = document.getElementById(id);
            if (el) el.textContent = txt;
        };

        let totalCod = 0;
        let deliveredCod = 0;
        let totalRevenue = 0;
        let totalIns = 0;

        shipments.forEach(function (s) {
            const cod = Number(s.codAmount || 0);
            totalCod += cod;
            if (s.currentStatus === 'DELIVERED') deliveredCod += cod;
            totalRevenue += Number(s.shippingFee || 0);
            totalIns += Number(s.insuranceFee || 0);
        });

        const activeRates = rateCards.filter(function (r) { return Boolean(r.isActive); });

        set('fin-kpi-cod', c.formatVnd(totalCod));
        set('fin-kpi-cod-sub', 'Đã giao: ' + c.formatVnd(deliveredCod) + ' (' + shipments.length + ' đơn)');
        set('fin-kpi-revenue', c.formatVnd(totalRevenue));
        set('fin-kpi-ins-sub', 'Trong đó khai giá: ' + c.formatVnd(totalIns));

        set('fin-kpi-active-rates', canViewRates ? c.formatNumber(activeRates.length) : '—');
        set('fin-kpi-total-rates', canViewRates
            ? ('Tổng phiên bản biểu phí: ' + c.formatNumber(rateCards.length))
            : 'Chỉ Admin / Quản lý xem biểu phí');

        set('fin-kpi-services', c.formatNumber(serviceTypes.length));
        if (serviceTypes.length > 0) {
            const codes = serviceTypes.map(function (st) { return st.code; }).join(' · ');
            set('fin-kpi-services-sub', codes);
        }

        set('tab-rate-count', c.formatNumber(activeRates.length));
        set('tab-cod-count', c.formatNumber(shipments.length));
    }

    function populateServiceSelects() {
        const esc = global.opsCommon.escapeHtml;
        const filterSel = document.getElementById('filter-rate-service');
        const rcSel = document.getElementById('rc-service-id');
        const qSel = document.getElementById('q-service');

        const idOptions = serviceTypes.map(function (st) {
            return '<option value="' + esc(st.id) + '">' + esc(st.name) + ' (' + esc(st.code) + ')</option>';
        }).join('');

        const codeOptions = serviceTypes.map(function (st) {
            return '<option value="' + esc(st.code) + '">' + esc(st.name) + ' (' + esc(st.code) + ')</option>';
        }).join('');

        if (filterSel) {
            const cur = filterSel.value;
            filterSel.innerHTML = '<option value="">Tất cả gói dịch vụ</option>' + idOptions;
            filterSel.value = cur;
        }
        if (rcSel) {
            const cur = rcSel.value;
            rcSel.innerHTML = idOptions || '<option value="">Chưa có gói dịch vụ</option>';
            if (cur) rcSel.value = cur;
        }
        if (qSel) {
            const cur = qSel.value;
            qSel.innerHTML = '<option value="">-- Chọn gói dịch vụ --</option>' + codeOptions;
            if (cur) qSel.value = cur;
            else if (serviceTypes.length > 0) qSel.value = serviceTypes[0].code;
        }
    }

    function renderServiceTypes() {
        const grid = document.getElementById('service-types-grid');
        if (!grid) return;
        const esc = global.opsCommon.escapeHtml;
        const c = global.opsCommon;

        if (serviceTypes.length === 0) {
            grid.innerHTML = '<div class="col-12 text-center py-4 text-muted small">Chưa có gói dịch vụ nào được cấu hình.</div>';
            return;
        }

        grid.innerHTML = serviceTypes.map(function (st) {
            const badgeCls = st.code === 'EXPRESS' ? 'le-badge-danger' : 'le-badge-info';
            const editBtn = isAdmin
                ? '<button type="button" class="le-btn-icon" data-action="edit-service" data-id="' + esc(st.id) + '" title="Chỉnh sửa gói" aria-label="Chỉnh sửa gói">' +
                      '<i data-lucide="pencil" style="width:14px;height:14px;" aria-hidden="true"></i>' +
                  '</button>'
                : '';

            return '<div class="col-12 col-md-6">' +
                '<div class="le-metric-box d-flex justify-content-between align-items-start gap-3">' +
                    '<div>' +
                        '<div class="d-flex align-items-center gap-2 mb-1">' +
                            '<span class="le-badge ' + badgeCls + ' font-monospace">' + esc(st.code) + '</span>' +
                            '<span class="fw-bold">' + esc(st.name) + '</span>' +
                        '</div>' +
                        '<div class="small text-muted mt-2">' +
                            'Hệ số quy đổi thể tích: <strong class="text-dark le-tabular">/' + esc(c.formatNumber(st.volumetricDivisor)) + '</strong> · ' +
                            'Cam kết SLA: <strong class="text-dark le-tabular">' + esc(c.formatNumber(st.slaHours)) + ' giờ</strong>' +
                        '</div>' +
                    '</div>' +
                    editBtn +
                '</div>' +
            '</div>';
        }).join('');

        refreshIcons();
    }

    function getFilteredRateCards() {
        const svcId = document.getElementById('filter-rate-service')?.value || '';
        const zone = document.getElementById('filter-rate-zone')?.value || '';
        const activeFilter = document.getElementById('filter-rate-active')?.value || 'ACTIVE';

        return rateCards.filter(function (rc) {
            if (svcId && rc.serviceTypeId !== svcId) return false;
            if (zone && rc.zoneType !== zone) return false;
            if (activeFilter === 'ACTIVE' && !rc.isActive) return false;
            if (activeFilter === 'INACTIVE' && rc.isActive) return false;
            return true;
        });
    }

    function renderRateCards() {
        const tbody = document.getElementById('rate-cards-body');
        const countEl = document.getElementById('rate-cards-count-label');
        if (!tbody) return;
        const c = global.opsCommon;
        const esc = c.escapeHtml;

        if (!canViewRates) {
            if (countEl) countEl.textContent = 'Hạn chế quyền';
            tbody.innerHTML = '<tr><td colspan="8" class="text-center py-5 text-muted">Tài khoản cần quyền <strong>ADMIN</strong> hoặc <strong>MANAGER</strong> để xem bảng biểu phí chi tiết. Bạn vẫn có thể sử dụng tab <strong>Tính thử cước phí</strong>.</td></tr>';
            return;
        }

        const rows = getFilteredRateCards();
        if (countEl) {
            countEl.textContent = 'Hiển thị ' + rows.length + ' / ' + rateCards.length + ' biểu phí';
        }

        if (rows.length === 0) {
            tbody.innerHTML = '<tr><td colspan="8" class="text-center py-5 text-muted">Không có biểu phí nào khớp với bộ lọc hiện tại.</td></tr>';
            return;
        }

        tbody.innerHTML = rows.map(function (rc) {
            const statusBadge = rc.isActive
                ? '<span class="le-badge le-badge-success">Đang áp dụng</span>'
                : '<span class="le-badge le-badge-neutral">Lịch sử</span>';

            const cloneBtn = isAdmin
                ? '<button type="button" class="btn btn-outline-secondary btn-sm py-0 px-2" data-action="clone-rate" data-id="' + esc(rc.id) + '">Phiên bản mới</button>'
                : '<span class="small text-muted">—</span>';

            return '<tr>' +
                '<td>' +
                    '<div class="fw-semibold">' + esc(rc.serviceTypeName || '—') + '</div>' +
                    '<div class="small text-muted font-monospace">' + esc(rc.serviceTypeCode || '') + '</div>' +
                '</td>' +
                '<td>' + renderZoneBadge(rc.zoneType) + '</td>' +
                '<td class="text-end le-tabular">' +
                    '<div class="fw-bold" style="color:var(--le-primary-hover);">' + esc(c.formatVnd(rc.basePrice)) + '</div>' +
                    '<div class="small text-muted">&le; ' + esc(c.formatNumber(rc.baseWeightGram)) + ' g</div>' +
                '</td>' +
                '<td class="text-end le-tabular">' +
                    '<div class="fw-semibold">+' + esc(c.formatVnd(rc.stepPrice)) + '</div>' +
                    '<div class="small text-muted">mỗi ' + esc(c.formatNumber(rc.stepWeightGram)) + ' g</div>' +
                '</td>' +
                '<td class="text-end le-tabular fw-semibold">' + esc(formatRatioPercent(rc.returnFeeRatio)) + '</td>' +
                '<td class="small le-tabular text-muted">' + esc(c.formatDateTime(rc.effectiveFrom)) + '</td>' +
                '<td>' + statusBadge + '</td>' +
                '<td class="text-end">' + cloneBtn + '</td>' +
            '</tr>';
        }).join('');
    }

    function getFilteredCodShipments() {
        const q = (document.getElementById('cod-search')?.value || '').trim().toLowerCase();
        const stage = document.getElementById('cod-stage-filter')?.value || '';

        return shipments.filter(function (s) {
            if (stage === 'DELIVERED' && s.currentStatus !== 'DELIVERED') return false;
            if (stage === 'FAILED_RETURN' && !FAILED_STATUSES.has(s.currentStatus)) return false;
            if (stage === 'IN_PROGRESS' && (s.currentStatus === 'DELIVERED' || FAILED_STATUSES.has(s.currentStatus))) return false;
            if (q) {
                const text = [s.trackingCode, s.senderName, s.receiverName, s.senderPhone, s.receiverPhone]
                    .filter(Boolean).join(' ').toLowerCase();
                if (!text.includes(q)) return false;
            }
            return true;
        });
    }

    function renderCodTable() {
        const tbody = document.getElementById('cod-table-body');
        if (!tbody) return;
        const c = global.opsCommon;
        const esc = c.escapeHtml;

        const rows = getFilteredCodShipments();
        if (rows.length === 0) {
            tbody.innerHTML = '<tr><td colspan="8" class="text-center py-5 text-muted">Chưa có vận đơn nào khớp bộ lọc dòng tiền COD.</td></tr>';
            return;
        }

        tbody.innerHTML = rows.map(function (s) {
            const detailUrl = '/ops/shipments/' + encodeURIComponent(s.trackingCode);
            const net = computeNetPayout(s);
            const netStyle = net < 0 ? 'color:var(--le-primary-hover);' : 'color:var(--le-on-success-container);';
            const payerLabel = s.feePayer === 'RECEIVER' ? 'Người nhận' : 'Shop';

            return '<tr>' +
                '<td><a href="' + detailUrl + '" class="fw-bold font-monospace text-decoration-none" style="color:var(--le-primary-hover);">' + esc(s.trackingCode) + '</a></td>' +
                '<td class="small"><div class="fw-semibold">' + esc(s.senderName || '—') + '</div><div class="text-muted">&rarr; ' + esc(s.receiverName || '—') + '</div></td>' +
                '<td>' + c.renderStatusBadge(s.currentStatus) + '</td>' +
                '<td class="small">' + esc(payerLabel) + '</td>' +
                '<td class="text-end le-tabular fw-semibold">' + esc(c.formatVnd(s.codAmount)) + '</td>' +
                '<td class="text-end le-tabular text-muted">' + esc(c.formatVnd(s.shippingFee)) + '</td>' +
                '<td class="text-end le-tabular fw-bold" style="color:var(--le-wine);">' + esc(c.formatVnd(s.amountToCollect)) + '</td>' +
                '<td class="text-end le-tabular fw-bold" style="' + netStyle + '">' + esc(c.formatVnd(net)) + '</td>' +
            '</tr>';
        }).join('');
    }

    async function loadProvincesIfNeeded() {
        if (provinces.length > 0) return;
        try {
            const res = await global.apiFetch('/api/public/provinces');
            if (!res.ok) return;
            const data = await res.json();
            provinces = Array.isArray(data) ? data : [];
            const esc = global.opsCommon.escapeHtml;
            const opts = '<option value="">-- Chọn Tỉnh / Thành --</option>' +
                provinces.map(function (p) {
                    return '<option value="' + esc(p.code) + '">' + esc(p.name) + ' (' + esc(p.code) + ')</option>';
                }).join('');
            const sSel = document.getElementById('q-sender-prov');
            const rSel = document.getElementById('q-receiver-prov');
            if (sSel) sSel.innerHTML = opts;
            if (rSel) rSel.innerHTML = opts;
            if (provinces.length > 0) {
                if (sSel) sSel.value = provinces[0].code;
                if (rSel) rSel.value = provinces[Math.min(1, provinces.length - 1)].code;
            }
        } catch (_) { /* ignore */ }
    }

    async function handleCalculateQuote(e) {
        e.preventDefault();
        setBoxError('quote-error', '');

        const serviceTypeCode = document.getElementById('q-service').value;
        const senderProvinceCode = document.getElementById('q-sender-prov').value;
        const receiverProvinceCode = document.getElementById('q-receiver-prov').value;
        const actualWeightGram = parseInt(document.getElementById('q-weight').value, 10);
        const lengthVal = document.getElementById('q-length').value;
        const widthVal = document.getElementById('q-width').value;
        const heightVal = document.getElementById('q-height').value;
        const declaredValue = Number(document.getElementById('q-declared').value || 0);
        const codAmount = Number(document.getElementById('q-cod').value || 0);
        const feePayer = document.getElementById('q-fee-payer').value || 'SENDER';

        if (!serviceTypeCode || !senderProvinceCode || !receiverProvinceCode || !actualWeightGram || actualWeightGram < 1) {
            setBoxError('quote-error', 'Vui lòng chọn Gói dịch vụ, Tỉnh gửi, Tỉnh nhận và nhập cân nặng hợp lệ.');
            return;
        }

        const btn = document.getElementById('btn-calc-quote');
        btn.disabled = true;
        try {
            const payload = {
                serviceTypeCode: serviceTypeCode,
                senderProvinceCode: senderProvinceCode,
                receiverProvinceCode: receiverProvinceCode,
                actualWeightGram: actualWeightGram,
                lengthCm: lengthVal ? parseInt(lengthVal, 10) : null,
                widthCm: widthVal ? parseInt(widthVal, 10) : null,
                heightCm: heightVal ? parseInt(heightVal, 10) : null,
                declaredValue: declaredValue,
                codAmount: codAmount,
                feePayer: feePayer
            };
            const res = await global.apiFetch('/api/public/quote', { method: 'POST', body: payload });
            if (!res.ok) {
                setBoxError('quote-error', await extractErrorMessage(res, 'Không thể tính cước cho tuyến này (kiểm tra đã có biểu phí cho vùng tương ứng chưa).'));
                return;
            }
            const q = await res.json();
            const c = global.opsCommon;

            document.getElementById('quote-result-empty').classList.add('d-none');
            document.getElementById('quote-result-body').classList.remove('d-none');

            const zoneMeta = ZONE_META[q.zoneType] || { label: q.zoneType, cls: 'le-badge-neutral' };
            const zb = document.getElementById('qr-zone-badge');
            zb.className = 'le-badge ' + zoneMeta.cls;
            zb.textContent = zoneMeta.label;

            document.getElementById('qr-actual-w').textContent = c.formatNumber(q.actualWeightGram) + ' g';
            document.getElementById('qr-vol-w').textContent = c.formatNumber(q.volumetricWeightGram) + ' g';
            document.getElementById('qr-charge-w').textContent = c.formatNumber(q.chargeableWeightGram) + ' g';

            document.getElementById('qr-base-fee').textContent = c.formatVnd(q.baseFee);
            document.getElementById('qr-ins-fee').textContent = c.formatVnd(q.insuranceFee);
            document.getElementById('qr-ship-fee').textContent = c.formatVnd(q.shippingFee);
            document.getElementById('qr-return-fee').textContent = c.formatVnd(q.returnFee);
            document.getElementById('qr-collect').textContent = c.formatVnd(q.amountToCollect);

            const netPayout = q.feePayer === 'RECEIVER' ? codAmount : (codAmount - Number(q.shippingFee || 0));
            document.getElementById('qr-net-payout').textContent = c.formatVnd(netPayout);
        } catch (_) {
            setBoxError('quote-error', 'Lỗi kết nối máy chủ khi tính cước.');
        } finally {
            btn.disabled = false;
        }
    }

    function openRateCardModal(preset) {
        setBoxError('rc-form-error', '');
        document.getElementById('rateCardModalTitle').textContent = preset
            ? ('Ban hành phiên bản mới — ' + preset.serviceTypeCode + ' / ' + (ZONE_META[preset.zoneType]?.label || preset.zoneType))
            : 'Tạo phiên bản biểu phí mới';

        if (preset) {
            document.getElementById('rc-service-id').value = preset.serviceTypeId || '';
            document.getElementById('rc-zone-type').value = preset.zoneType || 'INTRA_PROVINCE';
            document.getElementById('rc-base-weight').value = preset.baseWeightGram ?? 2000;
            document.getElementById('rc-base-price').value = preset.basePrice ?? 22000;
            document.getElementById('rc-step-weight').value = preset.stepWeightGram ?? 500;
            document.getElementById('rc-step-price').value = preset.stepPrice ?? 3500;
            document.getElementById('rc-return-ratio').value = preset.returnFeeRatio ?? 0.5;
        }
        rateCardModalInstance?.show();
    }

    async function handleSaveRateCard(e) {
        e.preventDefault();
        setBoxError('rc-form-error', '');

        const payload = {
            serviceTypeId: document.getElementById('rc-service-id').value,
            zoneType: document.getElementById('rc-zone-type').value,
            baseWeightGram: parseInt(document.getElementById('rc-base-weight').value, 10),
            basePrice: Number(document.getElementById('rc-base-price').value),
            stepWeightGram: parseInt(document.getElementById('rc-step-weight').value, 10),
            stepPrice: Number(document.getElementById('rc-step-price').value),
            returnFeeRatio: Number(document.getElementById('rc-return-ratio').value || 0.5)
        };

        if (!payload.serviceTypeId || !payload.baseWeightGram || payload.baseWeightGram < 1 || !payload.stepWeightGram || payload.stepWeightGram < 1) {
            setBoxError('rc-form-error', 'Vui lòng nhập đầy đủ gói dịch vụ và thông số khối lượng > 0.');
            return;
        }
        if (isNaN(payload.basePrice) || payload.basePrice < 0 || isNaN(payload.stepPrice) || payload.stepPrice < 0) {
            setBoxError('rc-form-error', 'Giá cơ bản và giá bước nhảy phải >= 0.');
            return;
        }

        const btn = document.getElementById('btn-save-rate');
        btn.disabled = true;
        try {
            const res = await global.apiFetch('/api/ops/rate-cards', { method: 'POST', body: payload });
            if (!res.ok) {
                setBoxError('rc-form-error', await extractErrorMessage(res, 'Không thể ban hành biểu phí mới.'));
                return;
            }
            rateCardModalInstance?.hide();
            await loadAllData();
            showPageAlert('success', 'Đã ban hành phiên bản biểu phí mới thành công.');
        } catch (_) {
            setBoxError('rc-form-error', 'Lỗi kết nối máy chủ.');
        } finally {
            btn.disabled = false;
        }
    }

    function openServiceTypeModal(existing) {
        setBoxError('st-form-error', '');
        document.getElementById('serviceTypeModalTitle').textContent = existing
            ? ('Chỉnh sửa gói dịch vụ ' + existing.code)
            : 'Thêm gói dịch vụ vận chuyển';

        document.getElementById('st-id').value = existing ? existing.id : '';
        document.getElementById('st-code').value = existing ? existing.code : 'STANDARD';
        document.getElementById('st-name').value = existing ? existing.name : '';
        document.getElementById('st-divisor').value = existing ? existing.volumetricDivisor : 6000;
        document.getElementById('st-sla').value = existing ? existing.slaHours : 48;
        serviceTypeModalInstance?.show();
    }

    async function handleSaveServiceType(e) {
        e.preventDefault();
        setBoxError('st-form-error', '');

        const editingId = document.getElementById('st-id').value;
        const payload = {
            code: document.getElementById('st-code').value,
            name: (document.getElementById('st-name').value || '').trim(),
            volumetricDivisor: parseInt(document.getElementById('st-divisor').value, 10),
            slaHours: parseInt(document.getElementById('st-sla').value, 10)
        };

        if (!payload.name || !payload.volumetricDivisor || payload.volumetricDivisor < 1 || !payload.slaHours || payload.slaHours < 1) {
            setBoxError('st-form-error', 'Vui lòng nhập đầy đủ Tên gói, Hệ số quy đổi (> 0) và SLA (> 0).');
            return;
        }

        const btn = document.getElementById('btn-save-service');
        btn.disabled = true;
        try {
            const url = editingId ? '/api/ops/service-types/' + encodeURIComponent(editingId) : '/api/ops/service-types';
            const method = editingId ? 'PUT' : 'POST';
            const res = await global.apiFetch(url, { method: method, body: payload });
            if (!res.ok) {
                setBoxError('st-form-error', await extractErrorMessage(res, 'Không thể lưu gói dịch vụ.'));
                return;
            }
            serviceTypeModalInstance?.hide();
            await loadAllData();
            showPageAlert('success', 'Đã lưu cấu hình gói dịch vụ ' + payload.code + '.');
        } catch (_) {
            setBoxError('st-form-error', 'Lỗi kết nối máy chủ.');
        } finally {
            btn.disabled = false;
        }
    }

    function downloadCsv(filename, header, rows) {
        const lines = [header.join(',')];
        rows.forEach(function (cols) {
            lines.push(cols.map(function (v) {
                return '"' + String(v == null ? '' : v).replace(/"/g, '""') + '"';
            }).join(','));
        });
        const blob = new Blob(['\uFEFF' + lines.join('\r\n')], { type: 'text/csv;charset=utf-8;' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = filename;
        document.body.appendChild(a);
        a.click();
        a.remove();
        URL.revokeObjectURL(url);
    }

    async function loadAllData() {
        const reqs = [
            global.apiFetch('/api/ops/service-types').then(function (r) { return r.ok ? r.json() : []; }).catch(function () { return []; }),
            canViewRates
                ? global.apiFetch('/api/ops/rate-cards').then(function (r) { return r.ok ? r.json() : []; }).catch(function () { return []; })
                : Promise.resolve([]),
            global.apiFetch('/api/ops/shipments?size=50&sort=createdAt,desc').then(function (r) { return r.ok ? r.json() : null; }).catch(function () { return null; })
        ];

        const [svcRes, rcRes, shipRes] = await Promise.all(reqs);
        serviceTypes = Array.isArray(svcRes) ? svcRes : [];
        rateCards = Array.isArray(rcRes) ? rcRes : [];
        shipments = shipRes && Array.isArray(shipRes.content) ? shipRes.content : [];

        populateServiceSelects();
        updateKpis();
        renderServiceTypes();
        renderRateCards();
        renderCodTable();
    }

    function bindTabs() {
        const btns = document.querySelectorAll('[data-fin-tab]');
        btns.forEach(function (btn) {
            btn.addEventListener('click', function () {
                const target = btn.getAttribute('data-fin-tab');
                btns.forEach(function (b) {
                    const active = b === btn;
                    b.classList.toggle('is-active', active);
                    b.setAttribute('aria-selected', String(active));
                    const panel = document.getElementById(b.getAttribute('data-fin-tab'));
                    if (panel) panel.classList.toggle('d-none', !active);
                });
                if (target === 'panel-quote') {
                    loadProvincesIfNeeded();
                }
            });
        });
    }

    function bindEvents() {
        bindTabs();

        document.getElementById('btn-refresh-finance')?.addEventListener('click', loadAllData);
        document.getElementById('btn-open-create-rate')?.addEventListener('click', function () {
            openRateCardModal(null);
        });
        document.getElementById('btn-open-create-service')?.addEventListener('click', function () {
            openServiceTypeModal(null);
        });

        document.getElementById('filter-rate-service')?.addEventListener('change', renderRateCards);
        document.getElementById('filter-rate-zone')?.addEventListener('change', renderRateCards);
        document.getElementById('filter-rate-active')?.addEventListener('change', renderRateCards);

        document.getElementById('cod-search')?.addEventListener('input', renderCodTable);
        document.getElementById('cod-stage-filter')?.addEventListener('change', renderCodTable);

        document.getElementById('quote-form')?.addEventListener('submit', handleCalculateQuote);
        document.getElementById('rate-card-form')?.addEventListener('submit', handleSaveRateCard);
        document.getElementById('service-type-form')?.addEventListener('submit', handleSaveServiceType);

        document.getElementById('btn-export-rates-csv')?.addEventListener('click', function () {
            const rows = getFilteredRateCards().map(function (rc) {
                return [
                    rc.serviceTypeCode, rc.serviceTypeName, rc.zoneType,
                    rc.baseWeightGram, rc.basePrice, rc.stepWeightGram, rc.stepPrice,
                    rc.returnFeeRatio, rc.isActive ? 'ACTIVE' : 'HISTORY', rc.effectiveFrom
                ];
            });
            downloadCsv('ute-express-rate-cards.csv',
                ['ServiceCode', 'ServiceName', 'ZoneType', 'BaseWeightGram', 'BasePrice', 'StepWeightGram', 'StepPrice', 'ReturnFeeRatio', 'Status', 'EffectiveFrom'],
                rows);
        });

        document.getElementById('btn-export-cod-csv')?.addEventListener('click', function () {
            const rows = getFilteredCodShipments().map(function (s) {
                return [
                    s.trackingCode, s.currentStatus, s.senderName, s.receiverName,
                    s.feePayer, s.codAmount, s.shippingFee, s.amountToCollect, computeNetPayout(s)
                ];
            });
            downloadCsv('ute-express-cod-cashflow.csv',
                ['TrackingCode', 'Status', 'Sender', 'Receiver', 'FeePayer', 'CodAmount', 'ShippingFee', 'AmountToCollect', 'EstimatedShopPayout'],
                rows);
        });

        document.addEventListener('click', function (e) {
            const btn = e.target.closest('[data-action]');
            if (!btn) return;
            const action = btn.getAttribute('data-action');
            const id = btn.getAttribute('data-id');
            if (action === 'clone-rate' && id) {
                const rc = rateCards.find(function (x) { return x.id === id; });
                if (rc) openRateCardModal(rc);
            } else if (action === 'edit-service' && id) {
                const st = serviceTypes.find(function (x) { return x.id === id; });
                if (st) openServiceTypeModal(st);
            }
        });
    }

    document.addEventListener('DOMContentLoaded', function () {
        const mainEl = document.getElementById('main-content');
        isAdmin = mainEl?.getAttribute('data-is-admin') === 'true';
        canViewRates = mainEl?.getAttribute('data-can-view-rates') === 'true';

        if (global.bootstrap) {
            const m1 = document.getElementById('rateCardModal');
            const m2 = document.getElementById('serviceTypeModal');
            if (m1) rateCardModalInstance = new global.bootstrap.Modal(m1);
            if (m2) serviceTypeModalInstance = new global.bootstrap.Modal(m2);
        }

        bindEvents();
        loadAllData();
    });
})(window);
