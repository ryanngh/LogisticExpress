(function (global) {
    'use strict';

    const ROLE_META = {
        ROLE_ADMIN: { label: 'Admin', cls: 'le-badge-danger' },
        ROLE_MANAGER: { label: 'Quản lý', cls: 'le-badge-info' },
        ROLE_DISPATCHER: { label: 'Điều phối', cls: 'le-badge-info' },
        ROLE_HUB_STAFF: { label: 'NV Hub', cls: 'le-badge-teal' },
        ROLE_SHIPPER: { label: 'Shipper', cls: 'le-badge-warning' },
        ROLE_ACCOUNTANT: { label: 'Kế toán', cls: 'le-badge-neutral' },
        ROLE_CSKH: { label: 'CSKH', cls: 'le-badge-neutral' },
        ROLE_SHOP: { label: 'Shop', cls: 'le-badge-neutral' }
    };

    const USER_STATUS_META = {
        ACTIVE: { label: 'Hoạt động', cls: 'le-badge-success' },
        PENDING_VERIFICATION: { label: 'Chờ xác thực', cls: 'le-badge-warning' },
        DISABLED: { label: 'Vô hiệu hóa', cls: 'le-badge-neutral' }
    };

    let allStaff = [];
    let allHubs = [];
    let hubById = new Map();
    let isAdmin = false;

    let staffModalInstance = null;
    let profileOnlyModalInstance = null;
    let disableModalInstance = null;

    function showPageAlert(type, message) {
        const el = document.getElementById('users-alert');
        if (!el) return;
        el.className = 'alert alert-' + type + ' alert-dismissible fade show mb-4';
        el.innerHTML = global.opsCommon.escapeHtml(message) +
            '<button type="button" class="btn-close" data-bs-dismiss="alert" aria-label="Đóng"></button>';
    }

    function showFormError(elId, message) {
        const el = document.getElementById(elId);
        if (!el) return;
        if (!message) {
            el.classList.add('d-none');
            el.textContent = '';
            return;
        }
        el.textContent = message;
        el.classList.remove('d-none');
    }

    async function extractErrorMessage(res, fallback) {
        try {
            const data = await res.json();
            if (data && data.message) return data.message;
        } catch (_) { /* ignore */ }
        return fallback;
    }

    function populateHubSelects() {
        const esc = global.opsCommon.escapeHtml;
        const filterHub = document.getElementById('filter-hub');
        const spHub = document.getElementById('sp-hub-id');
        const poHub = document.getElementById('po-hub-id');

        const optionsHtml = allHubs.map(function (h) {
            return '<option value="' + esc(h.id) + '">' + esc(h.name) + ' (' + esc(h.code) + ')</option>';
        }).join('');

        if (filterHub) {
            const current = filterHub.value;
            filterHub.innerHTML = '<option value="">Tất cả Hub / Bưu cục</option>' + optionsHtml;
            filterHub.value = current;
        }
        if (spHub) {
            spHub.innerHTML = '<option value="">-- Chọn Hub trực thuộc --</option>' + optionsHtml;
        }
        if (poHub) {
            poHub.innerHTML = '<option value="">-- Chọn Hub trực thuộc --</option>' + optionsHtml;
        }
    }

    function updateStats(staffList) {
        const c = global.opsCommon;
        const total = staffList.length;
        let active = 0;
        let shippers = 0;
        let disabled = 0;

        staffList.forEach(function (u) {
            if (u.status === 'ACTIVE') active++;
            if (u.status === 'DISABLED') disabled++;
            if (Array.isArray(u.roles) && u.roles.includes('ROLE_SHIPPER')) shippers++;
        });

        const set = function (id, val) {
            const el = document.getElementById(id);
            if (el) el.textContent = c.formatNumber(val);
        };
        set('stat-total-users', total);
        set('stat-active-users', active);
        set('stat-shipper-users', shippers);
        set('stat-disabled-users', disabled);
    }

    function getFilteredStaff() {
        const q = (document.getElementById('filter-search')?.value || '').trim().toLowerCase();
        const role = document.getElementById('filter-role')?.value || '';
        const hubId = document.getElementById('filter-hub')?.value || '';
        const status = document.getElementById('filter-status')?.value || '';

        return allStaff.filter(function (u) {
            if (role && (!Array.isArray(u.roles) || !u.roles.includes(role))) return false;
            if (hubId && (!u.staffProfile || u.staffProfile.hubId !== hubId)) return false;
            if (status && u.status !== status) return false;
            if (q) {
                const haystack = [
                    u.fullName,
                    u.username,
                    u.email,
                    u.phone,
                    u.staffProfile?.employeeCode,
                    u.staffProfile?.vehiclePlate
                ].filter(Boolean).join(' ').toLowerCase();
                if (!haystack.includes(q)) return false;
            }
            return true;
        });
    }

    function renderRoleBadges(roles) {
        if (!Array.isArray(roles) || roles.length === 0) return '<span class="text-muted">—</span>';
        const esc = global.opsCommon.escapeHtml;
        return '<div class="d-flex flex-wrap gap-1">' + roles.map(function (r) {
            const meta = ROLE_META[r] || { label: r, cls: 'le-badge-neutral' };
            return '<span class="le-badge ' + meta.cls + '">' + esc(meta.label) + '</span>';
        }).join('') + '</div>';
    }

    function renderStatusBadge(status) {
        const meta = USER_STATUS_META[status] || { label: status || '—', cls: 'le-badge-neutral' };
        return '<span class="le-badge ' + meta.cls + '">' + global.opsCommon.escapeHtml(meta.label) + '</span>';
    }

    function renderStaffProfileCell(sp) {
        if (!sp) return '<span class="text-muted small">Chưa gán Hub</span>';
        const esc = global.opsCommon.escapeHtml;
        const hub = hubById.get(sp.hubId);
        const hubName = hub ? hub.name : 'Hub đã gán';
        const capacityKg = sp.maxCapacityGram ? (sp.maxCapacityGram / 1000).toFixed(0) + ' kg' : '';
        const vehicleInfo = [sp.vehiclePlate, capacityKg, sp.maxOrdersPerShift ? sp.maxOrdersPerShift + ' đơn/ca' : '']
            .filter(Boolean).join(' · ');

        return '<div class="small fw-semibold">' + esc(sp.employeeCode) + ' · ' + esc(hubName) + '</div>' +
            (vehicleInfo ? '<div class="text-muted" style="font-size:11px;">' + esc(vehicleInfo) + '</div>' : '');
    }

    function renderTable() {
        const tbody = document.getElementById('staff-table-body');
        const caption = document.getElementById('staff-count-caption');
        if (!tbody) return;

        const filtered = getFilteredStaff();
        if (caption) {
            caption.textContent = 'Hiển thị ' + filtered.length + ' / ' + allStaff.length + ' tài khoản nhân sự';
        }

        if (filtered.length === 0) {
            tbody.innerHTML = '<tr><td colspan="7" class="text-center py-5 text-muted">' +
                '<div class="fw-semibold mb-1">Không tìm thấy nhân sự phù hợp</div>' +
                '<div class="small">Thử thay đổi bộ lọc tìm kiếm hoặc tạo tài khoản nhân sự mới.</div>' +
                '</td></tr>';
            return;
        }

        const c = global.opsCommon;
        tbody.innerHTML = filtered.map(function (u) {
            const initials = (u.fullName || u.username || 'NV').trim().slice(0, 2).toUpperCase();
            const adminBtns = isAdmin
                ? '<button type="button" class="btn btn-sm btn-outline-secondary" data-action="edit" data-id="' + c.escapeHtml(u.id) + '" title="Chỉnh sửa">' +
                    '<i data-lucide="pencil" style="width:14px;height:14px;"></i></button>' +
                  (u.status !== 'DISABLED'
                    ? '<button type="button" class="btn btn-sm btn-outline-danger" data-action="disable" data-id="' + c.escapeHtml(u.id) + '" title="Vô hiệu hóa">' +
                        '<i data-lucide="user-x" style="width:14px;height:14px;"></i></button>'
                    : '')
                : '';

            return '<tr>' +
                '<td>' +
                    '<div class="d-flex align-items-center gap-2">' +
                        '<span class="le-ops-avatar" style="width:34px;height:34px;font-size:11px;">' + c.escapeHtml(initials) + '</span>' +
                        '<div class="min-w-0">' +
                            '<div class="fw-semibold text-truncate">' + c.escapeHtml(u.fullName || u.username) + '</div>' +
                            '<div class="text-muted text-truncate" style="font-size:11px;">@' + c.escapeHtml(u.username) + ' · ' + c.escapeHtml(u.email) + '</div>' +
                        '</div>' +
                    '</div>' +
                '</td>' +
                '<td class="le-tabular">' + c.escapeHtml(u.phone || '—') + '</td>' +
                '<td>' + renderRoleBadges(u.roles) + '</td>' +
                '<td>' + renderStaffProfileCell(u.staffProfile) + '</td>' +
                '<td>' + renderStatusBadge(u.status) + '</td>' +
                '<td class="le-tabular text-muted small">' + c.formatDateTime(u.lastLoginAt) + '</td>' +
                '<td class="text-end">' +
                    '<div class="d-inline-flex gap-1">' +
                        '<button type="button" class="btn btn-sm btn-outline-primary" data-action="profile" data-id="' + c.escapeHtml(u.id) + '" title="Cấu hình Hub & xe">' +
                            '<i data-lucide="truck" style="width:14px;height:14px;"></i>' +
                        '</button>' +
                        adminBtns +
                    '</div>' +
                '</td>' +
            '</tr>';
        }).join('');

        if (global.lucide && typeof global.lucide.createIcons === 'function') {
            global.lucide.createIcons();
        }
    }

    async function loadData() {
        const [usersRes, hubsRes] = await Promise.all([
            global.apiFetch('/api/ops/users', { method: 'GET' }).catch(function () { return null; }),
            global.apiFetch('/api/ops/hubs', { method: 'GET' }).catch(function () { return null; })
        ]);

        if (hubsRes && hubsRes.ok) {
            allHubs = await hubsRes.json();
            hubById = new Map(allHubs.map(function (h) { return [h.id, h]; }));
            populateHubSelects();
        }

        if (usersRes && usersRes.ok) {
            allStaff = await usersRes.json();
            updateStats(allStaff);
            renderTable();
        } else {
            const tbody = document.getElementById('staff-table-body');
            if (tbody) {
                tbody.innerHTML = '<tr><td colspan="7" class="text-center py-4 text-danger">Không thể tải danh sách nhân sự (kiểm tra quyền ADMIN/DISPATCHER).</td></tr>';
            }
        }
    }

    function toggleProfileFields(show) {
        const box = document.getElementById('staff-profile-fields');
        const toggle = document.getElementById('toggle-staff-profile');
        if (toggle) toggle.checked = Boolean(show);
        if (box) box.classList.toggle('d-none', !show);
    }

    function openCreateModal() {
        showFormError('staffFormError', '');
        document.getElementById('staffForm').reset();
        document.getElementById('staff-id').value = '';
        document.getElementById('staffModalTitle').textContent = 'Tạo tài khoản nhân sự mới';
        document.getElementById('staffModalSubtitle').textContent = 'Cấp tài khoản nội bộ và phân quyền theo vai trò';

        const usernameEl = document.getElementById('staff-username');
        const emailEl = document.getElementById('staff-email');
        usernameEl.readOnly = false;
        emailEl.readOnly = false;

        document.getElementById('staff-password-group').classList.remove('d-none');
        document.getElementById('staff-status-group').classList.add('d-none');
        toggleProfileFields(false);

        staffModalInstance.show();
    }

    function openEditModal(userId) {
        const user = allStaff.find(function (u) { return u.id === userId; });
        if (!user) return;

        showFormError('staffFormError', '');
        document.getElementById('staff-id').value = user.id;
        document.getElementById('staffModalTitle').textContent = 'Cập nhật nhân sự: ' + (user.fullName || user.username);
        document.getElementById('staffModalSubtitle').textContent = '@' + user.username + ' · ' + user.email;

        const usernameEl = document.getElementById('staff-username');
        const emailEl = document.getElementById('staff-email');
        usernameEl.value = user.username || '';
        emailEl.value = user.email || '';
        usernameEl.readOnly = true;
        emailEl.readOnly = true;

        document.getElementById('staff-fullname').value = user.fullName || '';
        document.getElementById('staff-phone').value = user.phone || '';
        document.getElementById('staff-status').value = user.status || 'ACTIVE';

        document.getElementById('staff-password-group').classList.add('d-none');
        document.getElementById('staff-status-group').classList.remove('d-none');

        const userRoles = new Set(user.roles || []);
        document.querySelectorAll('.staff-role-cb').forEach(function (cb) {
            cb.checked = userRoles.has(cb.value);
        });

        if (user.staffProfile) {
            toggleProfileFields(true);
            document.getElementById('sp-hub-id').value = user.staffProfile.hubId || '';
            document.getElementById('sp-employee-code').value = user.staffProfile.employeeCode || '';
            document.getElementById('sp-vehicle-plate').value = user.staffProfile.vehiclePlate || '';
            document.getElementById('sp-max-capacity').value = user.staffProfile.maxCapacityGram || 50000;
            document.getElementById('sp-max-orders').value = user.staffProfile.maxOrdersPerShift || 35;
        } else {
            toggleProfileFields(false);
            document.getElementById('sp-hub-id').value = '';
            document.getElementById('sp-employee-code').value = '';
            document.getElementById('sp-vehicle-plate').value = '';
            document.getElementById('sp-max-capacity').value = 50000;
            document.getElementById('sp-max-orders').value = 35;
        }

        staffModalInstance.show();
    }

    function openProfileOnlyModal(userId) {
        const user = allStaff.find(function (u) { return u.id === userId; });
        if (!user) return;

        showFormError('profileOnlyError', '');
        document.getElementById('po-user-id').value = user.id;
        document.getElementById('profileOnlyUserLabel').textContent = (user.fullName || user.username) + ' (@' + user.username + ')';

        const sp = user.staffProfile || {};
        document.getElementById('po-hub-id').value = sp.hubId || '';
        document.getElementById('po-employee-code').value = sp.employeeCode || '';
        document.getElementById('po-vehicle-plate').value = sp.vehiclePlate || '';
        document.getElementById('po-max-capacity').value = sp.maxCapacityGram || 50000;
        document.getElementById('po-max-orders').value = sp.maxOrdersPerShift || 35;

        profileOnlyModalInstance.show();
    }

    function openDisableModal(userId) {
        const user = allStaff.find(function (u) { return u.id === userId; });
        if (!user) return;
        document.getElementById('disable-staff-id').value = user.id;
        document.getElementById('disable-staff-name').textContent = (user.fullName || user.username) + ' (@' + user.username + ')';
        disableModalInstance.show();
    }

    async function handleStaffFormSubmit(event) {
        event.preventDefault();
        showFormError('staffFormError', '');

        const id = document.getElementById('staff-id').value;
        const isEdit = Boolean(id);
        const roles = Array.from(document.querySelectorAll('.staff-role-cb:checked')).map(function (cb) { return cb.value; });

        if (roles.length === 0) {
            showFormError('staffFormError', 'Vui lòng chọn ít nhất một vai trò nội bộ.');
            return;
        }

        let staffProfile = null;
        if (document.getElementById('toggle-staff-profile').checked) {
            const hubId = document.getElementById('sp-hub-id').value;
            const employeeCode = document.getElementById('sp-employee-code').value.trim();
            if (!hubId || !employeeCode) {
                showFormError('staffFormError', 'Khi gán hồ sơ Hub, vui lòng chọn Hub trực thuộc và nhập Mã nhân viên.');
                return;
            }
            staffProfile = {
                hubId: hubId,
                employeeCode: employeeCode,
                vehiclePlate: document.getElementById('sp-vehicle-plate').value.trim() || null,
                maxCapacityGram: Number(document.getElementById('sp-max-capacity').value) || 50000,
                maxOrdersPerShift: Number(document.getElementById('sp-max-orders').value) || 35
            };
        }

        const btn = document.getElementById('btn-submit-staff');
        btn.disabled = true;

        try {
            let res;
            if (isEdit) {
                const payload = {
                    fullName: document.getElementById('staff-fullname').value.trim(),
                    phone: document.getElementById('staff-phone').value.trim(),
                    roles: roles,
                    status: document.getElementById('staff-status').value,
                    staffProfile: staffProfile
                };
                res = await global.apiFetch('/api/ops/users/' + encodeURIComponent(id), {
                    method: 'PUT',
                    body: payload
                });
            } else {
                const payload = {
                    username: document.getElementById('staff-username').value.trim().toLowerCase(),
                    email: document.getElementById('staff-email').value.trim().toLowerCase(),
                    password: document.getElementById('staff-password').value,
                    fullName: document.getElementById('staff-fullname').value.trim(),
                    phone: document.getElementById('staff-phone').value.trim(),
                    roles: roles,
                    staffProfile: staffProfile
                };
                res = await global.apiFetch('/api/ops/users', {
                    method: 'POST',
                    body: payload
                });
            }

            if (!res.ok) {
                const msg = await extractErrorMessage(res, 'Không thể lưu tài khoản nhân sự.');
                showFormError('staffFormError', msg);
                return;
            }

            staffModalInstance.hide();
            showPageAlert('success', isEdit ? 'Cập nhật thông tin nhân sự thành công.' : 'Tạo tài khoản nhân sự mới thành công.');
            await loadData();
        } finally {
            btn.disabled = false;
        }
    }

    async function handleProfileOnlySubmit(event) {
        event.preventDefault();
        showFormError('profileOnlyError', '');

        const userId = document.getElementById('po-user-id').value;
        const hubId = document.getElementById('po-hub-id').value;
        const employeeCode = document.getElementById('po-employee-code').value.trim();

        if (!hubId || !employeeCode) {
            showFormError('profileOnlyError', 'Vui lòng chọn Hub trực thuộc và nhập Mã nhân viên.');
            return;
        }

        const btn = document.getElementById('btn-submit-profile-only');
        btn.disabled = true;

        try {
            const res = await global.apiFetch('/api/ops/users/' + encodeURIComponent(userId) + '/staff-profile', {
                method: 'PUT',
                body: {
                    hubId: hubId,
                    employeeCode: employeeCode,
                    vehiclePlate: document.getElementById('po-vehicle-plate').value.trim() || null,
                    maxCapacityGram: Number(document.getElementById('po-max-capacity').value) || 50000,
                    maxOrdersPerShift: Number(document.getElementById('po-max-orders').value) || 35
                }
            });

            if (!res.ok) {
                const msg = await extractErrorMessage(res, 'Không thể cập nhật hồ sơ Hub & phương tiện.');
                showFormError('profileOnlyError', msg);
                return;
            }

            profileOnlyModalInstance.hide();
            showPageAlert('success', 'Đã cập nhật cấu hình Hub & phương tiện thành công.');
            await loadData();
        } finally {
            btn.disabled = false;
        }
    }

    async function handleConfirmDisable() {
        const userId = document.getElementById('disable-staff-id').value;
        if (!userId) return;

        const btn = document.getElementById('btn-confirm-disable');
        btn.disabled = true;

        try {
            const res = await global.apiFetch('/api/ops/users/' + encodeURIComponent(userId), { method: 'DELETE' });
            if (!res.ok) {
                const msg = await extractErrorMessage(res, 'Không thể vô hiệu hóa tài khoản.');
                disableModalInstance.hide();
                showPageAlert('danger', msg);
                return;
            }

            disableModalInstance.hide();
            showPageAlert('success', 'Đã vô hiệu hóa tài khoản và thu hồi phiên đăng nhập.');
            await loadData();
        } finally {
            btn.disabled = false;
        }
    }

    function exportFilteredCsv() {
        const rows = getFilteredStaff();
        const header = ['Username', 'HoTen', 'Email', 'SoDienThoai', 'VaiTro', 'MaNV', 'Hub', 'BienSoXe', 'TrangThai'];
        const csvLines = [header.join(',')];

        rows.forEach(function (u) {
            const hubName = u.staffProfile && hubById.get(u.staffProfile.hubId) ? hubById.get(u.staffProfile.hubId).name : '';
            const vals = [
                u.username,
                u.fullName,
                u.email,
                u.phone,
                (u.roles || []).join(';'),
                u.staffProfile?.employeeCode || '',
                hubName,
                u.staffProfile?.vehiclePlate || '',
                u.status
            ].map(function (v) {
                return '"' + String(v ?? '').replace(/"/g, '""') + '"';
            });
            csvLines.push(vals.join(','));
        });

        const blob = new Blob(['\uFEFF' + csvLines.join('\n')], { type: 'text/csv;charset=utf-8;' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = 'ute-express-staff.csv';
        a.click();
        URL.revokeObjectURL(url);
    }

    document.addEventListener('DOMContentLoaded', function () {
        const mainEl = document.getElementById('main-content');
        isAdmin = mainEl && mainEl.getAttribute('data-is-admin') === 'true';

        staffModalInstance = new global.bootstrap.Modal(document.getElementById('staffModal'));
        profileOnlyModalInstance = new global.bootstrap.Modal(document.getElementById('profileOnlyModal'));
        disableModalInstance = new global.bootstrap.Modal(document.getElementById('disableStaffModal'));

        document.getElementById('btn-open-create-staff')?.addEventListener('click', openCreateModal);
        document.getElementById('btn-export-csv')?.addEventListener('click', exportFilteredCsv);
        document.getElementById('btn-reload-users')?.addEventListener('click', loadData);
        document.getElementById('btn-reset-filters')?.addEventListener('click', function () {
            document.getElementById('filter-search').value = '';
            document.getElementById('filter-role').value = '';
            document.getElementById('filter-hub').value = '';
            document.getElementById('filter-status').value = '';
            renderTable();
        });

        ['filter-search', 'filter-role', 'filter-hub', 'filter-status'].forEach(function (id) {
            const el = document.getElementById(id);
            if (!el) return;
            el.addEventListener(id === 'filter-search' ? 'input' : 'change', renderTable);
        });

        document.getElementById('toggle-staff-profile')?.addEventListener('change', function (e) {
            toggleProfileFields(e.target.checked);
        });

        document.getElementById('staff-table-body')?.addEventListener('click', function (e) {
            const btn = e.target.closest('button[data-action]');
            if (!btn) return;
            const action = btn.getAttribute('data-action');
            const id = btn.getAttribute('data-id');
            if (action === 'edit') openEditModal(id);
            else if (action === 'profile') openProfileOnlyModal(id);
            else if (action === 'disable') openDisableModal(id);
        });

        document.getElementById('staffForm')?.addEventListener('submit', handleStaffFormSubmit);
        document.getElementById('profileOnlyForm')?.addEventListener('submit', handleProfileOnlySubmit);
        document.getElementById('btn-confirm-disable')?.addEventListener('click', handleConfirmDisable);

        loadData();
    });
})(window);
