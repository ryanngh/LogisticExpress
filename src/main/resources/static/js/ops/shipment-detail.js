(function (global) {
    'use strict';

    let shipmentId = '';
    let trackingCode = '';
    let originHubId = '';

    let assignModalInstance = null;
    let intakeModalInstance = null;
    let reweighModalInstance = null;

    function showPageAlert(type, message) {
        const el = document.getElementById('detail-alert');
        if (!el) return;
        el.className = 'alert alert-' + type + ' alert-dismissible fade show mb-4 le-no-print';
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

    function hydrateStatusLabels() {
        const map = global.opsCommon?.STATUS_MAP || {};
        document.querySelectorAll('[data-status]').forEach(function (el) {
            const code = el.getAttribute('data-status');
            const meta = map[code];
            if (!meta) return;
            el.className = 'le-badge ' + meta.cls;
            el.textContent = meta.label;
        });

        document.querySelectorAll('[data-status-label]').forEach(function (el) {
            const code = el.getAttribute('data-status-label');
            const meta = map[code];
            if (meta) {
                el.textContent = meta.label + ' (' + code + ')';
            }
        });
    }

    function bindTabs() {
        const tabs = document.querySelectorAll('[data-detail-tab]');
        tabs.forEach(function (btn) {
            btn.addEventListener('click', function () {
                const targetId = btn.getAttribute('data-detail-tab');
                tabs.forEach(function (b) {
                    const active = b === btn;
                    b.classList.toggle('is-active', active);
                    b.setAttribute('aria-selected', String(active));
                    const panel = document.getElementById(b.getAttribute('data-detail-tab'));
                    if (panel) panel.classList.toggle('d-none', !active);
                });
                if (targetId === 'panel-events') {
                    hydrateStatusLabels();
                }
            });
        });
    }

    async function openAssignModal() {
        setBoxError('da-error', '');
        document.getElementById('da-note').value = '';
        const select = document.getElementById('da-shipper-id');
        select.innerHTML = '<option value="">-- Đang tải danh sách Shipper... --</option>';
        assignModalInstance?.show();

        try {
            const q = new URLSearchParams({ role: 'ROLE_SHIPPER' });
            if (originHubId) q.set('hubId', originHubId);
            const res = await global.apiFetch('/api/ops/users?' + q.toString());
            if (!res.ok) {
                setBoxError('da-error', await extractErrorMessage(res, 'Không thể tải danh sách Shipper.'));
                select.innerHTML = '<option value="">Không tải được Shipper</option>';
                return;
            }
            const staff = await res.json();
            const active = (Array.isArray(staff) ? staff : []).filter(function (u) {
                return u.status === 'ACTIVE';
            });
            const esc = global.opsCommon.escapeHtml;
            if (active.length === 0) {
                select.innerHTML = '<option value="">Không có Shipper ACTIVE tại Hub gốc</option>';
                setBoxError('da-error', 'Chưa có Shipper đang hoạt động thuộc Hub gốc của vận đơn.');
                return;
            }
            select.innerHTML = '<option value="">-- Chọn Shipper nhận lấy hàng --</option>' +
                active.map(function (u) {
                    const plate = u.staffProfile?.vehiclePlate ? ' · ' + u.staffProfile.vehiclePlate : '';
                    return '<option value="' + esc(u.id) + '">' + esc(u.fullName || u.username) + esc(plate) + '</option>';
                }).join('');
        } catch (_) {
            setBoxError('da-error', 'Lỗi kết nối khi tải danh sách Shipper.');
        }
    }

    async function handleAssignSubmit(e) {
        e.preventDefault();
        setBoxError('da-error', '');
        const shipperId = document.getElementById('da-shipper-id').value;
        const note = (document.getElementById('da-note').value || '').trim();
        if (!shipperId) {
            setBoxError('da-error', 'Vui lòng chọn một Shipper.');
            return;
        }
        const btn = document.getElementById('btn-da-submit');
        btn.disabled = true;
        try {
            const res = await global.apiFetch('/api/ops/shipments/' + encodeURIComponent(shipmentId) + '/assign-pickup', {
                method: 'POST',
                body: { shipperId: shipperId, note: note || null }
            });
            if (!res.ok) {
                setBoxError('da-error', await extractErrorMessage(res, 'Không thể gán Shipper lấy hàng.'));
                return;
            }
            assignModalInstance?.hide();
            global.location.reload();
        } catch (_) {
            setBoxError('da-error', 'Lỗi kết nối máy chủ.');
        } finally {
            btn.disabled = false;
        }
    }

    async function handleIntakeSubmit(e) {
        e.preventDefault();
        setBoxError('di-error', '');
        const note = (document.getElementById('di-note').value || '').trim();
        const btn = document.getElementById('btn-di-submit');
        btn.disabled = true;
        try {
            const res = await global.apiFetch('/api/ops/shipments/intake', {
                method: 'POST',
                body: {
                    trackingCode: trackingCode,
                    hubId: originHubId || null,
                    note: note || null
                }
            });
            if (!res.ok) {
                setBoxError('di-error', await extractErrorMessage(res, 'Không thể tiếp nhận vận đơn tại Hub.'));
                return;
            }
            intakeModalInstance?.hide();
            global.location.reload();
        } catch (_) {
            setBoxError('di-error', 'Lỗi kết nối máy chủ.');
        } finally {
            btn.disabled = false;
        }
    }

    function openReweighModal() {
        const mainEl = document.getElementById('main-content');
        setBoxError('dr-error', '');
        const resultEl = document.getElementById('dr-result');
        if (resultEl) resultEl.classList.add('d-none');

        document.getElementById('dr-weight').value = mainEl?.getAttribute('data-actual-weight') || '';
        document.getElementById('dr-length').value = mainEl?.getAttribute('data-length-cm') || '';
        document.getElementById('dr-width').value = mainEl?.getAttribute('data-width-cm') || '';
        document.getElementById('dr-height').value = mainEl?.getAttribute('data-height-cm') || '';
        document.getElementById('dr-note').value = '';
        reweighModalInstance?.show();
    }

    async function handleReweighSubmit(e) {
        e.preventDefault();
        setBoxError('dr-error', '');
        const resultEl = document.getElementById('dr-result');
        if (resultEl) resultEl.classList.add('d-none');

        const weight = parseInt(document.getElementById('dr-weight').value, 10);
        const lengthVal = document.getElementById('dr-length').value;
        const widthVal = document.getElementById('dr-width').value;
        const heightVal = document.getElementById('dr-height').value;
        const note = (document.getElementById('dr-note').value || '').trim();

        if (!weight || weight <= 0) {
            setBoxError('dr-error', 'Vui lòng nhập cân nặng thực tế (> 0 gram).');
            return;
        }

        const payload = {
            actualWeightGram: weight,
            lengthCm: lengthVal ? parseInt(lengthVal, 10) : null,
            widthCm: widthVal ? parseInt(widthVal, 10) : null,
            heightCm: heightVal ? parseInt(heightVal, 10) : null,
            note: note || null
        };

        const btn = document.getElementById('btn-dr-submit');
        btn.disabled = true;
        try {
            const res = await global.apiFetch('/api/ops/shipments/' + encodeURIComponent(shipmentId) + '/reweigh', {
                method: 'POST',
                body: payload
            });
            if (!res.ok) {
                setBoxError('dr-error', await extractErrorMessage(res, 'Không thể cập nhật kết quả cân đo.'));
                return;
            }
            const data = await res.json();
            const c = global.opsCommon;
            if (resultEl) {
                resultEl.innerHTML =
                    '<strong>Đã cập nhật cân đo thành công!</strong><br>' +
                    'KL tính cước: ' + c.formatNumber(data.oldChargeableWeightGram) + 'g &rarr; <strong>' + c.formatNumber(data.newChargeableWeightGram) + 'g</strong><br>' +
                    'Cước vận chuyển: ' + c.formatVnd(data.oldShippingFee) + ' &rarr; <strong>' + c.formatVnd(data.newShippingFee) + '</strong>';
                resultEl.classList.remove('d-none');
            }
            setTimeout(function () {
                global.location.reload();
            }, 1200);
        } catch (_) {
            setBoxError('dr-error', 'Lỗi kết nối máy chủ.');
        } finally {
            btn.disabled = false;
        }
    }

    document.addEventListener('DOMContentLoaded', function () {
        const mainEl = document.getElementById('main-content');
        shipmentId = mainEl?.getAttribute('data-shipment-id') || '';
        trackingCode = mainEl?.getAttribute('data-tracking-code') || '';
        originHubId = mainEl?.getAttribute('data-origin-hub-id') || '';

        hydrateStatusLabels();
        bindTabs();

        if (global.bootstrap) {
            const m1 = document.getElementById('detailAssignModal');
            const m2 = document.getElementById('detailIntakeModal');
            const m3 = document.getElementById('detailReweighModal');
            if (m1) assignModalInstance = new global.bootstrap.Modal(m1);
            if (m2) intakeModalInstance = new global.bootstrap.Modal(m2);
            if (m3) reweighModalInstance = new global.bootstrap.Modal(m3);
        }

        document.getElementById('btn-copy-tracking')?.addEventListener('click', function () {
            if (!trackingCode) return;
            if (navigator.clipboard && navigator.clipboard.writeText) {
                navigator.clipboard.writeText(trackingCode).then(function () {
                    showPageAlert('success', 'Đã sao chép mã vận đơn ' + trackingCode + ' vào bộ nhớ tạm.');
                });
            }
        });

        document.getElementById('btn-print-label')?.addEventListener('click', function () {
            global.print();
        });

        document.getElementById('btn-detail-assign-pickup')?.addEventListener('click', openAssignModal);
        document.getElementById('btn-detail-intake')?.addEventListener('click', function () {
            setBoxError('di-error', '');
            document.getElementById('di-note').value = '';
            intakeModalInstance?.show();
        });
        document.getElementById('btn-detail-reweigh')?.addEventListener('click', openReweighModal);

        document.getElementById('detail-assign-form')?.addEventListener('submit', handleAssignSubmit);
        document.getElementById('detail-intake-form')?.addEventListener('submit', handleIntakeSubmit);
        document.getElementById('detail-reweigh-form')?.addEventListener('submit', handleReweighSubmit);
    });
})(window);
