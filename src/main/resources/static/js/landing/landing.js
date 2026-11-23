(function (global) {
    'use strict';

    document.addEventListener('DOMContentLoaded', async function () {
        if (global.lucide && typeof global.lucide.createIcons === 'function') {
            global.lucide.createIcons();
        }

        // Silent session restore on Landing Page when Access Token expired but Refresh Token is valid
        const body = document.body;
        const isAuthenticated = body && body.dataset.authenticated === 'true';
        const params = new URLSearchParams(global.location.search);
        if (isAuthenticated && global.authState) {
            global.authState.markLoggedIn();
        } else if (!isAuthenticated && params.get('logged_out') !== '1' && global.authState && global.authState.hasHint()) {
            const refreshed = await global.authRefreshSilent();
            if (refreshed) {
                global.location.reload();
                return;
            }
        }

        // Wire Landing Page Sign in / Sign up triggers to Bootstrap Auth Modal
        const modalEl = document.getElementById('authModal');
        const authCardEl = modalEl ? modalEl.querySelector('[data-auth-card]') : null;
        const bsModal = modalEl && global.bootstrap ? new global.bootstrap.Modal(modalEl) : null;

        document.querySelectorAll('[data-open-auth]').forEach(function (btn) {
            btn.addEventListener('click', function (e) {
                const tab = btn.dataset.openAuth === 'signup' ? 'signup' : 'signin';
                if (bsModal && authCardEl) {
                    e.preventDefault();
                    if (typeof authCardEl._switchAuthTab === 'function') {
                        authCardEl._switchAuthTab(tab);
                    }
                    bsModal.show();
                }
            });
        });

        // Wire Logout button
        document.querySelectorAll('[data-logout-btn]').forEach(function (btn) {
            btn.addEventListener('click', function (e) {
                e.preventDefault();
                btn.disabled = true;
                if (typeof global.authLogout === 'function') {
                    global.authLogout('/?logged_out=1');
                }
            });
        });
    });
})(window);
