(function (root, factory) {
    if (typeof module === 'object' && module.exports) {
        module.exports = factory(require('./ckeditor-resize-plugin.js'));
    } else {
        root.AdminCkeditorConfig = factory(root.AdminCkeditorResizePlugin);
    }
})(typeof self !== 'undefined' ? self : this, function (AdminCkeditorResizePlugin) {
    'use strict';

    // P13-T25: 기존 6개 ImageStyle(inline/block/side/alignLeft/alignCenter/alignRight)의
    // name/className/modelElements는 전혀 바꾸지 않고 title만 한글로 재선언한다. title은 UI
    // 표시용 메타데이터일 뿐 저장 HTML에는 전혀 반영되지 않는다(헤드리스 실행으로 확인).
    // block과 alignCenter는 이 프로젝트의 현재 공개 CSS(.ckeditor-content .image /
    // .image-style-align-center, home.css)에서 실제로 동일하게 렌더링되므로(alignCenter가 추가하는
    // margin-left/right:auto가 block의 기존 margin:var(--space-2) auto와 완전히 중복), 두 라벨 모두
    // "가운데"를 쓰면 서로 다른 옵션처럼 오해할 수 있어 block은 "기본"으로만 표기한다.
    var imageStyles = [
        { name: 'inline', title: '글 안에 배치' },
        { name: 'block', title: '기본' },
        { name: 'side', title: '글 옆에 배치' },
        { name: 'alignLeft', title: '왼쪽 정렬' },
        { name: 'alignCenter', title: '가운데 정렬' },
        { name: 'alignRight', title: '오른쪽 정렬' }
    ];

    // P13-T23: 기존 inline/block/side 버튼은 그대로 두고 ImageStyle에 이미 등록되어 있는
    // alignLeft/alignCenter/alignRight를 toolbar에 추가로 노출했었다.
    // P13-T25: 6개 버튼을 balloon toolbar에 나열하지 않고 "이미지 정렬" dropdown 1개로 묶는다.
    // image.styles를 건드리는 것은 title(한글 라벨)뿐이고, className/modelElements는 CKEditor
    // 기본값을 그대로 쓴다(문자열로만 등록해 기존 값을 재사용) - 저장 HTML/round-trip 무변경.
    // P13-T40: 25%/50%/75%/원본 preset 이미지 크기 조절 기능. CKEditor UI(balloon toolbar)에 새
    // 버튼을 얹지 않고(41.4.2 predefined build는 ButtonView를 전역 노출하지 않아 번들러 없이는 불가능함을
    // 헤드리스로 확인) admin 폼 자체의 일반 HTML 버튼으로 제공하므로, 여기서는 model/conversion만
    // 추가하는 extraPlugins 등록 외에 별도 toolbar 항목이 없다. 이 config.js보다 먼저 로드되어야 한다
    // (admin/*/form.html 참고 - ckeditor-resize-plugin.js를 ckeditor-config.js 앞에 배치).
    var EDITOR_CONFIG = {
        image: {
            styles: { options: imageStyles },
            toolbar: [
                {
                    name: 'imageStyle:dropdown',
                    title: '이미지 정렬',
                    items: [
                        'imageStyle:inline', 'imageStyle:block', 'imageStyle:side',
                        'imageStyle:alignLeft', 'imageStyle:alignCenter', 'imageStyle:alignRight'
                    ],
                    defaultItem: 'imageStyle:block'
                },
                '|', 'toggleImageCaption', 'imageTextAlternative'
            ]
        },
        extraPlugins: [AdminCkeditorResizePlugin.Plugin]
    };

    return {
        EDITOR_CONFIG: EDITOR_CONFIG
    };
});
