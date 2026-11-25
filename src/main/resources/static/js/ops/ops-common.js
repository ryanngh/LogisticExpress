(function (global) {
    'use strict';

    const STATUS_MAP = {
        CREATED: { label: 'Mới tạo', cls: 'le-badge-neutral' },
        PICKUP_ASSIGNED: { label: 'Đã gán lấy hàng', cls: 'le-badge-info' },
        PICKED_UP: { label: 'Đã lấy hàng', cls: 'le-badge-info' },
        AT_ORIGIN_HUB: { label: 'Tại Hub gốc', cls: 'le-badge-teal' },
        IN_LINEHAUL: { label: 'Đang trung chuyển', cls: 'le-badge-info' },
        AT_DEST_HUB: { label: 'Tại Hub đích', cls: 'le-badge-teal' },
        OUT_FOR_DELIVERY: { label: 'Đang giao hàng', cls: 'le-badge-warning' },
        DELIVERED: { label: 'Giao thành công', cls: 'le-badge-success' },
        DELIVERY_FAILED: { label: 'Giao thất bại', cls: 'le-badge-danger' },
        REFUSED: { label: 'Khách từ chối', cls: 'le-badge-danger' },
        ON_HOLD_FOR_RETURN: { label: 'Chờ cứu đơn', cls: 'le-badge-warning' },
        RETURN_INITIATED: { label: 'Bắt đầu hoàn', cls: 'le-badge-warning' },
        IN_RETURN_TRANSIT: { label: 'Đang hoàn hàng', cls: 'le-badge-warning' },
        RETURNED: { label: 'Đã hoàn Shop', cls: 'le-badge-neutral' },
        CANCELLED: { label: 'Đã hủy', cls: 'le-badge-neutral' }
    };

    document.addEventListener('DOMContentLoaded', function () {
        if (global.lucide && typeof global.lucide.createIcons === 'function') {
            global.lucide.createIcons();
        }

        document.querySelectorAll('[data-ops-logout]').forEach(function (btn) {
            btn.addEventListener('click', function () {
                if (typeof global.authLogout === 'function') {
                    global.authLogout('/login?logged_out=1');
                }
            });
        });
    });

    function escapeHtml(str) {
        if (str == null) return '';
        return String(str)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
    }

    function formatNumber(val) {
        if (val == null || isNaN(Number(val))) return '0';
        return Number(val).toLocaleString('vi-VN');
    }

    function formatVnd(amount) {
        if (amount == null || isNaN(Number(amount))) return '0 đ';
        return Number(amount).toLocaleString('vi-VN') + ' đ';
    }

    function formatDateTime(iso) {
        if (!iso) return '—';
        try {
            return new Date(iso).toLocaleString('vi-VN', {
                hour: '2-digit',
                minute: '2-digit',
                day: '2-digit',
                month: '2-digit',
                year: 'numeric'
            });
        } catch (_) {
            return String(iso);
        }
    }

    function renderStatusBadge(status) {
        const meta = STATUS_MAP[status] || { label: status || 'Không rõ', cls: 'le-badge-neutral' };
        return '<span class="le-badge ' + meta.cls + '">' + escapeHtml(meta.label) + '</span>';
    }

    global.opsCommon = {
        STATUS_MAP: STATUS_MAP,
        escapeHtml: escapeHtml,
        formatNumber: formatNumber,
        formatVnd: formatVnd,
        formatDateTime: formatDateTime,
        renderStatusBadge: renderStatusBadge
    };
})(window);
