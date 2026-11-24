(function (global) {
    'use strict';

    const SAFE_METHODS = new Set(['GET', 'HEAD', 'OPTIONS', 'TRACE']);
    const NO_REFRESH_PATHS = ['/auth/login', '/auth/register', '/auth/refresh', '/auth/logout', '/auth/forgot-password', '/auth/reset-password'];
    const AUTH_HINT_KEY = 'le_auth_hint';
    let refreshPromise = null;

    function getCsrfToken() {
        const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]*)/);
        return match ? decodeURIComponent(match[1]) : null;
    }

    function markLoggedIn() {
        try {
            global.sessionStorage.setItem(AUTH_HINT_KEY, '1');
        } catch (_) { /* storage may be unavailable */ }
    }

    function clearClientAuthState() {
        try {
            global.sessionStorage.removeItem(AUTH_HINT_KEY);
        } catch (_) { /* storage may be unavailable */ }
    }

    function hasAuthHint() {
        try {
            return global.sessionStorage.getItem(AUTH_HINT_KEY) === '1';
        } catch (_) {
            return false;
        }
    }

    function buildOptions(options) {
        const opts = Object.assign({ credentials: 'same-origin' }, options || {});
        const method = (opts.method || 'GET').toUpperCase();
        opts.method = method;

        const headers = new Headers(opts.headers || {});
        if (!SAFE_METHODS.has(method)) {
            const csrfToken = getCsrfToken();
            if (csrfToken) {
                headers.set('X-XSRF-TOKEN', csrfToken);
            }
        }
        if (opts.body && typeof opts.body === 'object' && !(opts.body instanceof FormData) && !(opts.body instanceof URLSearchParams)) {
            if (!headers.has('Content-Type')) {
                headers.set('Content-Type', 'application/json');
            }
            opts.body = JSON.stringify(opts.body);
        }
        if (!headers.has('Accept')) {
            headers.set('Accept', 'application/json');
        }
        opts.headers = headers;
        return opts;
    }

    async function refreshSessionOnce() {
        if (!refreshPromise) {
            const headers = new Headers({ Accept: 'application/json' });
            const csrfToken = getCsrfToken();
            if (csrfToken) {
                headers.set('X-XSRF-TOKEN', csrfToken);
            }
            refreshPromise = fetch('/auth/refresh', {
                method: 'POST',
                credentials: 'same-origin',
                headers: headers
            }).then(function (res) {
                if (res && res.ok) {
                    markLoggedIn();
                } else {
                    clearClientAuthState();
                }
                return res;
            }).catch(function () {
                clearClientAuthState();
                return null;
            }).finally(function () {
                refreshPromise = null;
            });
        }
        return refreshPromise;
    }

    async function apiFetch(url, options) {
        const response = await fetch(url, buildOptions(options));
        if (response.status !== 401) {
            return response;
        }

        const path = typeof url === 'string' ? url : (url && url.url) || '';
        const shouldSkipRefresh = NO_REFRESH_PATHS.some(function (p) {
            return path.includes(p);
        });
        if (shouldSkipRefresh) {
            return response;
        }

        const refreshRes = await refreshSessionOnce();
        if (refreshRes && refreshRes.ok) {
            return fetch(url, buildOptions(options));
        }

        clearClientAuthState();
        const next = encodeURIComponent(global.location.pathname + global.location.search);
        global.location.assign('/login?next=' + next + '&error=session_expired');
        return response;
    }

    async function authRefreshSilent() {
        const res = await refreshSessionOnce();
        if (!res || !res.ok) {
            return null;
        }
        try {
            return await res.json();
        } catch (_) {
            return { status: 'AUTHENTICATED' };
        }
    }

    async function authLogout(redirectTo) {
        try {
            await fetch('/auth/logout', buildOptions({ method: 'POST' }));
        } catch (_) {
            // Even if network fails, clear client hint
        } finally {
            clearClientAuthState();
        }
        global.location.assign(redirectTo || '/?logged_out=1');
    }

    global.apiFetch = apiFetch;
    global.getCsrfToken = getCsrfToken;
    global.authRefreshSilent = authRefreshSilent;
    global.authLogout = authLogout;
    global.authState = {
        markLoggedIn: markLoggedIn,
        clear: clearClientAuthState,
        hasHint: hasAuthHint
    };
})(window);
