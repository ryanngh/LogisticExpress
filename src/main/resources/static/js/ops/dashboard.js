(function (global) {
    'use strict';

    const state = {
        allShipments: [],
        totalElements: 0,
        hubs: [],
        staff: [],
        activeRange: 'today',
        userPickedRange: false,
        chartMode: 'day'
    };

    const NORTH_KEYWORDS = ['ha noi', 'hà nội', 'hai phong', 'hải phòng', 'quang ninh', 'quảng ninh', 'bac ninh', 'bắc ninh', 'hung yen', 'hưng yên', 'hai duong', 'hải dương', 'thai nguyen', 'thái nguyên', 'nam dinh', 'nam định', 'ninh binh', 'ninh bình', 'thanh hoa', 'thanh hóa', 'nghe an', 'nghệ an', 'cau giay', 'cầu giấy', 'ba dinh', 'hoan kiem', 'hn-', 'mb-'];
    const CENTRAL_KEYWORDS = ['da nang', 'đà nẵng', 'hue', 'huế', 'quang nam', 'quảng nam', 'khanh hoa', 'khánh hòa', 'nha trang', 'binh dinh', 'bình định', 'quy nhon', 'gia lai', 'dak lak', 'đắk lắk', 'lam dong', 'lâm đồng', 'dn-', 'mt-'];

    function setText(id, value) {
        const el = document.getElementById(id);
        if (el) el.textContent = value;
    }

    async function safeJson(url) {
        try {
            const res = await global.apiFetch(url, { method: 'GET' });
            if (!res || !res.ok) return null;
            return await res.json();
        } catch (_) {
            return null;
        }
    }

    function formatCompactVnd(amount) {
        const n = Number(amount || 0);
        if (n >= 1e9) {
            return (n / 1e9).toFixed(2).replace('.', ',') + ' tỷ đ';
        }
        if (n >= 1e6) {
            return (n / 1e6).toFixed(1).replace('.', ',') + ' tr đ';
        }
        return global.opsCommon.formatVnd(n);
    }

    function formatPercent(value) {
        return Number(value || 0).toFixed(1).replace('.', ',') + '%';
    }

    function formatShortTime(iso) {
        if (!iso) return '—';
        try {
            const d = new Date(iso);
            const now = new Date();
            const isSameDay = d.getFullYear() === now.getFullYear()
                && d.getMonth() === now.getMonth()
                && d.getDate() === now.getDate();
            if (isSameDay) {
                return d.toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' });
            }
            return d.toLocaleDateString('vi-VN', { day: '2-digit', month: '2-digit' }) + ' ' +
                d.toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' });
        } catch (_) {
            return '—';
        }
    }

    function classifyRegion(text) {
        const norm = String(text || '').toLowerCase();
        if (NORTH_KEYWORDS.some(function (k) { return norm.includes(k); })) return 'north';
        if (CENTRAL_KEYWORDS.some(function (k) { return norm.includes(k); })) return 'central';
        return 'south';
    }

    function filterByRange(shipments, range, explicit) {
        if (!Array.isArray(shipments) || shipments.length === 0) return [];
        const now = Date.now();
        let cutoff = 0;

        if (range === 'today') {
            const startOfDay = new Date();
            startOfDay.setHours(0, 0, 0, 0);
            cutoff = startOfDay.getTime();
        } else if (range === '7d') {
            cutoff = now - 7 * 24 * 60 * 60 * 1000;
        } else if (range === '30d') {
            cutoff = now - 30 * 24 * 60 * 60 * 1000;
        }

        const filtered = shipments.filter(function (s) {
            if (!s.createdAt) return true;
            const ts = new Date(s.createdAt).getTime();
            return !isNaN(ts) && ts >= cutoff;
        });

        // On initial auto-load, if today has 0 shipments in a seeded DB, show all loaded shipments
        // so the admin control center displays meaningful metrics immediately.
        if (!explicit && filtered.length === 0) {
            return shipments;
        }
        return filtered;
    }

    function renderShipmentOverviewChart(shipments) {
        const container = document.getElementById('chart-shipment-overview');
        if (!container) return;

        const subtitleEl = document.getElementById('chart-overview-subtitle');
        const mode = state.chartMode;
        let buckets = [];

        if (mode === 'day') {
            if (subtitleEl) subtitleEl.textContent = 'Số lượng vận đơn theo ngày trong tuần';
            const dayLabels = ['T2', 'T3', 'T4', 'T5', 'T6', 'T7', 'CN'];
            buckets = dayLabels.map(function (lbl) { return { label: lbl, count: 0 }; });
            shipments.forEach(function (s) {
                if (!s.createdAt) return;
                const d = new Date(s.createdAt);
                if (isNaN(d.getTime())) return;
                const idx = (d.getDay() + 6) % 7; // Mon=0 .. Sun=6
                buckets[idx].count++;
            });
        } else if (mode === 'week') {
            if (subtitleEl) subtitleEl.textContent = 'Số lượng vận đơn theo 4 tuần gần nhất';
            buckets = [
                { label: 'Tuần 1', count: 0 },
                { label: 'Tuần 2', count: 0 },
                { label: 'Tuần 3', count: 0 },
                { label: 'Tuần 4', count: 0 }
            ];
            const now = Date.now();
            const weekMs = 7 * 24 * 60 * 60 * 1000;
            shipments.forEach(function (s) {
                if (!s.createdAt) return;
                const age = now - new Date(s.createdAt).getTime();
                const wIdx = 3 - Math.min(3, Math.max(0, Math.floor(age / weekMs)));
                buckets[wIdx].count++;
            });
        } else {
            if (subtitleEl) subtitleEl.textContent = 'Số lượng vận đơn theo 6 tháng gần nhất';
            const now = new Date();
            for (let i = 5; i >= 0; i--) {
                const dt = new Date(now.getFullYear(), now.getMonth() - i, 1);
                buckets.push({
                    label: 'T' + (dt.getMonth() + 1),
                    year: dt.getFullYear(),
                    month: dt.getMonth(),
                    count: 0
                });
            }
            shipments.forEach(function (s) {
                if (!s.createdAt) return;
                const d = new Date(s.createdAt);
                const found = buckets.find(function (b) {
                    return b.year === d.getFullYear() && b.month === d.getMonth();
                });
                if (found) found.count++;
            });
        }

        const counts = buckets.map(function (b) { return b.count; });
        const maxVal = Math.max.apply(null, counts.concat([1]));
        const sortedUnique = Array.from(new Set(counts.filter(function (v) { return v > 0; }))).sort(function (a, b) { return b - a; });
        const top1 = sortedUnique[0] || -1;
        const top2 = sortedUnique[1] || -1;

        const chartLeft = 36;
        const chartRight = 465;
        const baseY = 140;
        const maxBarH = 114;
        const slotW = (chartRight - chartLeft) / buckets.length;
        const barW = Math.min(38, Math.max(24, Math.floor(slotW * 0.56)));

        const barsSvg = buckets.map(function (b, idx) {
            const centerX = chartLeft + slotW * idx + slotW / 2;
            const x = (centerX - barW / 2).toFixed(1);
            const h = b.count > 0 ? Math.max(10, Math.round((b.count / maxVal) * maxBarH)) : 4;
            const y = baseY - h;

            let fill = '#FEE2E2';
            let labelFill = '#64748B';
            let labelWeight = '400';
            if (b.count > 0 && b.count === top1) {
                fill = '#DC2626';
                labelFill = '#172033';
                labelWeight = '700';
            } else if (b.count > 0 && b.count === top2) {
                fill = '#7F1D1D';
                labelFill = '#172033';
                labelWeight = '700';
            }

            return '<g class="le-svg-bar">' +
                '<title>' + global.opsCommon.escapeHtml(b.label) + ': ' + global.opsCommon.formatNumber(b.count) + ' vận đơn</title>' +
                '<rect x="' + x + '" y="' + y + '" width="' + barW + '" height="' + h + '" rx="5" fill="' + fill + '"></rect>' +
                '<text x="' + centerX.toFixed(1) + '" y="158" text-anchor="middle" fill="' + labelFill + '" font-weight="' + labelWeight + '" font-size="11" font-family="Inter, sans-serif">' +
                    global.opsCommon.escapeHtml(b.label) +
                '</text>' +
            '</g>';
        }).join('');

        container.innerHTML =
            '<svg viewBox="0 0 480 170" class="le-svg-chart" style="height:176px;" role="img" aria-label="Biểu đồ cột số lượng vận đơn">' +
                '<line x1="36" y1="20" x2="465" y2="20" stroke="#E2E8F0" stroke-dasharray="3 3"></line>' +
                '<line x1="36" y1="60" x2="465" y2="60" stroke="#E2E8F0" stroke-dasharray="3 3"></line>' +
                '<line x1="36" y1="100" x2="465" y2="100" stroke="#E2E8F0" stroke-dasharray="3 3"></line>' +
                '<line x1="36" y1="140" x2="465" y2="140" stroke="#CBD5E1"></line>' +
                barsSvg +
            '</svg>';
    }

    function renderShipmentStatusDonut(counts, successRateNum) {
        const container = document.getElementById('chart-shipment-status');
        if (!container) return;

        const pendingAndFailed = counts.created + counts.pickup + counts.failed;
        const total = counts.delivered + counts.transit + counts.delivering + pendingAndFailed;

        const pctDelivered = total > 0 ? (counts.delivered / total) * 100 : 0;
        const pctTransit = total > 0 ? (counts.transit / total) * 100 : 0;
        const pctDelivering = total > 0 ? (counts.delivering / total) * 100 : 0;
        const pctOther = total > 0 ? Math.max(0, 100 - pctDelivered - pctTransit - pctDelivering) : 0;

        const circumference = 2 * Math.PI * 52; // ~326.73
        const lenDelivered = (pctDelivered / 100) * circumference;
        const lenTransit = (pctTransit / 100) * circumference;
        const lenDelivering = (pctDelivering / 100) * circumference;

        const centerPct = total > 0 ? formatPercent(successRateNum) : '0,0%';

        container.innerHTML =
            '<svg viewBox="0 0 140 140" style="width:144px;height:144px;flex-shrink:0;" role="img" aria-label="Biểu đồ tròn trạng thái vận đơn">' +
                '<circle cx="70" cy="70" r="52" fill="none" stroke="#E2E8F0" stroke-width="18"></circle>' +
                (lenDelivered > 0 ? '<circle cx="70" cy="70" r="52" fill="none" stroke="#15803D" stroke-width="18" stroke-dasharray="' + lenDelivered.toFixed(1) + ' ' + circumference.toFixed(1) + '" stroke-dashoffset="0" transform="rotate(-90 70 70)"></circle>' : '') +
                (lenTransit > 0 ? '<circle cx="70" cy="70" r="52" fill="none" stroke="#7F1D1D" stroke-width="18" stroke-dasharray="' + lenTransit.toFixed(1) + ' ' + circumference.toFixed(1) + '" stroke-dashoffset="' + (-lenDelivered).toFixed(1) + '" transform="rotate(-90 70 70)"></circle>' : '') +
                (lenDelivering > 0 ? '<circle cx="70" cy="70" r="52" fill="none" stroke="#DC2626" stroke-width="18" stroke-dasharray="' + lenDelivering.toFixed(1) + ' ' + circumference.toFixed(1) + '" stroke-dashoffset="' + (-(lenDelivered + lenTransit)).toFixed(1) + '" transform="rotate(-90 70 70)"></circle>' : '') +
                '<text x="70" y="67" text-anchor="middle" fill="#172033" font-size="18" font-weight="800" font-family="Inter, sans-serif">' + centerPct + '</text>' +
                '<text x="70" y="84" text-anchor="middle" fill="#64748B" font-size="10" font-family="Inter, sans-serif">Hoàn tất</text>' +
            '</svg>' +
            '<div class="w-100 d-flex flex-column" style="gap:10px;">' +
                '<div class="d-flex align-items-center justify-content-between gap-2">' +
                    '<span class="d-flex align-items-center gap-2" style="font-size:13px;color:var(--le-ink-body);"><span class="le-donut-legend-dot" style="background:var(--le-success);"></span>Delivered</span>' +
                    '<span class="le-tabular" style="font-size:12px;font-weight:600;color:var(--le-on-surface);">' + formatPercent(pctDelivered) + '</span>' +
                '</div>' +
                '<div class="d-flex align-items-center justify-content-between gap-2">' +
                    '<span class="d-flex align-items-center gap-2" style="font-size:13px;color:var(--le-ink-body);"><span class="le-donut-legend-dot" style="background:var(--le-wine);"></span>In Transit</span>' +
                    '<span class="le-tabular" style="font-size:12px;font-weight:600;color:var(--le-on-surface);">' + formatPercent(pctTransit) + '</span>' +
                '</div>' +
                '<div class="d-flex align-items-center justify-content-between gap-2">' +
                    '<span class="d-flex align-items-center gap-2" style="font-size:13px;color:var(--le-ink-body);"><span class="le-donut-legend-dot" style="background:var(--le-primary);"></span>Delivering</span>' +
                    '<span class="le-tabular" style="font-size:12px;font-weight:600;color:var(--le-on-surface);">' + formatPercent(pctDelivering) + '</span>' +
                '</div>' +
                '<div class="d-flex align-items-center justify-content-between gap-2">' +
                    '<span class="d-flex align-items-center gap-2" style="font-size:13px;color:var(--le-ink-body);"><span class="le-donut-legend-dot" style="background:var(--le-outline-strong);"></span>Pending &amp; Failed</span>' +
                    '<span class="le-tabular" style="font-size:12px;font-weight:600;color:var(--le-on-surface);">' + formatPercent(pctOther) + '</span>' +
                '</div>' +
            '</div>';
    }

    function renderRevenueAnalyticsChart(allShipments) {
        const container = document.getElementById('chart-revenue-analytics');
        if (!container) return;

        const now = new Date();
        const months = [];
        for (let i = 5; i >= 0; i--) {
            const dt = new Date(now.getFullYear(), now.getMonth() - i, 1);
            months.push({
                label: 'T' + (dt.getMonth() + 1),
                year: dt.getFullYear(),
                month: dt.getMonth(),
                revenue: 0
            });
        }

        allShipments.forEach(function (s) {
            if (!s.createdAt) return;
            const d = new Date(s.createdAt);
            const bucket = months.find(function (m) {
                return m.year === d.getFullYear() && m.month === d.getMonth();
            });
            if (bucket) {
                bucket.revenue += Number(s.shippingFee || 0);
            }
        });

        const currRev = months[5].revenue;
        const prevRev = months[4].revenue;
        let momText = 'Tháng này';
        if (prevRev > 0) {
            const pct = ((currRev - prevRev) / prevRev) * 100;
            momText = (pct >= 0 ? '+' : '') + pct.toFixed(1).replace('.', ',') + '% MoM';
        } else if (currRev > 0) {
            momText = formatCompactVnd(currRev);
        }
        setText('chart-revenue-mom', momText);

        const maxRev = Math.max.apply(null, months.map(function (m) { return m.revenue; }).concat([1]));
        const xs = [35, 115, 195, 275, 355, 445];
        const baseY = 120;
        const topY = 26;

        const pts = months.map(function (m, idx) {
            const ratio = m.revenue / maxRev;
            const y = Math.round(baseY - ratio * (baseY - topY));
            return { x: xs[idx], y: m.revenue > 0 ? y : baseY - 2, label: m.label, revenue: m.revenue, isLast: idx === 5 };
        });

        const linePoints = pts.map(function (p) { return p.x + ',' + p.y; }).join(' ');
        const polyPoints = xs[0] + ',' + baseY + ' ' + linePoints + ' ' + xs[xs.length - 1] + ',' + baseY;
        const lastPt = pts[pts.length - 1];

        const labelsSvg = pts.map(function (p) {
            return '<text x="' + p.x + '" y="142" text-anchor="middle" fill="' + (p.isLast ? '#172033' : '#64748B') + '" font-weight="' + (p.isLast ? '700' : '400') + '" font-size="11" font-family="Inter, sans-serif">' + p.label + '</text>';
        }).join('');

        const hoverDots = pts.map(function (p) {
            return '<circle cx="' + p.x + '" cy="' + p.y + '" r="' + (p.isLast ? '5' : '3.5') + '" fill="' + (p.isLast ? '#DC2626' : '#FFFFFF') + '" stroke="#DC2626" stroke-width="2">' +
                '<title>' + p.label + ': ' + formatCompactVnd(p.revenue) + '</title>' +
            '</circle>';
        }).join('');

        container.innerHTML =
            '<svg viewBox="0 0 480 155" class="le-svg-chart" style="height:160px;" role="img" aria-label="Biểu đồ đường doanh thu theo tháng">' +
                '<line x1="20" y1="30" x2="465" y2="30" stroke="#E2E8F0" stroke-dasharray="3 3"></line>' +
                '<line x1="20" y1="75" x2="465" y2="75" stroke="#E2E8F0" stroke-dasharray="3 3"></line>' +
                '<line x1="20" y1="120" x2="465" y2="120" stroke="#CBD5E1"></line>' +
                '<polygon points="' + polyPoints + '" fill="#FEF2F2"></polygon>' +
                '<polyline points="' + linePoints + '" fill="none" stroke="#DC2626" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"></polyline>' +
                hoverDots +
                '<circle cx="' + lastPt.x + '" cy="' + lastPt.y + '" r="5" fill="#DC2626"></circle>' +
                labelsSvg +
            '</svg>';
    }

    function renderDeliveryPerformance(shipments, hubs) {
        const container = document.getElementById('chart-delivery-performance');
        if (!container) return;

        const regions = {
            south: { name: 'Miền Nam', hubs: 0, delivered: 0, finished: 0 },
            north: { name: 'Miền Bắc', hubs: 0, delivered: 0, finished: 0 },
            central: { name: 'Miền Trung', hubs: 0, delivered: 0, finished: 0 }
        };

        hubs.forEach(function (h) {
            const key = classifyRegion((h.name || '') + ' ' + (h.code || ''));
            regions[key].hubs++;
        });

        shipments.forEach(function (s) {
            const st = s.currentStatus || s.status;
            const isDelivered = st === 'DELIVERED';
            const isFailed = st === 'DELIVERY_FAILED' || st === 'REFUSED' || st === 'ON_HOLD_FOR_RETURN'
                || st === 'RETURN_INITIATED' || st === 'IN_RETURN_TRANSIT' || st === 'RETURNED';
            if (!isDelivered && !isFailed) return;

            const locText = (s.deliveryAddress && s.deliveryAddress.provinceName ? s.deliveryAddress.provinceName : '')
                + ' ' + (s.destHubName || s.originHubName || '');
            const key = classifyRegion(locText);
            regions[key].finished++;
            if (isDelivered) regions[key].delivered++;
        });

        const ordered = [regions.south, regions.north, regions.central];
        container.innerHTML = ordered.map(function (r) {
            const rate = r.finished > 0 ? (r.delivered / r.finished) * 100 : 0;
            const barWidth = Math.max(0, Math.min(100, Math.round(rate)));
            const rateLabel = r.finished > 0 ? formatPercent(rate) + ' Thành công' : 'Chưa có dữ liệu';
            return '<div>' +
                '<div class="d-flex justify-content-between align-items-center mb-1">' +
                    '<span style="font-size:12px;font-weight:600;color:var(--le-ink-label);">' + r.name + ' (' + r.hubs + ' Hub)</span>' +
                    '<span class="le-tabular" style="font-size:12px;font-weight:500;color:var(--le-on-success-container);">' + rateLabel + '</span>' +
                '</div>' +
                '<div class="le-progress">' +
                    '<div class="le-progress-bar" style="width:' + barWidth + '%;"></div>' +
                '</div>' +
            '</div>';
        }).join('');
    }

    function renderHubBacklogList(hubs, shipments) {
        const container = document.getElementById('ops-hub-list');
        if (!container) return;

        if (!Array.isArray(hubs) || hubs.length === 0) {
            container.innerHTML = '<div class="text-muted small py-3 text-center">Chưa có dữ liệu Hub nào.</div>';
            return;
        }

        const hubStats = {};
        hubs.forEach(function (h) {
            hubStats[h.id] = { hub: h, backlog: 0, total: 0 };
        });

        shipments.forEach(function (s) {
            const hid = s.currentHubId || s.originHubId;
            if (!hid || !hubStats[hid]) return;
            hubStats[hid].total++;
            const st = s.currentStatus || s.status;
            if (st !== 'DELIVERED' && st !== 'RETURNED' && st !== 'CANCELLED') {
                hubStats[hid].backlog++;
            }
        });

        const ranked = Object.values(hubStats).sort(function (a, b) {
            return b.backlog - a.backlog || b.total - a.total;
        }).slice(0, 2);

        const esc = global.opsCommon.escapeHtml;
        container.innerHTML = ranked.map(function (item) {
            const processed = Math.max(0, item.total - item.backlog);
            const eff = item.total > 0 ? Math.round((processed / item.total) * 100) : 100;
            const badgeText = item.backlog > 0
                ? global.opsCommon.formatNumber(item.backlog) + ' tồn'
                : (item.hub.level === 'REGIONAL_HUB' ? 'Hub vùng' : 'Ổn định');
            return '<a href="/ops/hubs" class="le-ops-list-item le-ops-list-link">' +
                '<div class="min-w-0">' +
                    '<div class="text-truncate" style="font-size:12px;font-weight:600;color:var(--le-on-surface);">' + esc(item.hub.name) + '</div>' +
                    '<div style="font-size:11px;color:var(--le-muted);">Xử lý đạt ' + eff + '% · ' + esc(item.hub.code) + '</div>' +
                '</div>' +
                '<span class="le-pill le-pill-rose le-tabular">' + esc(badgeText) + '</span>' +
            '</a>';
        }).join('');
    }

    function describeActivity(shipment) {
        const st = shipment.currentStatus || shipment.status;
        const hubOrReceiver = shipment.currentHubName || shipment.originHubName || shipment.receiverName || 'Hệ thống';
        const province = shipment.deliveryAddress && shipment.deliveryAddress.provinceName
            ? shipment.deliveryAddress.provinceName
            : (shipment.receiverName || '');

        switch (st) {
            case 'CREATED':
                return { action: 'Tạo vận đơn', detail: province || 'Đơn mới', actor: shipment.senderName || 'Shop', pill: 'le-pill-neutral' };
            case 'PICKUP_ASSIGNED':
            case 'PICKED_UP':
                return { action: 'Tiếp nhận hàng', detail: hubOrReceiver, actor: 'Shipper', pill: 'le-pill-neutral' };
            case 'AT_ORIGIN_HUB':
            case 'IN_LINEHAUL':
            case 'AT_DEST_HUB':
                return { action: 'Chuyển đến Hub', detail: hubOrReceiver, actor: 'Hub Staff', pill: 'le-pill-rose' };
            case 'OUT_FOR_DELIVERY':
                return { action: 'Đang giao hàng', detail: province || hubOrReceiver, actor: 'Shipper', pill: 'le-pill-rose' };
            case 'DELIVERED':
                return { action: 'Cập nhật trạng thái', detail: 'Giao hàng thành công', actor: 'System', pill: 'le-pill-success' };
            case 'DELIVERY_FAILED':
            case 'REFUSED':
                return { action: 'Giao thất bại', detail: province || hubOrReceiver, actor: 'Shipper', pill: 'le-pill-rose' };
            default:
                return { action: 'Cập nhật vận đơn', detail: province || hubOrReceiver, actor: 'System', pill: 'le-pill-neutral' };
        }
    }

    function renderRecentActivities(items) {
        const tbody = document.getElementById('recent-shipments-body');
        if (!tbody) return;

        if (!Array.isArray(items) || items.length === 0) {
            tbody.innerHTML = '<tr><td colspan="4" class="text-center py-4 text-muted">Chưa có hoạt động vận đơn nào trong khoảng thời gian này.</td></tr>';
            return;
        }

        const c = global.opsCommon;
        tbody.innerHTML = items.slice(0, 6).map(function (s) {
            const code = c.escapeHtml(s.trackingCode || '—');
            const act = describeActivity(s);
            const timeStr = formatShortTime(s.deliveredAt || s.createdAt);
            return '<tr>' +
                '<td class="le-tabular" style="font-size:12px;font-weight:600;color:var(--le-on-surface);">' + c.escapeHtml(timeStr) + '</td>' +
                '<td style="font-size:13px;color:var(--le-on-surface);">' + c.escapeHtml(act.action) + '</td>' +
                '<td class="le-tabular" style="font-size:13px;color:var(--le-ink-body);">' +
                    '<a href="/ops/shipments/' + encodeURIComponent(s.trackingCode || '') + '" class="fw-semibold text-decoration-none" style="color:var(--le-on-surface);">' + code + '</a>' +
                    ' &middot; ' + c.escapeHtml(act.detail) +
                '</td>' +
                '<td><span class="le-pill ' + act.pill + '">' + c.escapeHtml(act.actor) + '</span></td>' +
            '</tr>';
        }).join('');
    }

    function renderDashboardView() {
        const c = global.opsCommon;
        const shipments = filterByRange(state.allShipments, state.activeRange, state.userPickedRange);
        const hubs = state.hubs;
        const staff = state.staff;

        const counts = { created: 0, pickup: 0, transit: 0, delivering: 0, delivered: 0, failed: 0, returning: 0, overdue: 0 };
        let totalRevenue = 0;
        let pendingCod = 0;
        const activeShopIds = new Set();
        let deliveryDurationHoursSum = 0;
        let deliveryDurationCount = 0;
        const overdueThresholdMs = 48 * 60 * 60 * 1000;
        const nowMs = Date.now();

        shipments.forEach(function (s) {
            const st = s.currentStatus || s.status;
            if (s.shopId) activeShopIds.add(s.shopId);

            if (st === 'CREATED') counts.created++;
            else if (st === 'PICKUP_ASSIGNED' || st === 'PICKED_UP') counts.pickup++;
            else if (st === 'AT_ORIGIN_HUB' || st === 'IN_LINEHAUL' || st === 'AT_DEST_HUB') counts.transit++;
            else if (st === 'OUT_FOR_DELIVERY') counts.delivering++;
            else if (st === 'DELIVERED') counts.delivered++;
            else if (st === 'DELIVERY_FAILED' || st === 'REFUSED') counts.failed++;
            else if (st === 'ON_HOLD_FOR_RETURN' || st === 'RETURN_INITIATED' || st === 'IN_RETURN_TRANSIT' || st === 'RETURNED') {
                counts.failed++;
                counts.returning++;
            }

            if (st !== 'DELIVERED' && st !== 'RETURNED' && st !== 'CANCELLED' && s.createdAt) {
                const ageMs = nowMs - new Date(s.createdAt).getTime();
                if (ageMs > overdueThresholdMs) counts.overdue++;
            }

            if (st === 'DELIVERED' && s.createdAt && s.deliveredAt) {
                const diffH = (new Date(s.deliveredAt).getTime() - new Date(s.createdAt).getTime()) / (1000 * 60 * 60);
                if (diffH >= 0 && diffH < 720) {
                    deliveryDurationHoursSum += diffH;
                    deliveryDurationCount++;
                }
            }

            totalRevenue += Number(s.shippingFee || 0);
            if (st !== 'CANCELLED' && st !== 'RETURNED') {
                pendingCod += Number(s.codAmount || 0);
            }
        });

        const shipperCount = staff.filter(function (u) {
            return Array.isArray(u.roles) && u.roles.includes('ROLE_SHIPPER');
        }).length;

        const finished = counts.delivered + counts.failed;
        const successRateNum = finished > 0 ? (counts.delivered / finished) * 100 : 0;
        const failRateNum = finished > 0 ? (counts.failed / finished) * 100 : 0;
        const displayTotal = (!state.userPickedRange && state.totalElements > shipments.length)
            ? state.totalElements
            : shipments.length;

        const avgDeliveryText = deliveryDurationCount > 0
            ? (deliveryDurationHoursSum / deliveryDurationCount).toFixed(1).replace('.', ',') + ' giờ'
            : '—';

        setText('kpi-total-shipments', c.formatNumber(displayTotal));
        setText('kpi-total-badge', state.userPickedRange ? (state.activeRange === 'today' ? 'Hôm nay' : state.activeRange === '7d' ? '7 ngày' : '30 ngày') : 'Live');
        setText('kpi-pending-shipments', c.formatNumber(counts.created + counts.pickup));
        setText('kpi-transit-shipments', c.formatNumber(counts.transit));
        setText('kpi-delivering-shipments', c.formatNumber(counts.delivering));
        setText('kpi-delivered-shipments', c.formatNumber(counts.delivered));
        setText('kpi-failed-shipments', c.formatNumber(counts.failed));
        setText('kpi-total-revenue', formatCompactVnd(totalRevenue));
        setText('kpi-total-cod', formatCompactVnd(pendingCod));
        setText('kpi-success-rate', formatPercent(successRateNum));
        setText('kpi-fail-rate', formatPercent(failRateNum));
        setText('kpi-hub-badge', hubs.length + ' Hub');
        setText('kpi-shipper-badge', shipperCount + ' Shipper');
        setText('kpi-shop-badge', activeShopIds.size + ' Shop');

        setText('strip-success-rate', formatPercent(successRateNum));
        setText('strip-avg-delivery', avgDeliveryText);
        setText('strip-active-shops', c.formatNumber(activeShopIds.size));
        setText('strip-hub-count', hubs.length + ' / ' + hubs.length);

        setText('alert-overdue-count', c.formatNumber(counts.overdue));
        setText('alert-failed-count', c.formatNumber(counts.failed));
        setText('alert-return-count', c.formatNumber(counts.returning));
        setText('alert-created-count', c.formatNumber(counts.created));

        renderShipmentOverviewChart(shipments);
        renderShipmentStatusDonut(counts, successRateNum);
        renderRevenueAnalyticsChart(state.allShipments);
        renderDeliveryPerformance(shipments, hubs);
        renderHubBacklogList(hubs, shipments);
        renderRecentActivities(shipments);
    }

    function exportDashboardCsv() {
        const shipments = filterByRange(state.allShipments, state.activeRange, state.userPickedRange);
        const headers = ['Ma van don', 'Trang thai', 'Nguoi gui', 'Nguoi nhan', 'Cuoc phi (VND)', 'COD (VND)', 'Thoi gian tao'];
        const rows = shipments.map(function (s) {
            return [
                s.trackingCode || '',
                s.currentStatus || s.status || '',
                s.senderName || '',
                s.receiverName || '',
                s.shippingFee || 0,
                s.codAmount || 0,
                s.createdAt || ''
            ].map(function (cell) {
                return '"' + String(cell).replace(/"/g, '""') + '"';
            }).join(',');
        });
        const csv = '\uFEFF' + [headers.join(',')].concat(rows).join('\n');
        const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = 'ute-express-dashboard-' + state.activeRange + '.csv';
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        URL.revokeObjectURL(url);
    }

    async function loadDashboard() {
        const [shipmentsPage, hubs, staff] = await Promise.all([
            safeJson('/api/ops/shipments?size=200'),
            safeJson('/api/ops/hubs'),
            safeJson('/api/ops/users')
        ]);

        state.allShipments = (shipmentsPage && Array.isArray(shipmentsPage.content)) ? shipmentsPage.content : [];
        state.totalElements = (shipmentsPage && typeof shipmentsPage.totalElements === 'number')
            ? shipmentsPage.totalElements
            : state.allShipments.length;
        state.hubs = Array.isArray(hubs) ? hubs : [];
        state.staff = Array.isArray(staff) ? staff : [];

        renderDashboardView();
    }

    document.addEventListener('DOMContentLoaded', function () {
        document.querySelectorAll('.le-segment-btn').forEach(function (btn) {
            btn.addEventListener('click', function () {
                document.querySelectorAll('.le-segment-btn').forEach(function (b) { b.classList.remove('is-active'); });
                btn.classList.add('is-active');
                state.activeRange = btn.getAttribute('data-range') || 'today';
                state.userPickedRange = true;
                renderDashboardView();
            });
        });

        document.querySelectorAll('[data-chart-mode]').forEach(function (btn) {
            btn.addEventListener('click', function () {
                document.querySelectorAll('[data-chart-mode]').forEach(function (b) { b.classList.remove('is-active'); });
                btn.classList.add('is-active');
                state.chartMode = btn.getAttribute('data-chart-mode') || 'day';
                const shipments = filterByRange(state.allShipments, state.activeRange, state.userPickedRange);
                renderShipmentOverviewChart(shipments);
            });
        });

        const exportBtn = document.getElementById('btn-export-report');
        if (exportBtn) {
            exportBtn.addEventListener('click', exportDashboardCsv);
        }

        loadDashboard();
    });
})(window);
