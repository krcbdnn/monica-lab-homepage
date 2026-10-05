package com.monicalab.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.monicalab.support.AbstractIntegrationTest;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

// P15-T7B: 발주처 공식 로고에서 만든 branding 파생 asset(favicon/apple-touch-icon/기본 OG 이미지)이 static 리소스로
// 인증 없이 제공되고, 실제로 decode 가능한 정확한 크기의 이미지인지 확인한다.
@AutoConfigureMockMvc
class BrandAssetTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void faviconIsMultiSizeIcoWithDecodablePngEntries() throws Exception {
        String contentType = mockMvc.perform(get("/favicon.ico"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentType();
        assertThat(contentType).isIn("image/x-icon", "image/vnd.microsoft.icon");

        byte[] ico = mockMvc.perform(get("/favicon.ico")).andReturn().getResponse().getContentAsByteArray();
        ByteBuffer header = ByteBuffer.wrap(ico).order(ByteOrder.LITTLE_ENDIAN);
        assertThat(header.getShort(0)).isZero();
        assertThat(header.getShort(2)).isEqualTo((short) 1);
        int count = header.getShort(4);

        List<Integer> sizes = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int entry = 6 + 16 * i;
            int length = header.getInt(entry + 8);
            int offset = header.getInt(entry + 12);
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(ico, offset, length));
            assertThat(image).as("ICO entry %d decodable", i).isNotNull();
            assertThat(image.getWidth()).isEqualTo(image.getHeight()).isEqualTo(Byte.toUnsignedInt(ico[entry]));
            sizes.add(image.getWidth());
        }
        assertThat(sizes).containsExactly(16, 32, 48);
    }

    @Test
    void appleTouchIconIs180SquarePng() throws Exception {
        BufferedImage image = readPng("/apple-touch-icon.png");

        assertThat(image.getWidth()).isEqualTo(180);
        assertThat(image.getHeight()).isEqualTo(180);
    }

    @Test
    void defaultOgImageIs1200x630OpaquePng() throws Exception {
        BufferedImage image = readPng("/images/og-default.png");

        assertThat(image.getWidth()).isEqualTo(1200);
        assertThat(image.getHeight()).isEqualTo(630);
        // SNS 미리보기에서 투명 영역이 검게 보이지 않도록 불투명(흰 배경) 이미지다.
        assertThat(image.getColorModel().hasAlpha()).isFalse();
    }

    private BufferedImage readPng(String path) throws Exception {
        byte[] body = mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.IMAGE_PNG))
                .andReturn().getResponse().getContentAsByteArray();

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(body));
        assertThat(image).as("%s decodable", path).isNotNull();
        return image;
    }
}
