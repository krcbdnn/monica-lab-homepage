(function (root, factory) {
    if (typeof module === 'object' && module.exports) {
        module.exports = factory();
    } else {
        root.AdminLayout = factory();
    }
})(typeof self !== 'undefined' ? self : this, function () {
    'use strict';

    // P14-T9A: 이 파일의 책임은 정확히 두 가지 - (A) mobile off-canvas sidebar 열고 닫기, (B) logout
    // 상호작용 - 뿐이다. delete/validation/CKEditor/active route 계산(서버 SSR이 담당) 등은 여기서
    // 다루지 않는다.

    function bootstrapMobileSidebar(doc) {
        var toggle = doc.getElementById('admin-sidebar-toggle');
        var sidebar = doc.getElementById('admin-sidebar');
        if (!toggle || !sidebar) {
            return;
        }

        function setOpen(isOpen) {
            sidebar.classList.toggle('is-open', isOpen);
            toggle.setAttribute('aria-expanded', isOpen ? 'true' : 'false');
        }

        toggle.addEventListener('click', function () {
            setOpen(!sidebar.classList.contains('is-open'));
        });

        doc.addEventListener('keydown', function (event) {
            if (event.key === 'Escape' && sidebar.classList.contains('is-open')) {
                setOpen(false);
            }
        });
    }

    // 실패 시 절대 /admin/login으로 이동하지 않는다 - 현재 화면에 머무르며 버튼을 다시 활성화하고
    // 실패를 알린다(기존 프로젝트가 파일 업로드 실패 등에 이미 쓰고 있는 window.alert 패턴을
    // 그대로 재사용 - 새 toast/notification 컴포넌트를 만들지 않는다).
    function bootstrapLogout(adminFetch, doc) {
        var button = doc.getElementById('admin-logout-button');
        if (!button) {
            return;
        }

        button.addEventListener('click', function () {
            button.disabled = true;

            adminFetch('/api/admin/logout', {method: 'POST'})
                .then(function (response) {
                    return response.json().then(function (body) {
                        return {ok: response.ok, body: body};
                    });
                })
                .then(function (result) {
                    if (result.ok && result.body.success) {
                        window.location.href = '/admin/login';
                        return;
                    }
                    button.disabled = false;
                    window.alert((result.body.error && result.body.error.message)
                        || '로그아웃 중 오류가 발생했습니다.');
                })
                .catch(function () {
                    button.disabled = false;
                    window.alert('로그아웃 중 오류가 발생했습니다.');
                });
        });
    }

    function bootstrap(adminFetch, doc) {
        bootstrapMobileSidebar(doc);
        bootstrapLogout(adminFetch, doc);
    }

    return {
        bootstrap: bootstrap
    };
});
