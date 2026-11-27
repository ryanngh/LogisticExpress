(function (global) {
    'use strict';

    const PICKUP_STAGE = new Set(['CREATED', 'PICKUP_ASSIGNED', 'PICKED_UP']);
    const TRANSIT_STAGE = new Set(['AT_ORIGIN_HUB', 'IN_LINEHAUL', 'AT_DEST_HUB', 'OUT_FOR_DELIVERY']);

    let currentItems = [];
    let currentPage = 0;
    let pageSize = 20;
    let totalPages = 0;
    let totalElements = 0;

    let canAssign = false;
    let canIntake = false;
    let hubsLoaded = false;

    let assignPickupModalInstance = null;
    let intakeModalInstance = null;

    function refreshIcons() {
        if (global.lucide && typeof global.lucide.createIcons === 'function') {
            global.lucide.createIcons();
        }
    }

    function showPageAlert(type, message) {
        const el = document.getElementById('shipments-alert');
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

    function formatShortAddress(addr) {
        if (!addr) return '—';
        return [addr.wardName, addr.provinceName].filter(Boolean).join(', ') || addr.rawText || '—';
    }

    function formatFullAddress(addr) {
        if (!addr) return '—';
        return [addr.streetDetail, addr.wardName, addr.provinceName].filter(Boolean).join(', ') || addr.rawText || '—';
    }

    function syncStatusPills(statusVal) {
        document.querySelectorAll('[data-status-pill]').forEach(function (btn) {
            btn.classList.toggle('is-active', btn.getAttribute('data-status-pill') === (statusVal || ''));
        });
    }

    function updateKpis() {
        const c = global.opsCommon;
        let pickupCount = 0;
        let transitCount = 0;
        let totalCod = 0;
        let totalFee = 0;

        currentItems.forEach(function (s) {
            if (PICKUP_STAGE.has(s.currentStatus)) pickupCount++;
            if (TRANSIT_STAGE.has(s.currentStatus)) transitCount++;
            totalCod += Number(s.codAmount || 0);
            totalFee += Number(s.shippingFee || 0);
        });

        const set = function (id, txt) {
            const el = document.getElementById(id);
            if (el) el.textContent = txt;
        };
        set('stat-ship-total', c.formatNumber(totalElements));
        set('stat-ship-page-hint', 'Trang ' + (totalPages > 0 ? (currentPage + 1) : 0) + ' / ' + totalPages);
        set('stat-ship-pickup', c.formatNumber(pickupCount));
        set('stat-ship-transit', c.formatNumber(transitCount));
        set('stat-ship-cod', c.formatVnd(totalCod));
        set('stat-ship-fee', 'Tổng cước: ' + c.formatVnd(totalFee));
    }

    function renderTable() {
        const tbody = document.getElementById('shipments-table-body');
        if (!tbody) return;
        const c = global.opsCommon;
        const esc = c.escapeHtml;

        if (!currentItems || currentItems.length === 0) {
            const hasFilter = Boolean(
                document.getElementById('f-tracking-code')?.value.trim() ||
                document.getElementById('f-status')?.value ||
                document.getElementById('f-from-date')?.value ||
                document.getElementById('f-to-date')?.value
            );
            tbody.innerHTML =
                '<tr><td colspan="7" class="text-center py-5">' +
                '<div class="fw-semibold mb-1">' + (hasFilter ? 'Không tìm thấy vận đơn phù hợp' : 'Chưa có vận đơn nào trong hệ thống') + '</div>' +
                '<div class="small text-muted mb-3">' + (hasFilter ? 'Thử thay đổi mã vận đơn, trạng thái hoặc khoảng thời gian tìm kiếm.' : 'Vận đơn do Shop tạo mới sẽ tự động xuất hiện tại đây.') + '</div>' +
                (hasFilter ? '<button type="button" class="le-btn le-btn-tertiary" data-action="clear-filters">Xóa toàn bộ bộ lọc</button>' : '') +
                '</td></tr>';
            return;
        }

        tbody.innerHTML = currentItems.map(function (s) {
            const detailUrl = '/ops/shipments/' + encodeURIComponent(s.trackingCode);
            const feePayerLabel = s.feePayer === 'RECEIVER' ? 'Người nhận trả cước' : 'Shop trả cước';
            const weightText = s.chargeableWeightGram ? (c.formatNumber(s.chargeableWeightGram) + ' g') : '—';

            const showAssign = canAssign && s.currentStatus === 'CREATED';
            const showIntake = canIntake && (s.currentStatus === 'CREATED' || s.currentStatus === 'PICKED_UP');

            const assignBtn = showAssign
                ? '<button type="button" class="le-btn-icon" data-action="assign-pickup" data-id="' + esc(s.id) + '" title="Gán Shipper lấy hàng" aria-label="Gán Shipper lấy hàng">' +
                      '<i data-lucide="user-check" style="width:15px;height:15px;" aria-hidden="true"></i>' +
                  '</button>'
                : '';

            const intakeBtn = showIntake
                ? '<button type="button" class="le-btn-icon" data-action="intake" data-id="' + esc(s.id) + '" title="Tiếp nhận tại Hub gốc" aria-label="Tiếp nhận tại Hub gốc">' +
                      '<i data-lucide="package-check" style="width:15px;height:15px;" aria-hidden="true"></i>' +
                  '</button>'
                : '';

            const currentHubTag = s.currentHubName
                ? '<div class="small text-success fw-semibold mt-1">Hiện tại: ' + esc(s.currentHubName) + '</div>'
                : '';

            return '<tr>' +
                '<td>' +
                    '<a href="' + detailUrl + '" class="fw-bold font-monospace text-decoration-none d-inline-block" style="color:var(--le-primary-hover);">' +
                        esc(s.trackingCode) +
                    '</a>' +
                    '<div class="small text-muted">' + esc(s.serviceTypeName || s.serviceTypeCode || '—') + ' · ' + esc(weightText) + '</div>' +
                '</td>' +
                '<td>' +
                    '<div class="small"><span class="text-muted">Gửi:</span> <span class="fw-semibold">' + esc(s.senderName || '—') + '</span> <span class="text-muted">(' + esc(s.senderPhone || '—') + ')</span></div>' +
                    '<div class="small mt-1"><span class="text-muted">Nhận:</span> <span class="fw-semibold">' + esc(s.receiverName || '—') + '</span> <span class="text-muted">(' + esc(s.receiverPhone || '—') + ')</span></div>' +
                    '<div class="small text-muted text-truncate" style="max-width:260px;" title="' + esc(formatFullAddress(s.deliveryAddress)) + '">' +
                        esc(formatShortAddress(s.deliveryAddress)) +
                    '</div>' +
                '</td>' +
                '<td>' +
                    '<div class="small fw-semibold">' + esc(s.originHubName || 'Chưa rõ') + '</div>' +
                    '<div class="small text-muted">&rarr; ' + esc(s.destHubName || 'Chưa rõ') + '</div>' +
                    currentHubTag +
                '</td>' +
                '<td>' + c.renderStatusBadge(s.currentStatus) + '</td>' +
                '<td class="text-end le-tabular">' +
                    '<div class="fw-bold" style="color:var(--le-wine);">' + esc(c.formatVnd(s.codAmount)) + '</div>' +
                    '<div class="small text-muted">Cước: ' + esc(c.formatVnd(s.shippingFee)) + '</div>' +
                    '<div class="text-muted" style="font-size:11px;">' + esc(feePayerLabel) + '</div>' +
                '</td>' +
                '<td class="small le-tabular text-muted">' + esc(c.formatDateTime(s.createdAt)) + '</td>' +
                '<td class="text-end">' +
                    '<div class="d-inline-flex align-items-center gap-1">' +
                        assignBtn +
                        intakeBtn +
                        '<a href="' + detailUrl + '" class="le-btn-icon" title="Xem chi tiết vận đơn" aria-label="Xem chi tiết vận đơn">' +
                            '<i data-lucide="arrow-up-right" style="width:15px;height:15px;" aria-hidden="true"></i>' +
                        '</a>' +
                    '</div>' +
                '</td>' +
            '</tr>';
        }).join('');

        refreshIcons();
    }

    function renderPagination() {
        const infoEl = document.getElementById('shipments-pagination-info');
        const ul = document.getElementById('shipments-pagination');
        if (!infoEl || !ul) return;

        if (totalElements === 0) {
            infoEl.textContent = 'Hiển thị 0 vận đơn';
            ul.innerHTML = '';
            return;
        }

        const start = currentPage * pageSize + 1;
        const end = Math.min(totalElements, start + currentItems.length - 1);
        infoEl.textContent = 'Đang xem ' + start + '–' + end + ' trên tổng ' + global.opsCommon.formatNumber(totalElements) + ' vận đơn';

        const pages = [];
        const maxButtons = 5;
        let from = Math.max(0, currentPage - 2);
        let to = Math.min(totalPages - 1, from + maxButtons - 1);
        if (to - from < maxButtons - 1) {
            from = Math.max(0, to - maxButtons + 1);
        }

        let html = '<li class="page-item' + (currentPage <= 0 ? ' disabled' : '') + '">' +
            '<button type="button" class="page-link" data-page="' + (currentPage - 1) + '">&laquo;</button></li>';

        for (let p = from; p <= to; p++) {
            pages.push(p);
            html += '<li class="page-item' + (p === currentPage ? ' active' : '') + '">' +
                '<button type="button" class="page-link" data-page="' + p + '">' + (p + 1) + '</button></li>';
        }

        html += '<li class="page-item' + (currentPage >= totalPages - 1 ? ' disabled' : '') + '">' +
            '<button type="button" class="page-link" data-page="' + (currentPage + 1) + '">&raquo;</button></li>';

        ul.innerHTML = html;
    }

    async function loadShipments(pageIdx) {
        if (typeof pageIdx === 'number') {
            currentPage = Math.max(0, pageIdx);
        }

        const tbody = document.getElementById('shipments-table-body');
        if (tbody) {
            tbody.innerHTML =
                '<tr><td colspan="7" class="py-4"><div class="d-flex flex-column gap-2">' +
                '<div class="le-skeleton" style="height:24px;width:100%;"></div>' +
                '<div class="le-skeleton" style="height:24px;width:95%;"></div>' +
                '<div class="le-skeleton" style="height:24px;width:90%;"></div>' +
                '</div></td></tr>';
        }

        const params = new URLSearchParams();
        params.set('page', String(currentPage));
        params.set('size', String(pageSize));
        params.set('sort', 'createdAt,desc');

        const trackingCode = (document.getElementById('f-tracking-code')?.value || '').trim();
        const status = document.getElementById('f-status')?.value || '';
        const fromDate = document.getElementById('f-from-date')?.value || '';
        const toDate = document.getElementById('f-to-date')?.value || '';

        if (trackingCode) params.set('trackingCode', trackingCode);
        if (status) params.set('status', status);
        if (fromDate) params.set('fromDate', fromDate);
        if (toDate) params.set('toDate', toDate);

        syncStatusPills(status);

        try {
            const res = await global.apiFetch('/api/ops/shipments?' + params.toString());
            if (!res.ok) {
                showPageAlert('danger', await extractErrorMessage(res, 'Không thể tải danh sách vận đơn.'));
                currentItems = [];
                totalElements = 0;
                totalPages = 0;
                updateKpis();
                renderTable();
                renderPagination();
                return;
            }
            const data = await res.json();
            const meta = data.page || data;
            currentItems = Array.isArray(data.content) ? data.content : [];
            totalElements = Number(meta.totalElements != null ? meta.totalElements : currentItems.length);
            totalPages = Number(meta.totalPages != null ? meta.totalPages : (currentItems.length > 0 ? 1 : 0));
            currentPage = Number(meta.number != null ? meta.number : currentPage);

            updateKpis();
            renderTable();
            renderPagination();
        } catch (_) {
            showPageAlert('danger', 'Lỗi kết nối máy chủ khi tải danh sách vận đơn.');
        }
    }

    async function ensureHubsForIntake() {
        if (hubsLoaded) return;
        try {
            const res = await global.apiFetch('/api/ops/hubs');
            if (!res.ok) return;
            const hubs = await res.json();
            const esc = global.opsCommon.escapeHtml;
            const select = document.getElementById('in-hub-id');
            if (select && Array.isArray(hubs)) {
                select.innerHTML = '<option value="">-- Tự động theo Hub mặc định của nhân viên / đơn --</option>' +
                    hubs.map(function (h) {
                        return '<option value="' + esc(h.id) + '">' + esc(h.name) + ' (' + esc(h.code) + ')</option>';
                    }).join('');
                hubsLoaded = true;
            }
        } catch (_) { /* ignore */ }
    }

    async function openAssignPickupModal(shipmentId) {
        const s = currentItems.find(function (x) { return x.id === shipmentId; });
        if (!s) return;

        setBoxError('ap-form-error', '');
        document.getElementById('ap-shipment-id').value = s.id;
        document.getElementById('ap-tracking-label').textContent = s.trackingCode;
        document.getElementById('ap-origin-hub').textContent = s.originHubName || 'Chưa xác định';
        document.getElementById('ap-pickup-address').textContent = formatFullAddress(s.pickupAddress);
        document.getElementById('ap-note').value = '';

        const select = document.getElementById('ap-shipper-id');
        select.innerHTML = '<option value="">-- Đang tải danh sách Shipper... --</option>';
        assignPickupModalInstance?.show();

        try {
            const q = new URLSearchParams({ role: 'ROLE_SHIPPER' });
            if (s.originHubId) q.set('hubId', s.originHubId);
            const res = await global.apiFetch('/api/ops/users?' + q.toString());
            if (!res.ok) {
                select.innerHTML = '<option value="">Không tải được danh sách Shipper</option>';
                setBoxError('ap-form-error', await extractErrorMessage(res, 'Không thể tải danh sách Shipper.'));
                return;
            }
            const staff = await res.json();
            const activeShippers = (Array.isArray(staff) ? staff : []).filter(function (u) {
                return u.status === 'ACTIVE';
            });
            const esc = global.opsCommon.escapeHtml;
            if (activeShippers.length === 0) {
                select.innerHTML = '<option value="">Không có Shipper đang hoạt động tại Hub gốc này</option>';
                setBoxError('ap-form-error', 'Chưa có tài khoản Shipper ACTIVE thuộc Hub gốc. Hãy gán Hub cho Shipper tại mục Người dùng & Nhân sự.');
                return;
            }
            select.innerHTML = '<option value="">-- Chọn Shipper nhận lấy hàng --</option>' +
                activeShippers.map(function (u) {
                    const plate = u.staffProfile?.vehiclePlate ? ' · ' + u.staffProfile.vehiclePlate : '';
                    const code = u.staffProfile?.employeeCode ? ' (' + u.staffProfile.employeeCode + ')' : '';
                    return '<option value="' + esc(u.id) + '">' + esc(u.fullName || u.username) + esc(code) + esc(plate) + '</option>';
                }).join('');
        } catch (_) {
            setBoxError('ap-form-error', 'Lỗi kết nối khi tải danh sách Shipper.');
        }
    }

    async function handleAssignPickupSubmit(e) {
        e.preventDefault();
        setBoxError('ap-form-error', '');
        const shipmentId = document.getElementById('ap-shipment-id').value;
        const shipperId = document.getElementById('ap-shipper-id').value;
        const note = (document.getElementById('ap-note').value || '').trim();

        if (!shipperId) {
            setBoxError('ap-form-error', 'Vui lòng chọn một Shipper.');
            return;
        }

        const btn = document.getElementById('btn-submit-assign-pickup');
        btn.disabled = true;
        try {
            const res = await global.apiFetch('/api/ops/shipments/' + encodeURIComponent(shipmentId) + '/assign-pickup', {
                method: 'POST',
                body: { shipperId: shipperId, note: note || null }
            });
            if (!res.ok) {
                setBoxError('ap-form-error', await extractErrorMessage(res, 'Không thể gán Shipper lấy hàng.'));
                return;
            }
            const updated = await res.json();
            assignPickupModalInstance?.hide();
            showPageAlert('success', 'Đã gán Shipper lấy hàng cho vận đơn ' + updated.trackingCode + '.');
            loadShipments(currentPage);
        } catch (_) {
            setBoxError('ap-form-error', 'Lỗi kết nối máy chủ.');
        } finally {
            btn.disabled = false;
        }
    }

    async function openIntakeModal(shipment) {
        setBoxError('intake-form-error', '');
        await ensureHubsForIntake();
        document.getElementById('in-tracking-code').value = shipment ? shipment.trackingCode : '';
        document.getElementById('in-hub-id').value = shipment && shipment.originHubId ? shipment.originHubId : '';
        document.getElementById('in-note').value = '';
        intakeModalInstance?.show();
    }

    async function handleIntakeSubmit(e) {
        e.preventDefault();
        setBoxError('intake-form-error', '');
        const trackingCode = (document.getElementById('in-tracking-code').value || '').trim().toUpperCase();
        const hubId = document.getElementById('in-hub-id').value || null;
        const note = (document.getElementById('in-note').value || '').trim();

        if (!trackingCode) {
            setBoxError('intake-form-error', 'Vui lòng nhập hoặc quét mã vận đơn.');
            return;
        }

        const btn = document.getElementById('btn-submit-intake');
        btn.disabled = true;
        try {
            const res = await global.apiFetch('/api/ops/shipments/intake', {
                method: 'POST',
                body: { trackingCode: trackingCode, hubId: hubId, note: note || null }
            });
            if (!res.ok) {
                setBoxError('intake-form-error', await extractErrorMessage(res, 'Không thể tiếp nhận vận đơn tại Hub.'));
                return;
            }
            const updated = await res.json();
            intakeModalInstance?.hide();
            showPageAlert('success', 'Đã tiếp nhận vận đơn ' + updated.trackingCode + ' vào ' + (updated.currentHubName || 'Hub gốc') + '.');
            loadShipments(currentPage);
        } catch (_) {
            setBoxError('intake-form-error', 'Lỗi kết nối máy chủ.');
        } finally {
            btn.disabled = false;
        }
    }

    function exportShipmentsCsv() {
        const header = ['TrackingCode', 'Status', 'Service', 'SenderName', 'SenderPhone', 'ReceiverName', 'ReceiverPhone', 'OriginHub', 'DestHub', 'ChargeableWeightGram', 'CodAmount', 'ShippingFee', 'FeePayer', 'CreatedAt'];
        const lines = [header.join(',')];

        currentItems.forEach(function (s) {
            const cols = [
                s.trackingCode || '',
                s.currentStatus || '',
                s.serviceTypeCode || '',
                s.senderName || '',
                s.senderPhone || '',
                s.receiverName || '',
                s.receiverPhone || '',
                s.originHubName || '',
                s.destHubName || '',
                s.chargeableWeightGram != null ? s.chargeableWeightGram : '',
                s.codAmount != null ? s.codAmount : '',
                s.shippingFee != null ? s.shippingFee : '',
                s.feePayer || '',
                s.createdAt || ''
            ].map(function (v) {
                return '"' + String(v).replace(/"/g, '""') + '"';
            });
            lines.push(cols.join(','));
        });

        const blob = new Blob(['\uFEFF' + lines.join('\r\n')], { type: 'text/csv;charset=utf-8;' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = 'ute-express-shipments-page-' + (currentPage + 1) + '.csv';
        document.body.appendChild(a);
        a.click();
        a.remove();
        URL.revokeObjectURL(url);
    }

    function resetFilters() {
        document.getElementById('f-tracking-code').value = '';
        document.getElementById('f-status').value = '';
        document.getElementById('f-from-date').value = '';
        document.getElementById('f-to-date').value = '';
        loadShipments(0);
    }

    function bindEvents() {
        document.getElementById('shipments-filter-form')?.addEventListener('submit', function (e) {
            e.preventDefault();
            loadShipments(0);
        });
        document.getElementById('btn-refresh-shipments')?.addEventListener('click', function () {
            loadShipments(currentPage);
        });
        document.getElementById('btn-export-shipments-csv')?.addEventListener('click', exportShipmentsCsv);
        document.getElementById('btn-reset-shipment-filters')?.addEventListener('click', resetFilters);
        document.getElementById('btn-open-quick-intake')?.addEventListener('click', function () {
            openIntakeModal(null);
        });

        document.getElementById('f-status')?.addEventListener('change', function () {
            loadShipments(0);
        });
        document.getElementById('f-page-size')?.addEventListener('change', function (e) {
            pageSize = Number(e.target.value) || 20;
            loadShipments(0);
        });

        document.querySelectorAll('[data-status-pill]').forEach(function (btn) {
            btn.addEventListener('click', function () {
                const statusVal = btn.getAttribute('data-status-pill') || '';
                const select = document.getElementById('f-status');
                if (select) select.value = statusVal;
                loadShipments(0);
            });
        });

        document.getElementById('shipments-pagination')?.addEventListener('click', function (e) {
            const btn = e.target.closest('[data-page]');
            if (!btn || btn.closest('.disabled') || btn.closest('.active')) return;
            const targetPage = Number(btn.getAttribute('data-page'));
            if (!isNaN(targetPage) && targetPage >= 0 && targetPage < totalPages) {
                loadShipments(targetPage);
            }
        });

        document.getElementById('assign-pickup-form')?.addEventListener('submit', handleAssignPickupSubmit);
        document.getElementById('intake-form')?.addEventListener('submit', handleIntakeSubmit);

        document.getElementById('shipments-table-body')?.addEventListener('click', function (e) {
            const btn = e.target.closest('[data-action]');
            if (!btn) return;
            const action = btn.getAttribute('data-action');
            const id = btn.getAttribute('data-id');
            if (action === 'assign-pickup' && id) {
                openAssignPickupModal(id);
            } else if (action === 'intake' && id) {
                const s = currentItems.find(function (x) { return x.id === id; });
                openIntakeModal(s || null);
            } else if (action === 'clear-filters') {
                resetFilters();
            }
        });
    }

    document.addEventListener('DOMContentLoaded', function () {
        const mainEl = document.getElementById('main-content');
        canAssign = mainEl?.getAttribute('data-can-assign') === 'true';
        canIntake = mainEl?.getAttribute('data-can-intake') === 'true';

        if (global.bootstrap) {
            const m1 = document.getElementById('assignPickupModal');
            const m2 = document.getElementById('intakeModal');
            if (m1) assignPickupModalInstance = new global.bootstrap.Modal(m1);
            if (m2) intakeModalInstance = new global.bootstrap.Modal(m2);
        }

        // Pre-fill filters from URL query string if navigated with ?status=... or ?trackingCode=...
        const urlParams = new URLSearchParams(global.location.search);
        if (urlParams.get('status')) {
            const sel = document.getElementById('f-status');
            if (sel) sel.value = urlParams.get('status');
        }
        if (urlParams.get('trackingCode')) {
            const inp = document.getElementById('f-tracking-code');
            if (inp) inp.value = urlParams.get('trackingCode');
        }

        bindEvents();
        loadShipments(0);
    });
})(window);
