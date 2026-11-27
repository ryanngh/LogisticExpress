(function (global) {
    'use strict';

    const LEVEL_META = {
        REGIONAL_HUB: { label: 'Hub Vùng', short: 'REGIONAL', cls: 'le-badge-info' },
        LOCAL_POST_OFFICE: { label: 'Bưu cục địa phương', short: 'LOCAL', cls: 'le-badge-teal' }
    };

    let allHubs = [];
    let hubById = new Map();
    let allProvinces = [];
    let wardsCacheByProvince = new Map();
    let isAdmin = false;

    let selectedDrawerHubId = null;
    let currentCoverageWards = [];
    let awSelectedWardCodes = new Set();
    let awCurrentProvinceWards = [];

    let hubFormModalInstance = null;
    let assignWardsModalInstance = null;
    let deleteHubModalInstance = null;
    let detailDrawerInstance = null;

    function refreshIcons() {
        if (global.lucide && typeof global.lucide.createIcons === 'function') {
            global.lucide.createIcons();
        }
    }

    function showPageAlert(type, message) {
        const el = document.getElementById('hubs-alert');
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

    function renderLevelBadge(level) {
        const meta = LEVEL_META[level] || { label: level || '—', cls: 'le-badge-neutral' };
        return '<span class="le-badge ' + meta.cls + '">' + global.opsCommon.escapeHtml(meta.label) + '</span>';
    }

    function formatCoord(lat, lng) {
        if (lat == null || lng == null) return '—';
        return Number(lat).toFixed(4) + ', ' + Number(lng).toFixed(4);
    }

    function updateStats() {
        const c = global.opsCommon;
        const total = allHubs.length;
        let regional = 0;
        let local = 0;
        let totalWards = 0;
        let hubsWithoutWards = 0;

        allHubs.forEach(function (h) {
            if (h.level === 'REGIONAL_HUB') regional++;
            else if (h.level === 'LOCAL_POST_OFFICE') local++;
            const wc = Array.isArray(h.assignedWardCodes) ? h.assignedWardCodes.length : 0;
            totalWards += wc;
            if (wc === 0) hubsWithoutWards++;
        });

        const setText = function (id, txt) {
            const el = document.getElementById(id);
            if (el) el.textContent = txt;
        };

        setText('stat-total-hubs', c.formatNumber(total));
        setText('stat-hub-breakdown', regional + ' Hub Vùng · ' + local + ' Bưu cục');
        setText('stat-regional-hubs', c.formatNumber(regional));
        setText('stat-local-hubs', c.formatNumber(local));
        setText('stat-assigned-wards', c.formatNumber(totalWards));
        setText('tab-hub-count', c.formatNumber(total));

        const hintEl = document.getElementById('stat-unassigned-hubs-hint');
        const badgeEl = document.getElementById('stat-coverage-badge');
        if (hintEl && badgeEl) {
            if (hubsWithoutWards > 0) {
                hintEl.textContent = hubsWithoutWards + ' Hub chưa gán Phường/Xã nào';
                badgeEl.className = 'le-badge le-badge-warning';
                badgeEl.textContent = 'Cần bổ sung';
            } else if (total > 0) {
                hintEl.textContent = 'Tất cả Hub đều đã có vùng phụ trách';
                badgeEl.className = 'le-badge le-badge-success';
                badgeEl.textContent = 'Đầy đủ';
            } else {
                hintEl.textContent = 'Chưa có điểm Hub nào trong hệ thống';
                badgeEl.className = 'le-badge le-badge-neutral';
                badgeEl.textContent = 'Trống';
            }
        }
    }

    function populateParentFilterSelect() {
        const esc = global.opsCommon.escapeHtml;
        const filterParent = document.getElementById('filter-hub-parent');
        if (!filterParent) return;
        const current = filterParent.value;
        const regionals = allHubs.filter(function (h) { return h.level === 'REGIONAL_HUB'; });
        filterParent.innerHTML = '<option value="">Tất cả Hub cấp trên</option>' +
            '<option value="__ROOT__">Chỉ Hub gốc (không có cấp trên)</option>' +
            regionals.map(function (h) {
                return '<option value="' + esc(h.id) + '">' + esc(h.name) + ' (' + esc(h.code) + ')</option>';
            }).join('');
        filterParent.value = current;
    }

    function populateFormParentSelect(editingHubId) {
        const esc = global.opsCommon.escapeHtml;
        const select = document.getElementById('hf-parent-id');
        if (!select) return;
        const level = document.getElementById('hf-level')?.value || 'REGIONAL_HUB';
        const currentVal = select.value;

        const candidates = allHubs.filter(function (h) {
            if (editingHubId && h.id === editingHubId) return false;
            if (level === 'LOCAL_POST_OFFICE') return h.level === 'REGIONAL_HUB';
            return h.level === 'REGIONAL_HUB';
        });

        select.innerHTML = '<option value="">-- Không có Hub cấp trên --</option>' +
            candidates.map(function (h) {
                return '<option value="' + esc(h.id) + '">' + esc(h.name) + ' (' + esc(h.code) + ')</option>';
            }).join('');

        if (currentVal && candidates.some(function (c) { return c.id === currentVal; })) {
            select.value = currentVal;
        }
    }

    function getFilteredHubs() {
        const q = (document.getElementById('filter-hub-search')?.value || '').trim().toLowerCase();
        const level = document.getElementById('filter-hub-level')?.value || '';
        const parentVal = document.getElementById('filter-hub-parent')?.value || '';

        return allHubs.filter(function (h) {
            if (level && h.level !== level) return false;
            if (parentVal === '__ROOT__' && h.parentHubId) return false;
            if (parentVal && parentVal !== '__ROOT__' && h.parentHubId !== parentVal) return false;
            if (q) {
                const parent = h.parentHubId ? hubById.get(h.parentHubId) : null;
                const haystack = [
                    h.code,
                    h.name,
                    parent ? parent.name : '',
                    parent ? parent.code : '',
                    Array.isArray(h.assignedWardCodes) ? h.assignedWardCodes.join(' ') : ''
                ].join(' ').toLowerCase();
                if (!haystack.includes(q)) return false;
            }
            return true;
        });
    }

    function renderDirectoryTable() {
        const tbody = document.getElementById('hubs-table-body');
        const countLabel = document.getElementById('hubs-count-label');
        if (!tbody) return;

        const esc = global.opsCommon.escapeHtml;
        const rows = getFilteredHubs();

        if (countLabel) {
            countLabel.textContent = 'Hiển thị ' + rows.length + ' / ' + allHubs.length + ' điểm Hub';
        }

        if (allHubs.length === 0) {
            tbody.innerHTML =
                '<tr><td colspan="6" class="text-center py-5">' +
                '<div class="fw-semibold mb-1">Chưa có điểm Hub nào trong hệ thống</div>' +
                '<div class="small text-muted mb-3">Khởi tạo Hub Vùng đầu tiên để thiết lập mạng lưới giao nhận và phân vùng Phường/Xã.</div>' +
                (isAdmin ? '<button type="button" class="le-btn le-btn-primary" data-action="empty-create-hub">+ Tạo Hub đầu tiên</button>' : '') +
                '</td></tr>';
            return;
        }

        if (rows.length === 0) {
            tbody.innerHTML =
                '<tr><td colspan="6" class="text-center py-5 text-muted">' +
                'Không tìm thấy Hub nào khớp với bộ lọc hiện tại.' +
                '</td></tr>';
            return;
        }

        tbody.innerHTML = rows.map(function (h) {
            const parent = h.parentHubId ? hubById.get(h.parentHubId) : null;
            const wardCodes = Array.isArray(h.assignedWardCodes) ? h.assignedWardCodes : [];
            const selectedCls = selectedDrawerHubId === h.id ? ' le-row-selected' : '';

            let wardsHtml = '<span class="le-badge le-badge-warning">Chưa gán</span>';
            if (wardCodes.length > 0) {
                const preview = wardCodes.slice(0, 3).map(function (c) {
                    return '<span class="badge text-bg-light border font-monospace" style="font-size:11px;">' + esc(c) + '</span>';
                }).join(' ');
                const extra = wardCodes.length > 3
                    ? ' <span class="small text-muted fw-semibold">+' + (wardCodes.length - 3) + '</span>'
                    : '';
                wardsHtml = '<div class="d-flex flex-wrap align-items-center gap-1">' +
                    '<span class="le-badge le-badge-success le-tabular">' + wardCodes.length + ' phường</span>' +
                    preview + extra +
                    '</div>';
            }

            const parentHtml = parent
                ? '<div class="fw-semibold small">' + esc(parent.name) + '</div>' +
                  '<div class="small text-muted font-monospace">' + esc(parent.code) + '</div>'
                : '<span class="text-muted small">Cấp gốc</span>';

            const adminActions = isAdmin
                ? '<button type="button" class="le-btn-icon" data-action="wards" data-id="' + esc(h.id) + '" title="Phân vùng Phường/Xã" aria-label="Phân vùng Phường/Xã">' +
                      '<i data-lucide="map-pinned" style="width:15px;height:15px;" aria-hidden="true"></i>' +
                  '</button>' +
                  '<button type="button" class="le-btn-icon" data-action="edit" data-id="' + esc(h.id) + '" title="Chỉnh sửa Hub" aria-label="Chỉnh sửa Hub">' +
                      '<i data-lucide="pencil" style="width:15px;height:15px;" aria-hidden="true"></i>' +
                  '</button>' +
                  '<button type="button" class="le-btn-icon text-danger" data-action="delete" data-id="' + esc(h.id) + '" title="Xóa Hub" aria-label="Xóa Hub">' +
                      '<i data-lucide="trash-2" style="width:15px;height:15px;" aria-hidden="true"></i>' +
                  '</button>'
                : '';

            return '<tr class="le-row-clickable' + selectedCls + '" data-row-hub-id="' + esc(h.id) + '">' +
                '<td>' +
                    '<div class="fw-bold font-monospace" style="color:var(--le-primary-hover);">' + esc(h.code) + '</div>' +
                    '<div class="fw-semibold">' + esc(h.name) + '</div>' +
                '</td>' +
                '<td>' + renderLevelBadge(h.level) + '</td>' +
                '<td>' + parentHtml + '</td>' +
                '<td class="font-monospace small le-tabular text-muted">' + esc(formatCoord(h.lat, h.lng)) + '</td>' +
                '<td>' + wardsHtml + '</td>' +
                '<td class="text-end">' +
                    '<div class="d-inline-flex align-items-center gap-1">' +
                        '<button type="button" class="le-btn-icon" data-action="detail" data-id="' + esc(h.id) + '" title="Xem chi tiết" aria-label="Xem chi tiết">' +
                            '<i data-lucide="panel-right-open" style="width:15px;height:15px;" aria-hidden="true"></i>' +
                        '</button>' +
                        adminActions +
                    '</div>' +
                '</td>' +
            '</tr>';
        }).join('');

        refreshIcons();
    }

    function renderHierarchyTree() {
        const container = document.getElementById('hub-hierarchy-container');
        if (!container) return;
        const esc = global.opsCommon.escapeHtml;

        if (allHubs.length === 0) {
            container.innerHTML = '<div class="le-card p-5 text-center text-muted">Chưa có điểm Hub nào để hiển thị sơ đồ phân cấp.</div>';
            return;
        }

        const childrenByParent = new Map();
        const roots = [];

        allHubs.forEach(function (h) {
            if (h.parentHubId && hubById.has(h.parentHubId)) {
                if (!childrenByParent.has(h.parentHubId)) childrenByParent.set(h.parentHubId, []);
                childrenByParent.get(h.parentHubId).push(h);
            } else {
                roots.push(h);
            }
        });

        container.innerHTML = roots.map(function (root) {
            const kids = childrenByParent.get(root.id) || [];
            const rootWards = Array.isArray(root.assignedWardCodes) ? root.assignedWardCodes.length : 0;

            const kidsHtml = kids.length > 0
                ? '<div class="le-hub-tree-children">' + kids.map(function (kid) {
                    const kw = Array.isArray(kid.assignedWardCodes) ? kid.assignedWardCodes.length : 0;
                    return '<div class="le-ops-list-item">' +
                        '<div class="d-flex flex-wrap align-items-center gap-2">' +
                            renderLevelBadge(kid.level) +
                            '<span class="font-monospace fw-bold small" style="color:var(--le-primary-hover);">' + esc(kid.code) + '</span>' +
                            '<span class="fw-semibold small">' + esc(kid.name) + '</span>' +
                            '<span class="small text-muted font-monospace">(' + esc(formatCoord(kid.lat, kid.lng)) + ')</span>' +
                        '</div>' +
                        '<div class="d-flex align-items-center gap-2">' +
                            '<span class="le-badge ' + (kw > 0 ? 'le-badge-success' : 'le-badge-warning') + ' le-tabular">' + kw + ' phường</span>' +
                            '<button type="button" class="btn btn-outline-secondary btn-sm py-0 px-2" data-action="detail" data-id="' + esc(kid.id) + '">Chi tiết</button>' +
                        '</div>' +
                    '</div>';
                }).join('') + '</div>'
                : '<div class="small text-muted mt-2 ps-2">Chưa có bưu cục địa phương trực thuộc.</div>';

            return '<div class="le-hub-tree-node">' +
                '<div class="d-flex flex-wrap justify-content-between align-items-center gap-2">' +
                    '<div class="d-flex flex-wrap align-items-center gap-2">' +
                        renderLevelBadge(root.level) +
                        '<span class="font-monospace fw-bold" style="color:var(--le-primary-hover);">' + esc(root.code) + '</span>' +
                        '<span class="fw-bold">' + esc(root.name) + '</span>' +
                        '<span class="small text-muted font-monospace">' + esc(formatCoord(root.lat, root.lng)) + '</span>' +
                    '</div>' +
                    '<div class="d-flex align-items-center gap-2">' +
                        '<span class="le-badge le-badge-neutral le-tabular">' + kids.length + ' bưu cục con</span>' +
                        '<span class="le-badge ' + (rootWards > 0 ? 'le-badge-success' : 'le-badge-neutral') + ' le-tabular">' + rootWards + ' phường</span>' +
                        '<button type="button" class="btn btn-outline-secondary btn-sm py-0 px-2" data-action="detail" data-id="' + esc(root.id) + '">Chi tiết</button>' +
                    '</div>' +
                '</div>' +
                kidsHtml +
            '</div>';
        }).join('');
    }

    function openDetailDrawer(hubId) {
        const hub = hubById.get(hubId);
        if (!hub) return;
        selectedDrawerHubId = hubId;
        renderDirectoryTable();

        const esc = global.opsCommon.escapeHtml;
        const meta = LEVEL_META[hub.level] || { label: hub.level, cls: 'le-badge-neutral' };
        const levelEl = document.getElementById('drawer-hub-level');
        if (levelEl) {
            levelEl.className = 'le-badge mb-1 ' + meta.cls;
            levelEl.textContent = meta.label;
        }
        document.getElementById('hubDetailDrawerTitle').textContent = hub.name || '—';
        document.getElementById('drawer-hub-code').textContent = hub.code || '—';

        const parent = hub.parentHubId ? hubById.get(hub.parentHubId) : null;
        document.getElementById('drawer-hub-parent').textContent = parent ? (parent.name + ' (' + parent.code + ')') : 'Không có (Cấp gốc)';

        const wards = Array.isArray(hub.assignedWardCodes) ? hub.assignedWardCodes : [];
        document.getElementById('drawer-hub-ward-count').textContent = global.opsCommon.formatNumber(wards.length);
        document.getElementById('drawer-hub-coords').textContent = formatCoord(hub.lat, hub.lng);

        const mapLink = document.getElementById('drawer-hub-map-link');
        if (mapLink && hub.lat != null && hub.lng != null) {
            mapLink.href = 'https://www.google.com/maps?q=' + encodeURIComponent(hub.lat + ',' + hub.lng);
            mapLink.classList.remove('d-none');
        } else if (mapLink) {
            mapLink.classList.add('d-none');
        }

        document.getElementById('drawer-hub-timestamps').textContent =
            'Tạo: ' + global.opsCommon.formatDateTime(hub.createdAt) + ' · Cập nhật: ' + global.opsCommon.formatDateTime(hub.updatedAt);

        const children = allHubs.filter(function (h) { return h.parentHubId === hub.id; });
        document.getElementById('drawer-children-count').textContent = String(children.length);
        const childListEl = document.getElementById('drawer-children-list');
        if (children.length === 0) {
            childListEl.innerHTML = '<div class="small text-muted">Không có bưu cục con trực thuộc.</div>';
        } else {
            childListEl.innerHTML = children.map(function (c) {
                return '<div class="le-ops-list-item">' +
                    '<div><div class="fw-semibold small">' + esc(c.name) + '</div>' +
                    '<div class="small text-muted font-monospace">' + esc(c.code) + '</div></div>' +
                    '<button type="button" class="btn btn-outline-secondary btn-sm py-0 px-2" data-action="detail" data-id="' + esc(c.id) + '">Xem</button>' +
                '</div>';
            }).join('');
        }

        const wardsListEl = document.getElementById('drawer-wards-list');
        if (wards.length === 0) {
            wardsListEl.innerHTML = '<span class="small text-muted">Chưa gán Phường/Xã nào cho Hub này.</span>';
        } else {
            wardsListEl.innerHTML = wards.map(function (code) {
                return '<span class="badge text-bg-light border font-monospace">' + esc(code) + '</span>';
            }).join('');
        }

        if (detailDrawerInstance) {
            detailDrawerInstance.show();
        }
    }

    function openCreateHubModal() {
        setBoxError('hub-form-error', '');
        document.getElementById('hubFormModalTitle').textContent = 'Tạo điểm Hub mới';
        document.getElementById('hf-id').value = '';
        document.getElementById('hf-code').value = '';
        document.getElementById('hf-name').value = '';
        document.getElementById('hf-level').value = 'REGIONAL_HUB';
        populateFormParentSelect(null);
        document.getElementById('hf-parent-id').value = '';
        document.getElementById('hf-lat').value = '';
        document.getElementById('hf-lng').value = '';
        hubFormModalInstance?.show();
    }

    function openEditHubModal(hubId) {
        const hub = hubById.get(hubId);
        if (!hub) return;
        setBoxError('hub-form-error', '');
        document.getElementById('hubFormModalTitle').textContent = 'Cập nhật Hub — ' + hub.code;
        document.getElementById('hf-id').value = hub.id;
        document.getElementById('hf-code').value = hub.code || '';
        document.getElementById('hf-name').value = hub.name || '';
        document.getElementById('hf-level').value = hub.level || 'REGIONAL_HUB';
        populateFormParentSelect(hub.id);
        document.getElementById('hf-parent-id').value = hub.parentHubId || '';
        document.getElementById('hf-lat').value = hub.lat != null ? hub.lat : '';
        document.getElementById('hf-lng').value = hub.lng != null ? hub.lng : '';
        hubFormModalInstance?.show();
    }

    async function handleSaveHub(e) {
        e.preventDefault();
        setBoxError('hub-form-error', '');

        const editingId = document.getElementById('hf-id').value;
        const code = (document.getElementById('hf-code').value || '').trim().toUpperCase();
        const name = (document.getElementById('hf-name').value || '').trim();
        const level = document.getElementById('hf-level').value;
        const parentHubId = document.getElementById('hf-parent-id').value || null;
        const latVal = document.getElementById('hf-lat').value;
        const lngVal = document.getElementById('hf-lng').value;

        if (!code || !name) {
            setBoxError('hub-form-error', 'Vui lòng nhập đầy đủ Mã Hub và Tên Hub.');
            return;
        }
        const lat = Number(latVal);
        const lng = Number(lngVal);
        if (latVal === '' || lngVal === '' || isNaN(lat) || isNaN(lng) || lat < -90 || lat > 90 || lng < -180 || lng > 180) {
            setBoxError('hub-form-error', 'Tọa độ Vĩ độ (-90..90) và Kinh độ (-180..180) không hợp lệ.');
            return;
        }

        const existing = editingId ? hubById.get(editingId) : null;
        const payload = {
            code: code,
            name: name,
            level: level,
            parentHubId: parentHubId,
            addressId: existing ? existing.addressId : null,
            lat: lat,
            lng: lng,
            assignedWardCodes: null
        };

        const btn = document.getElementById('btn-save-hub');
        btn.disabled = true;
        try {
            const url = editingId ? '/api/ops/hubs/' + encodeURIComponent(editingId) : '/api/ops/hubs';
            const method = editingId ? 'PUT' : 'POST';
            const res = await global.apiFetch(url, { method: method, body: payload });
            if (!res.ok) {
                const msg = await extractErrorMessage(res, 'Không thể lưu thông tin Hub.');
                setBoxError('hub-form-error', msg);
                return;
            }
            const saved = await res.json();
            hubFormModalInstance?.hide();
            await loadHubs();
            if (selectedDrawerHubId && hubById.has(selectedDrawerHubId)) {
                openDetailDrawer(selectedDrawerHubId);
            }
            showPageAlert('success', (editingId ? 'Đã cập nhật Hub ' : 'Đã tạo thành công Hub ') + saved.code + ' — ' + saved.name + '.');
        } catch (_) {
            setBoxError('hub-form-error', 'Lỗi kết nối máy chủ. Vui lòng thử lại.');
        } finally {
            btn.disabled = false;
        }
    }

    function openDeleteHubModal(hubId) {
        const hub = hubById.get(hubId);
        if (!hub) return;
        setBoxError('delete-hub-error', '');
        document.getElementById('del-hub-id').value = hub.id;
        document.getElementById('del-hub-name').textContent = hub.name + ' (' + hub.code + ')';
        deleteHubModalInstance?.show();
    }

    async function handleConfirmDeleteHub() {
        const id = document.getElementById('del-hub-id').value;
        if (!id) return;
        setBoxError('delete-hub-error', '');

        const btn = document.getElementById('btn-confirm-delete-hub');
        btn.disabled = true;
        try {
            const res = await global.apiFetch('/api/ops/hubs/' + encodeURIComponent(id), { method: 'DELETE' });
            if (!res.ok) {
                const msg = await extractErrorMessage(res, 'Không thể xóa Hub này.');
                setBoxError('delete-hub-error', msg);
                return;
            }
            deleteHubModalInstance?.hide();
            if (selectedDrawerHubId === id) {
                selectedDrawerHubId = null;
                detailDrawerInstance?.hide();
            }
            wardsCacheByProvince.clear();
            await loadHubs();
            showPageAlert('success', 'Đã xóa điểm Hub và giải phóng các Phường/Xã liên kết.');
        } catch (_) {
            setBoxError('delete-hub-error', 'Lỗi kết nối máy chủ.');
        } finally {
            btn.disabled = false;
        }
    }

    async function fetchProvincesIfNeeded() {
        if (allProvinces.length > 0) return allProvinces;
        const res = await global.apiFetch('/api/public/provinces');
        if (res.ok) {
            const data = await res.json();
            allProvinces = Array.isArray(data) ? data : [];
            populateProvinceSelects();
        }
        return allProvinces;
    }

    function populateProvinceSelects() {
        const esc = global.opsCommon.escapeHtml;
        const opts = '<option value="">-- Chọn Tỉnh / Thành phố --</option>' +
            allProvinces.map(function (p) {
                return '<option value="' + esc(p.code) + '">' + esc(p.name) + ' (' + esc(p.code) + ')</option>';
            }).join('');

        ['cov-province', 'aw-province'].forEach(function (id) {
            const el = document.getElementById(id);
            if (!el) return;
            const cur = el.value;
            el.innerHTML = opts;
            if (cur) el.value = cur;
        });
    }

    async function fetchWardsByProvince(provinceCode, forceReload) {
        if (!provinceCode) return [];
        if (!forceReload && wardsCacheByProvince.has(provinceCode)) {
            return wardsCacheByProvince.get(provinceCode);
        }
        const res = await global.apiFetch('/api/public/provinces/' + encodeURIComponent(provinceCode) + '/wards');
        if (!res.ok) return [];
        const data = await res.json();
        const list = Array.isArray(data) ? data : [];
        wardsCacheByProvince.set(provinceCode, list);
        return list;
    }

    async function loadCoverageForProvince() {
        const provinceCode = document.getElementById('cov-province')?.value || '';
        const tbody = document.getElementById('cov-table-body');
        if (!provinceCode) {
            currentCoverageWards = [];
            document.getElementById('cov-summary').textContent = 'Chọn Tỉnh/Thành phố để tra cứu vùng phục vụ Hub';
            tbody.innerHTML = '<tr><td colspan="5" class="text-center py-5 text-muted">Vui lòng chọn Tỉnh/Thành phố phía trên để xem danh sách Phường/Xã.</td></tr>';
            return;
        }

        tbody.innerHTML = '<tr><td colspan="5" class="text-center py-4 text-muted">Đang tải danh sách Phường/Xã...</td></tr>';
        currentCoverageWards = await fetchWardsByProvince(provinceCode, true);
        renderCoverageTable();
    }

    function renderCoverageTable() {
        const tbody = document.getElementById('cov-table-body');
        const summaryEl = document.getElementById('cov-summary');
        const esc = global.opsCommon.escapeHtml;

        const q = (document.getElementById('cov-search')?.value || '').trim().toLowerCase();
        const filterState = document.getElementById('cov-filter-assigned')?.value || '';

        const total = currentCoverageWards.length;
        const assignedTotal = currentCoverageWards.filter(function (w) { return Boolean(w.assignedHubId); }).length;

        const filtered = currentCoverageWards.filter(function (w) {
            if (filterState === 'ASSIGNED' && !w.assignedHubId) return false;
            if (filterState === 'UNASSIGNED' && w.assignedHubId) return false;
            if (q) {
                const text = (w.code + ' ' + w.name).toLowerCase();
                if (!text.includes(q)) return false;
            }
            return true;
        });

        summaryEl.textContent = 'Đã phủ sóng ' + assignedTotal + '/' + total + ' Phường/Xã (đang hiển thị ' + filtered.length + ')';

        if (filtered.length === 0) {
            tbody.innerHTML = '<tr><td colspan="5" class="text-center py-4 text-muted">Không có Phường/Xã nào khớp bộ lọc.</td></tr>';
            return;
        }

        tbody.innerHTML = filtered.map(function (w) {
            const hub = w.assignedHubId ? hubById.get(w.assignedHubId) : null;
            const hubCell = hub
                ? '<span class="le-badge le-badge-success">' + esc(hub.code) + ' · ' + esc(hub.name) + '</span>'
                : '<span class="le-badge le-badge-danger">Chưa phân vùng</span>';

            const actionBtn = isAdmin && hub
                ? '<button type="button" class="btn btn-outline-secondary btn-sm py-0 px-2" data-action="wards" data-id="' + esc(hub.id) + '">Điều chỉnh</button>'
                : (isAdmin && allHubs.length > 0
                    ? '<button type="button" class="btn btn-outline-danger btn-sm py-0 px-2" data-action="quick-assign-ward" data-ward="' + esc(w.code) + '">Gán Hub</button>'
                    : '<span class="small text-muted">—</span>');

            return '<tr>' +
                '<td class="font-monospace small fw-semibold">' + esc(w.code) + '</td>' +
                '<td class="fw-semibold">' + esc(w.name) + '</td>' +
                '<td class="font-monospace small text-muted le-tabular">' + esc(formatCoord(w.centerLat, w.centerLng)) + '</td>' +
                '<td>' + hubCell + '</td>' +
                '<td class="text-end">' + actionBtn + '</td>' +
            '</tr>';
        }).join('');
    }

    async function openAssignWardsModal(hubId, preselectWardCode) {
        const hub = hubById.get(hubId);
        if (!hub) return;
        setBoxError('aw-form-error', '');
        document.getElementById('aw-hub-id').value = hub.id;
        document.getElementById('aw-hub-label').textContent = hub.name + ' (' + hub.code + ')';
        document.getElementById('aw-search').value = '';

        awSelectedWardCodes = new Set(Array.isArray(hub.assignedWardCodes) ? hub.assignedWardCodes : []);
        if (preselectWardCode) {
            awSelectedWardCodes.add(preselectWardCode);
        }

        renderSelectedWardChips();
        assignWardsModalInstance?.show();

        await fetchProvincesIfNeeded();
        const provSelect = document.getElementById('aw-province');
        const covProv = document.getElementById('cov-province')?.value;
        if (covProv && !provSelect.value) {
            provSelect.value = covProv;
        }
        if (provSelect.value) {
            awCurrentProvinceWards = await fetchWardsByProvince(provSelect.value, false);
            renderWardPickerList();
        } else {
            awCurrentProvinceWards = [];
            document.getElementById('aw-ward-list').innerHTML =
                '<div class="p-4 text-center text-muted small">Chọn Tỉnh/Thành phố để hiển thị danh sách Phường/Xã.</div>';
        }
    }

    function renderWardPickerList() {
        const listEl = document.getElementById('aw-ward-list');
        const esc = global.opsCommon.escapeHtml;
        const hubId = document.getElementById('aw-hub-id').value;
        const q = (document.getElementById('aw-search')?.value || '').trim().toLowerCase();

        const visible = awCurrentProvinceWards.filter(function (w) {
            if (!q) return true;
            return (w.code + ' ' + w.name).toLowerCase().includes(q);
        });

        if (visible.length === 0) {
            listEl.innerHTML = '<div class="p-4 text-center text-muted small">Không có Phường/Xã nào khớp tìm kiếm.</div>';
            return;
        }

        listEl.innerHTML = visible.map(function (w) {
            const checked = awSelectedWardCodes.has(w.code) ? ' checked' : '';
            const otherHub = w.assignedHubId && w.assignedHubId !== hubId ? hubById.get(w.assignedHubId) : null;
            const otherTag = otherHub
                ? '<span class="le-badge le-badge-warning" style="font-size:10px;">Đang thuộc ' + esc(otherHub.code) + '</span>'
                : '';

            return '<label class="le-ward-picker-item">' +
                '<span class="d-flex align-items-center gap-2">' +
                    '<input type="checkbox" class="form-check-input mt-0 aw-ward-cb" value="' + esc(w.code) + '"' + checked + '>' +
                    '<span><span class="fw-semibold">' + esc(w.name) + '</span> <span class="font-monospace small text-muted">(' + esc(w.code) + ')</span></span>' +
                '</span>' +
                otherTag +
            '</label>';
        }).join('');
    }

    function renderSelectedWardChips() {
        const countEl = document.getElementById('aw-selected-count');
        const chipsEl = document.getElementById('aw-selected-chips');
        const esc = global.opsCommon.escapeHtml;
        const codes = Array.from(awSelectedWardCodes).sort();

        countEl.textContent = String(codes.length);
        if (codes.length === 0) {
            chipsEl.innerHTML = '<span class="small text-muted">Chưa chọn Phường/Xã nào.</span>';
            return;
        }
        chipsEl.innerHTML = codes.map(function (c) {
            return '<span class="badge text-bg-light border font-monospace d-inline-flex align-items-center gap-1">' +
                esc(c) +
                '<button type="button" class="btn-close" style="width:8px;height:8px;font-size:8px;" data-remove-ward="' + esc(c) + '" aria-label="Bỏ ' + esc(c) + '"></button>' +
            '</span>';
        }).join('');
    }

    async function handleSaveAssignedWards(e) {
        e.preventDefault();
        setBoxError('aw-form-error', '');
        const hubId = document.getElementById('aw-hub-id').value;
        const hub = hubById.get(hubId);
        if (!hub) return;

        const codes = Array.from(awSelectedWardCodes);
        const btn = document.getElementById('btn-save-wards');
        btn.disabled = true;

        try {
            // Use PUT /api/ops/hubs/{id} so both additions AND removals of wards are synced cleanly
            const payload = {
                code: hub.code,
                name: hub.name,
                level: hub.level,
                parentHubId: hub.parentHubId || null,
                addressId: hub.addressId || null,
                lat: hub.lat,
                lng: hub.lng,
                assignedWardCodes: codes
            };
            const res = await global.apiFetch('/api/ops/hubs/' + encodeURIComponent(hubId), {
                method: 'PUT',
                body: payload
            });
            if (!res.ok) {
                const msg = await extractErrorMessage(res, 'Không thể cập nhật phân vùng Phường/Xã.');
                setBoxError('aw-form-error', msg);
                return;
            }
            assignWardsModalInstance?.hide();
            wardsCacheByProvince.clear();
            await loadHubs();
            if (document.getElementById('cov-province')?.value) {
                await loadCoverageForProvince();
            }
            if (selectedDrawerHubId === hubId) {
                openDetailDrawer(hubId);
            }
            showPageAlert('success', 'Đã cập nhật ' + codes.length + ' Phường/Xã phụ trách cho Hub ' + hub.code + '.');
        } catch (_) {
            setBoxError('aw-form-error', 'Lỗi kết nối máy chủ.');
        } finally {
            btn.disabled = false;
        }
    }

    function exportHubsCsv() {
        const rows = getFilteredHubs();
        const header = ['Code', 'Name', 'Level', 'ParentHubCode', 'Lat', 'Lng', 'AssignedWardCount', 'AssignedWardCodes'];
        const csvLines = [header.join(',')];

        rows.forEach(function (h) {
            const parent = h.parentHubId ? hubById.get(h.parentHubId) : null;
            const wards = Array.isArray(h.assignedWardCodes) ? h.assignedWardCodes : [];
            const cols = [
                h.code || '',
                h.name || '',
                h.level || '',
                parent ? parent.code : '',
                h.lat != null ? h.lat : '',
                h.lng != null ? h.lng : '',
                wards.length,
                wards.join(';')
            ].map(function (v) {
                return '"' + String(v).replace(/"/g, '""') + '"';
            });
            csvLines.push(cols.join(','));
        });

        const blob = new Blob(['\uFEFF' + csvLines.join('\r\n')], { type: 'text/csv;charset=utf-8;' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = 'ute-express-hubs.csv';
        document.body.appendChild(a);
        a.click();
        a.remove();
        URL.revokeObjectURL(url);
    }

    async function loadHubs() {
        try {
            const res = await global.apiFetch('/api/ops/hubs');
            if (!res.ok) {
                showPageAlert('danger', await extractErrorMessage(res, 'Không thể tải danh sách Hub.'));
                return;
            }
            const data = await res.json();
            allHubs = Array.isArray(data) ? data : [];
            hubById = new Map(allHubs.map(function (h) { return [h.id, h]; }));

            updateStats();
            populateParentFilterSelect();
            renderDirectoryTable();
            renderHierarchyTree();
        } catch (_) {
            showPageAlert('danger', 'Không thể kết nối máy chủ để tải dữ liệu Hub.');
        }
    }

    function bindTabs() {
        const buttons = document.querySelectorAll('[data-hub-tab]');
        buttons.forEach(function (btn) {
            btn.addEventListener('click', function () {
                const targetId = btn.getAttribute('data-hub-tab');
                buttons.forEach(function (b) {
                    const active = b === btn;
                    b.classList.toggle('is-active', active);
                    b.setAttribute('aria-selected', String(active));
                    const panel = document.getElementById(b.getAttribute('data-hub-tab'));
                    if (panel) panel.classList.toggle('d-none', !active);
                });
                if (targetId === 'panel-coverage') {
                    fetchProvincesIfNeeded();
                }
            });
        });
    }

    function bindEvents() {
        bindTabs();

        document.getElementById('btn-refresh-hubs')?.addEventListener('click', function () {
            wardsCacheByProvince.clear();
            loadHubs();
        });
        document.getElementById('btn-export-hubs-csv')?.addEventListener('click', exportHubsCsv);
        document.getElementById('btn-open-create-hub')?.addEventListener('click', openCreateHubModal);

        let searchTimer = null;
        document.getElementById('filter-hub-search')?.addEventListener('input', function () {
            clearTimeout(searchTimer);
            searchTimer = setTimeout(renderDirectoryTable, 200);
        });
        document.getElementById('filter-hub-level')?.addEventListener('change', renderDirectoryTable);
        document.getElementById('filter-hub-parent')?.addEventListener('change', renderDirectoryTable);
        document.getElementById('btn-reset-hub-filters')?.addEventListener('click', function () {
            document.getElementById('filter-hub-search').value = '';
            document.getElementById('filter-hub-level').value = '';
            document.getElementById('filter-hub-parent').value = '';
            renderDirectoryTable();
        });

        // Hub form events
        document.getElementById('hf-level')?.addEventListener('change', function () {
            populateFormParentSelect(document.getElementById('hf-id').value || null);
        });
        document.querySelectorAll('[data-preset-coord]').forEach(function (btn) {
            btn.addEventListener('click', function () {
                const parts = btn.getAttribute('data-preset-coord').split(',');
                document.getElementById('hf-lat').value = parts[0];
                document.getElementById('hf-lng').value = parts[1];
            });
        });
        document.getElementById('hub-form')?.addEventListener('submit', handleSaveHub);
        document.getElementById('btn-confirm-delete-hub')?.addEventListener('click', handleConfirmDeleteHub);

        // Coverage tab events
        document.getElementById('cov-province')?.addEventListener('change', loadCoverageForProvince);
        document.getElementById('cov-search')?.addEventListener('input', renderCoverageTable);
        document.getElementById('cov-filter-assigned')?.addEventListener('change', renderCoverageTable);

        // Assign wards modal events
        document.getElementById('aw-province')?.addEventListener('change', async function (e) {
            const code = e.target.value;
            if (!code) {
                awCurrentProvinceWards = [];
                document.getElementById('aw-ward-list').innerHTML =
                    '<div class="p-4 text-center text-muted small">Chọn Tỉnh/Thành phố để hiển thị danh sách Phường/Xã.</div>';
                return;
            }
            document.getElementById('aw-ward-list').innerHTML = '<div class="p-4 text-center text-muted small">Đang tải...</div>';
            awCurrentProvinceWards = await fetchWardsByProvince(code, false);
            renderWardPickerList();
        });
        document.getElementById('aw-search')?.addEventListener('input', renderWardPickerList);
        document.getElementById('aw-ward-list')?.addEventListener('change', function (e) {
            if (!e.target.classList.contains('aw-ward-cb')) return;
            if (e.target.checked) awSelectedWardCodes.add(e.target.value);
            else awSelectedWardCodes.delete(e.target.value);
            renderSelectedWardChips();
        });
        document.getElementById('btn-aw-select-visible')?.addEventListener('click', function () {
            document.querySelectorAll('#aw-ward-list .aw-ward-cb').forEach(function (cb) {
                cb.checked = true;
                awSelectedWardCodes.add(cb.value);
            });
            renderSelectedWardChips();
        });
        document.getElementById('btn-aw-clear-visible')?.addEventListener('click', function () {
            document.querySelectorAll('#aw-ward-list .aw-ward-cb').forEach(function (cb) {
                cb.checked = false;
                awSelectedWardCodes.delete(cb.value);
            });
            renderSelectedWardChips();
        });
        document.getElementById('btn-aw-clear-all')?.addEventListener('click', function () {
            awSelectedWardCodes.clear();
            renderWardPickerList();
            renderSelectedWardChips();
        });
        document.getElementById('aw-selected-chips')?.addEventListener('click', function (e) {
            const btn = e.target.closest('[data-remove-ward]');
            if (!btn) return;
            awSelectedWardCodes.delete(btn.getAttribute('data-remove-ward'));
            renderWardPickerList();
            renderSelectedWardChips();
        });
        document.getElementById('assign-wards-form')?.addEventListener('submit', handleSaveAssignedWards);

        // Drawer action buttons
        document.getElementById('drawer-btn-edit')?.addEventListener('click', function () {
            if (selectedDrawerHubId) openEditHubModal(selectedDrawerHubId);
        });
        document.getElementById('drawer-btn-assign-wards')?.addEventListener('click', function () {
            if (selectedDrawerHubId) openAssignWardsModal(selectedDrawerHubId);
        });
        document.getElementById('drawer-btn-delete')?.addEventListener('click', function () {
            if (selectedDrawerHubId) openDeleteHubModal(selectedDrawerHubId);
        });

        // Delegated actions across directory, coverage, hierarchy, and drawer
        document.addEventListener('click', function (e) {
            const btn = e.target.closest('[data-action]');
            if (btn) {
                const action = btn.getAttribute('data-action');
                const id = btn.getAttribute('data-id');
                if (action === 'detail' && id) {
                    openDetailDrawer(id);
                } else if (action === 'edit' && id) {
                    openEditHubModal(id);
                } else if (action === 'wards' && id) {
                    openAssignWardsModal(id);
                } else if (action === 'delete' && id) {
                    openDeleteHubModal(id);
                } else if (action === 'empty-create-hub') {
                    openCreateHubModal();
                } else if (action === 'quick-assign-ward') {
                    const wardCode = btn.getAttribute('data-ward');
                    const defaultHub = allHubs.find(function (h) { return h.level === 'LOCAL_POST_OFFICE'; }) || allHubs[0];
                    if (defaultHub) {
                        openAssignWardsModal(defaultHub.id, wardCode);
                    }
                }
                return;
            }

            const row = e.target.closest('tr[data-row-hub-id]');
            if (row && !e.target.closest('button, a, input, select')) {
                openDetailDrawer(row.getAttribute('data-row-hub-id'));
            }
        });
    }

    document.addEventListener('DOMContentLoaded', function () {
        const mainEl = document.getElementById('main-content');
        isAdmin = mainEl?.getAttribute('data-is-admin') === 'true';

        if (global.bootstrap) {
            const m1 = document.getElementById('hubFormModal');
            const m2 = document.getElementById('assignWardsModal');
            const m3 = document.getElementById('deleteHubModal');
            const d1 = document.getElementById('hubDetailDrawer');
            if (m1) hubFormModalInstance = new global.bootstrap.Modal(m1);
            if (m2) assignWardsModalInstance = new global.bootstrap.Modal(m2);
            if (m3) deleteHubModalInstance = new global.bootstrap.Modal(m3);
            if (d1) detailDrawerInstance = new global.bootstrap.Offcanvas(d1);
        }

        bindEvents();
        loadHubs();
    });
})(window);
