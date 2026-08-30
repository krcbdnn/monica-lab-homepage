(function (root, factory) {
    if (typeof module === 'object' && module.exports) {
        module.exports = factory();
    } else {
        root.AdminCkeditorConfig = factory();
    }
})(typeof self !== 'undefined' ? self : this, function () {
    'use strict';

    // P13-T23: 기존 inline/block/side 버튼은 그대로 두고 ImageStyle에 이미 등록되어 있는
    // alignLeft/alignCenter/alignRight를 toolbar에 추가로 노출한다(신규 plugin/build 불필요).
    var EDITOR_CONFIG = {
        image: {
            toolbar: [
                'imageStyle:inline', 'imageStyle:block', 'imageStyle:side',
                'imageStyle:alignLeft', 'imageStyle:alignCenter', 'imageStyle:alignRight',
                '|', 'toggleImageCaption', 'imageTextAlternative'
            ]
        }
    };

    return {
        EDITOR_CONFIG: EDITOR_CONFIG
    };
});
