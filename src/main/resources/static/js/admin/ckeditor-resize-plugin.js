(function (root, factory) {
    if (typeof module === 'object' && module.exports) {
        module.exports = factory();
    } else {
        root.AdminCkeditorResizePlugin = factory();
    }
})(typeof self !== 'undefined' ? self : this, function () {
    'use strict';

    // P13-T40: 관리자가 이미지 크기를 25%/50%/75%/원본(속성 없음) preset으로만 조절하는 자체 기능이다.
    // CKEditor 5 41.4.2 classic build(현재 CDN URL 그대로 유지, 공식 ImageResize plugin은 이 build에
    // 없음을 헤드리스로 확인됨)를 그대로 두고, extraPlugins로 추가하는 최소 plugin이다.
    //
    // model attribute 이름은 공식 ImageResizeEditing이 쓰는 이름과 동일한 "resizedWidth"를 쓴다
    // (GitHub ckeditor5 v41.4.2 태그의 packages/ckeditor5-image/src/imageresize/imageresizeediting.ts
    // 원본을 직접 확인: schema.extend('imageBlock', {allowAttributes:['resizedWidth','resizedHeight']})).
    // "width"라는 이름은 쓰지 않는다 - 헤드리스로 직접 확인한 결과 imageBlock 스키마에는 이미 v40+의
    // 원본(자연) 크기 보존용 "width"/"height" attribute가 별도로 존재하기 때문이다(리사이즈와 무관한
    // <img width height> 저장용, 이름 충돌 방지를 위해 다른 이름을 선택). resizedHeight는 등록하지
    // 않는다 - 세로 비율은 항상 auto로 유지하고 가로 %만 다룬다. 1차 범위는 block 이미지만 지원하므로
    // imageInline에는 이 attribute를 허용하지 않는다.
    var ALLOWED_WIDTHS = ['25', '50', '75'];

    // CKEditor의 predefined CDN build는 Plugin/Command 같은 프레임워크 베이스 클래스를 전역으로
    // 노출하지 않는다(번들러 없이는 import 불가 - 헤드리스로 실측 확인됨). 이 plugin은 그 클래스들을
    // extends하지 않는 순수 생성자 함수이며, editor.model/editor.conversion 같은 editor 인스턴스의
    // 공개(public) 런타임 API만 사용한다(extraPlugins에 등록 시 정상 초기화됨을 헤드리스로 확인).
    function ImageResizePresetPlugin(editor) {
        this.editor = editor;
    }

    ImageResizePresetPlugin.prototype.init = function () {
        var editor = this.editor;

        editor.model.schema.extend('imageBlock', { allowAttributes: ['resizedWidth'] });

        editor.conversion.for('downcast').add(function (dispatcher) {
            dispatcher.on('attribute:resizedWidth:imageBlock', function (evt, data, conversionApi) {
                var viewFigure = conversionApi.mapper.toViewElement(data.item);
                var viewWriter = conversionApi.writer;
                if (data.attributeNewValue) {
                    viewWriter.setStyle('width', data.attributeNewValue + '%', viewFigure);
                    viewWriter.addClass('image_resized', viewFigure);
                } else {
                    viewWriter.removeStyle('width', viewFigure);
                    viewWriter.removeClass('image_resized', viewFigure);
                }
            });
        });

        // P13-T40 §7: style width만 보고 upcast한다(image_resized class 유무는 보지 않음) - class는
        // downcast가 항상 canonical하게 다시 붙여주므로(위 참고), 클래스 없이 style만 있는 legacy/외부
        // 입력도 정상적으로 리사이즈 상태로 인식되고, 재출력 시 class가 자동으로 정규화되어 붙는다.
        // (헤드리스로 실측: <figure class="image" style="width:50%"> 입력 → resizedWidth='50' 모델
        // attribute로 정상 upcast, 재출력 시 image_resized class 자동 추가 확인.) 25/50/75 외 값은
        // 정규식이 매칭하지 않아 그냥 일반 figure로 취급된다(에러 없음, 조용히 무시).
        editor.conversion.for('upcast').attributeToAttribute({
            view: {
                name: 'figure',
                styles: { width: /^(25|50|75)%$/ }
            },
            model: {
                key: 'resizedWidth',
                value: function (viewElement) {
                    return viewElement.getStyle('width').replace('%', '');
                }
            }
        });
    };

    // P13-T40 §4/§5: Command 베이스 클래스를 extends할 수 없는 제약(위 참고)과 무관하게, 이 기능은
    // 버튼 4개뿐이라 Command architecture를 재현하는 것보다 이 평범한 함수가 더 단순하고 유지보수하기
    // 쉽다(헤드리스로 duck-typed 객체를 editor.commands.add에 등록해도 동작함을 확인했으나 채택하지
    // 않음 - Command 등록 자체는 이 기능의 DoD가 아님).
    //
    // UI(버튼 클릭)가 25/50/75만 보낸다는 것을 신뢰하지 않고, 이 함수 자체도 다시 한번 값을 검증한다
    // (Admin UI allowlist → 이 함수의 allowlist → 서버 HtmlSanitizer allowlist, 3단계 동일 정책).
    function setResize(editor, value) {
        if (value !== null && ALLOWED_WIDTHS.indexOf(value) === -1) {
            return false;
        }
        var element = editor.model.document.selection.getSelectedElement();
        if (!element || !element.is('element', 'imageBlock')) {
            return false;
        }
        editor.model.change(function (writer) {
            if (value === null) {
                writer.removeAttribute('resizedWidth', element);
            } else {
                writer.setAttribute('resizedWidth', value, element);
            }
        });
        return true;
    }

    function getSelectedImageBlock(editor) {
        var element = editor.model.document.selection.getSelectedElement();
        return (element && element.is('element', 'imageBlock')) ? element : null;
    }

    // container 안의 [data-resize-value] 버튼들을 editor 인스턴스와 연결한다. CKEditor UI 프레임워크
    // (ButtonView 등)를 전혀 쓰지 않고 admin 폼 자체의 평범한 <button> DOM만 조작한다.
    function bindResizeControls(editor, container) {
        if (!container) {
            return;
        }
        container.hidden = false;
        var buttons = Array.prototype.slice.call(container.querySelectorAll('[data-resize-value]'));

        function updateState() {
            var selectedImage = getSelectedImageBlock(editor);
            var currentWidth = selectedImage ? selectedImage.getAttribute('resizedWidth') : null;

            buttons.forEach(function (btn) {
                var btnValue = btn.getAttribute('data-resize-value') || null;
                btn.disabled = !selectedImage;
                var isActive = !!selectedImage && (
                    (btnValue === null && !currentWidth) || btnValue === currentWidth
                );
                btn.classList.toggle('active', isActive);
            });
        }

        buttons.forEach(function (btn) {
            btn.addEventListener('click', function () {
                var value = btn.getAttribute('data-resize-value') || null;
                setResize(editor, value);
                updateState();
            });
        });

        // selection 변경(이미지 선택/해제 등)과 내용 변경(우리 버튼 클릭 포함, undo/redo 포함) 둘 다
        // public 이벤트다 - 두 이벤트 모두 구독해야 undo/redo 후에도 버튼 active 상태가 정확히 갱신된다.
        editor.model.document.selection.on('change:range', updateState);
        editor.model.document.on('change:data', updateState);
        updateState();
    }

    return {
        Plugin: ImageResizePresetPlugin,
        ALLOWED_WIDTHS: ALLOWED_WIDTHS,
        setResize: setResize,
        bindResizeControls: bindResizeControls
    };
});
