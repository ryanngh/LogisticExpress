(function (global) {
    'use strict';

    const SAFE_METHODS = new Set(['GET', 'HEAD', 'OPTIONS', 'TRACE']);
    const NO_REFRESH_PATHS = ['/auth/login', '/auth/register', '/auth/refresh', '/auth/forgot-password', '/auth/reset-password'];
    let refreshPromise = null;

    function getCsrfToken() {
        const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]*)/);
        return match ? decodeURIComponent(match[1]) : null;
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

        const next = encodeURIComponent(window.location.pathname + window.location.search);
        window.location.assign('/login?next=' + next);
        return response;
    }

    global.apiFetch = apiFetch;
    global.getCsrfToken = getCsrfToken;
})(window);
