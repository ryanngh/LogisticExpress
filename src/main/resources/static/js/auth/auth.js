(function (global) {
    'use strict';

    const PHONE_REGEX = /^(0|\+84)[0-9]{9,10}$/;
    const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

    function initIcons() {
        if (global.lucide && typeof global.lucide.createIcons === 'function') {
            global.lucide.createIcons();
        }
    }

    function setFieldError(input, errorEl, message) {
        if (!input || !errorEl) return;
        if (message) {
            input.classList.add('is-invalid');
            input.setAttribute('aria-invalid', 'true');
            errorEl.textContent = message;
            errorEl.classList.remove('d-none');
        } else {
            input.classList.remove('is-invalid');
            input.removeAttribute('aria-invalid');
            errorEl.textContent = '';
            errorEl.classList.add('d-none');
        }
    }

    function showBanner(bannerEl, type, message) {
        if (!bannerEl) return;
        if (!message) {
            bannerEl.classList.add('d-none');
            bannerEl.innerHTML = '';
            return;
        }
        bannerEl.className = 'le-alert ' + (type === 'success' ? 'le-alert-success' : 'le-alert-error');
        bannerEl.setAttribute('role', type === 'success' ? 'status' : 'alert');
        const iconName = type === 'success' ? 'check-circle-2' : 'alert-circle';
        bannerEl.innerHTML =
            '<i data-lucide="' + iconName + '" class="flex-shrink-0 mt-1" style="width:16px;height:16px;" aria-hidden="true"></i>' +
            '<span></span>';
        bannerEl.querySelector('span').textContent = message;
        bannerEl.classList.remove('d-none');
        initIcons();
    }

    function setButtonLoading(btn, loading, loadingLabel) {
        if (!btn) return;
        if (loading) {
            if (!btn.dataset.originalHtml) {
                btn.dataset.originalHtml = btn.innerHTML;
            }
            btn.disabled = true;
            btn.innerHTML =
                '<i data-lucide="loader-2" class="le-spin" style="width:16px;height:16px;" aria-hidden="true"></i>' +
                '<span>' + loadingLabel + '</span>';
            initIcons();
        } else {
            btn.disabled = false;
            if (btn.dataset.originalHtml) {
                btn.innerHTML = btn.dataset.originalHtml;
            }
        }
    }

    function mapServerError(data, fallback) {
        if (!data || !data.message) return fallback;
        const code = data.errorCode || '';
        if (code === 'INVALID_CREDENTIALS') {
            return 'Tên tài khoản/Email hoặc mật khẩu không chính xác.';
        }
        if (code === 'ACCOUNT_LOCKED') {
            return 'Tài khoản tạm thời bị khóa do nhập sai nhiều lần. Vui lòng thử lại sau.';
        }
        if (code === 'ACCOUNT_DISABLED') {
            return 'Tài khoản đã bị vô hiệu hóa. Vui lòng liên hệ quản trị viên.';
        }
        if (code === 'ACCOUNT_ALREADY_EXISTS') {
            if (data.message.toLowerCase().includes('email')) {
                return 'Email này đã được đăng ký. Vui lòng sử dụng email khác hoặc đăng nhập.';
            }
            if (data.message.toLowerCase().includes('phone')) {
                return 'Số điện thoại này đã được đăng ký. Vui lòng kiểm tra lại.';
            }
            return 'Tài khoản đã tồn tại trong hệ thống.';
        }
        return data.message;
    }

    function resolveTargetRedirect(data, rootEl) {
        const nextUrl = rootEl ? (rootEl.dataset.nextUrl || '').trim() : '';
        if (nextUrl && nextUrl.startsWith('/') && !nextUrl.startsWith('//')) {
            return nextUrl;
        }
        if (data && typeof data.redirectUrl === 'string' && data.redirectUrl.startsWith('/')) {
            return data.redirectUrl;
        }
        return '/';
    }

    function initAuthCard(rootEl) {
        if (!rootEl || rootEl.dataset.authInitialized === 'true') return;
        rootEl.dataset.authInitialized = 'true';

        const tabs = rootEl.querySelectorAll('[data-auth-tab]');
        const panels = rootEl.querySelectorAll('[data-auth-panel]');
        const subtitleEl = rootEl.querySelector('[data-auth-subtitle]');

        const subtitles = {
            signin: 'Đăng nhập hoặc tạo tài khoản Shop để bắt đầu quản lý vận đơn.',
            signup: 'Tạo tài khoản Shop để bắt đầu lên đơn và quản lý vận chuyển.',
            forgot: 'Nhập email đã đăng ký để nhận hướng dẫn đặt lại mật khẩu.'
        };

        function switchTab(tabName) {
            const target = ['signin', 'signup', 'forgot'].includes(tabName) ? tabName : 'signin';
            tabs.forEach(function (tab) {
                const active = tab.dataset.authTab === target;
                tab.classList.toggle('is-active', active);
                tab.setAttribute('aria-selected', active ? 'true' : 'false');
            });
            panels.forEach(function (panel) {
                panel.classList.toggle('d-none', panel.dataset.authPanel !== target);
            });
            if (subtitleEl && subtitles[target]) {
                subtitleEl.textContent = subtitles[target];
            }
        }

        tabs.forEach(function (tab) {
            tab.addEventListener('click', function () {
                switchTab(tab.dataset.authTab);
            });
        });

        rootEl.querySelectorAll('[data-switch-tab]').forEach(function (trigger) {
            trigger.addEventListener('click', function () {
                switchTab(trigger.dataset.switchTab);
            });
        });

        // Password show/hide toggles
        rootEl.querySelectorAll('[data-toggle-pw]').forEach(function (btn) {
            btn.addEventListener('click', function () {
                const input = rootEl.querySelector('#' + btn.dataset.togglePw);
                if (!input) return;
                const show = input.type === 'password';
                input.type = show ? 'text' : 'password';
                btn.textContent = show ? 'Ẩn' : 'Hiện';
                btn.setAttribute('aria-label', show ? 'Ẩn mật khẩu' : 'Hiện mật khẩu');
            });
        });

        // --- SIGN IN FORM ---
        const signinForm = rootEl.querySelector('[data-form="signin"]');
        if (signinForm) {
            const accountInput = signinForm.querySelector('#login-account');
            const accountErr = signinForm.querySelector('#login-account-error');
            const passwordInput = signinForm.querySelector('#login-password');
            const passwordErr = signinForm.querySelector('#login-password-error');
            const banner = signinForm.querySelector('[data-form-banner]');
            const submitBtn = signinForm.querySelector('button[type="submit"]');

            signinForm.addEventListener('submit', async function (e) {
                e.preventDefault();
                showBanner(banner, null, '');

                const usernameOrEmail = (accountInput.value || '').trim();
                const password = passwordInput.value || '';
                let valid = true;

                if (!usernameOrEmail) {
                    setFieldError(accountInput, accountErr, 'Vui lòng nhập tên tài khoản hoặc email.');
                    valid = false;
                } else {
                    setFieldError(accountInput, accountErr, '');
                }

                if (!password) {
                    setFieldError(passwordInput, passwordErr, 'Vui lòng nhập mật khẩu.');
                    valid = false;
                } else {
                    setFieldError(passwordInput, passwordErr, '');
                }

                if (!valid) {
                    const firstInvalid = signinForm.querySelector('.is-invalid');
                    if (firstInvalid) firstInvalid.focus();
                    return;
                }

                setButtonLoading(submitBtn, true, 'Đang đăng nhập...');
                try {
                    const res = await global.apiFetch('/auth/login', {
                        method: 'POST',
                        body: { usernameOrEmail: usernameOrEmail, password: password }
                    });
                    const data = await res.json().catch(function () { return {}; });
                    if (res.ok) {
                        if (global.authState) global.authState.markLoggedIn();
                        if (subtitleEl) {
                            subtitleEl.textContent = 'Đang chuyển hướng về bảng điều khiển vận đơn.';
                        }
                        showBanner(banner, 'success', 'Thông tin hợp lệ. Đang đăng nhập vào hệ thống...');
                        const target = resolveTargetRedirect(data, rootEl);
                        setTimeout(function () {
                            global.location.assign(target);
                        }, 500);
                    } else {
                        setButtonLoading(submitBtn, false);
                        showBanner(banner, 'error', mapServerError(data, 'Đăng nhập thất bại. Vui lòng thử lại.'));
                    }
                } catch (_) {
                    setButtonLoading(submitBtn, false);
                    showBanner(banner, 'error', 'Không thể kết nối máy chủ. Vui lòng thử lại sau.');
                }
            });
        }

        // --- SIGN UP FORM (5 required fields per Auth State 02; OTP skipped per backend) ---
        const signupForm = rootEl.querySelector('[data-form="signup"]');
        if (signupForm) {
            const fullNameInput = signupForm.querySelector('#reg-fullname');
            const fullNameErr = signupForm.querySelector('#reg-fullname-error');
            const phoneInput = signupForm.querySelector('#reg-phone');
            const phoneErr = signupForm.querySelector('#reg-phone-error');
            const emailInput = signupForm.querySelector('#reg-email');
            const emailErr = signupForm.querySelector('#reg-email-error');
            const passwordInput = signupForm.querySelector('#reg-password');
            const passwordErr = signupForm.querySelector('#reg-password-error');
            const confirmInput = signupForm.querySelector('#reg-confirm');
            const confirmErr = signupForm.querySelector('#reg-confirm-error');
            const banner = signupForm.querySelector('[data-form-banner]');
            const submitBtn = signupForm.querySelector('button[type="submit"]');

            function validateSignup() {
                let valid = true;
                const fullName = (fullNameInput.value || '').trim();
                const phone = (phoneInput.value || '').trim();
                const email = (emailInput.value || '').trim();
                const password = passwordInput.value || '';
                const confirm = confirmInput.value || '';

                if (!fullName) {
                    setFieldError(fullNameInput, fullNameErr, 'Vui lòng nhập họ tên.');
                    valid = false;
                } else {
                    setFieldError(fullNameInput, fullNameErr, '');
                }

                if (!PHONE_REGEX.test(phone)) {
                    setFieldError(phoneInput, phoneErr, 'Số điện thoại Việt Nam không hợp lệ (VD: 0901234567).');
                    valid = false;
                } else {
                    setFieldError(phoneInput, phoneErr, '');
                }

                if (!EMAIL_REGEX.test(email)) {
                    setFieldError(emailInput, emailErr, 'Email không hợp lệ.');
                    valid = false;
                } else {
                    setFieldError(emailInput, emailErr, '');
                }

                if (password.length < 6 || password.length > 100) {
                    setFieldError(passwordInput, passwordErr, 'Mật khẩu phải từ 6 đến 100 ký tự.');
                    valid = false;
                } else {
                    setFieldError(passwordInput, passwordErr, '');
                }

                if (!confirm || confirm !== password) {
                    setFieldError(confirmInput, confirmErr, 'Mật khẩu nhập lại không khớp.');
                    valid = false;
                } else {
                    setFieldError(confirmInput, confirmErr, '');
                }

                return valid;
            }

            signupForm.addEventListener('submit', async function (e) {
                e.preventDefault();
                showBanner(banner, null, '');

                if (!validateSignup()) {
                    if (subtitleEl) {
                        subtitleEl.textContent = 'Kiểm tra các trường vi phạm định dạng trước khi gửi.';
                    }
                    const firstInvalid = signupForm.querySelector('.is-invalid');
                    if (firstInvalid) firstInvalid.focus();
                    return;
                }

                setButtonLoading(submitBtn, true, 'Đang tạo tài khoản...');
                try {
                    const res = await global.apiFetch('/auth/register', {
                        method: 'POST',
                        body: {
                            fullName: fullNameInput.value.trim(),
                            phone: phoneInput.value.trim(),
                            email: emailInput.value.trim(),
                            password: passwordInput.value
                        }
                    });
                    const data = await res.json().catch(function () { return {}; });
                    if (res.ok) {
                        if (global.authState) global.authState.markLoggedIn();
                        if (subtitleEl) {
                            subtitleEl.textContent = 'Đang chuyển hướng về bảng điều khiển vận đơn.';
                        }
                        showBanner(banner, 'success', 'Đăng ký thành công. Đang đăng nhập vào hệ thống...');
                        const target = resolveTargetRedirect(data, rootEl);
                        setTimeout(function () {
                            global.location.assign(target);
                        }, 600);
                    } else {
                        setButtonLoading(submitBtn, false);
                        showBanner(banner, 'error', mapServerError(data, 'Đăng ký thất bại. Vui lòng kiểm tra lại thông tin.'));
                    }
                } catch (_) {
                    setButtonLoading(submitBtn, false);
                    showBanner(banner, 'error', 'Không thể kết nối máy chủ. Vui lòng thử lại sau.');
                }
            });
        }

        // --- FORGOT PASSWORD FORM ---
        const forgotForm = rootEl.querySelector('[data-form="forgot"]');
        if (forgotForm) {
            const emailInput = forgotForm.querySelector('#forgot-email');
            const emailErr = forgotForm.querySelector('#forgot-email-error');
            const banner = forgotForm.querySelector('[data-form-banner]');
            const submitBtn = forgotForm.querySelector('button[type="submit"]');

            forgotForm.addEventListener('submit', async function (e) {
                e.preventDefault();
                showBanner(banner, null, '');
                const email = (emailInput.value || '').trim();
                if (!EMAIL_REGEX.test(email)) {
                    setFieldError(emailInput, emailErr, 'Email không hợp lệ.');
                    emailInput.focus();
                    return;
                }
                setFieldError(emailInput, emailErr, '');

                setButtonLoading(submitBtn, true, 'Đang gửi yêu cầu...');
                try {
                    const res = await global.apiFetch('/auth/forgot-password', {
                        method: 'POST',
                        body: { email: email }
                    });
                    setButtonLoading(submitBtn, false);
                    if (res.ok) {
                        showBanner(banner, 'success', 'Nếu email tồn tại trong hệ thống, hướng dẫn đặt lại mật khẩu đã được ghi nhận.');
                    } else {
                        const data = await res.json().catch(function () { return {}; });
                        showBanner(banner, 'error', mapServerError(data, 'Không thể gửi yêu cầu. Vui lòng thử lại.'));
                    }
                } catch (_) {
                    setButtonLoading(submitBtn, false);
                    showBanner(banner, 'error', 'Không thể kết nối máy chủ. Vui lòng thử lại sau.');
                }
            });
        }

        // Initial tab
        switchTab(rootEl.dataset.initialTab || 'signin');

        // Expose switchTab for modal triggers
        rootEl._switchAuthTab = switchTab;
    }

    // On standalone /login page: if Access Token expired while Refresh Token is still valid,
    // silently refresh once so the user does NOT have to log in again.
    async function trySilentSessionRestore(standaloneRoot) {
        if (!standaloneRoot || standaloneRoot.dataset.standalone !== 'true') return;
        const params = new URLSearchParams(global.location.search);
        const errorParam = params.get('error');
        const loggedOut = params.get('logged_out');
        const nextUrl = standaloneRoot.dataset.nextUrl || '';
        const isAuthenticated = standaloneRoot.dataset.authenticated === 'true';

        if (isAuthenticated || loggedOut === '1' || errorParam === 'session_expired' || errorParam === 'forbidden') {
            return;
        }
        if (!nextUrl && !(global.authState && global.authState.hasHint())) {
            return;
        }

        const refreshed = await global.authRefreshSilent();
        if (refreshed) {
            const target = resolveTargetRedirect(refreshed, standaloneRoot);
            global.location.replace(target);
        }
    }

    document.addEventListener('DOMContentLoaded', function () {
        initIcons();
        document.querySelectorAll('[data-auth-card]').forEach(initAuthCard);

        const standaloneCard = document.querySelector('[data-auth-card][data-standalone="true"]');
        if (standaloneCard) {
            trySilentSessionRestore(standaloneCard);
        }
    });

    global.initAuthCard = initAuthCard;
})(window);
