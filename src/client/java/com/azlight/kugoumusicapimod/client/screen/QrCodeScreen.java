package com.azlight.kugoumusicapimod.client.screen;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;

public class QrCodeScreen extends Screen {
    private static final Identifier QR_TEXTURE = Identifier.of("kugoumusicapimod", "qr_login");
    private final String base64Image;
    private int imageWidth = 0, imageHeight = 0;

    public QrCodeScreen(String base64Image) {
        super(Text.literal("酷狗扫码登录"));
        this.base64Image = base64Image;
    }

    @Override
    protected void init() {
        super.init();
        try {
            if (base64Image.contains(",")) {
                String pureBase64 = base64Image.split(",")[1];
                byte[] imgBytes = Base64.getDecoder().decode(pureBase64);
                BufferedImage bufferedImage = ImageIO.read(new ByteArrayInputStream(imgBytes));
                if (bufferedImage != null) {
                    this.imageWidth = bufferedImage.getWidth();
                    this.imageHeight = bufferedImage.getHeight();
                    NativeImage nativeImage = new NativeImage(bufferedImage.getWidth(), bufferedImage.getHeight(), false);
                    for (int y = 0; y < imageHeight; y++) {
                        for (int x = 0; x < imageWidth; x++) {
                            int argb = bufferedImage.getRGB(x, y);
                            nativeImage.setColor(x, y, argb);
                        }
                    }
                    MinecraftClient.getInstance().getTextureManager()
                            .registerTexture(QR_TEXTURE, new NativeImageBackedTexture(() -> "kugoumusicapimod:qr_login", nativeImage));
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            // 1. 绘制深色背景（避免调用 renderBackground）
            //context.fill(0, 0, width, height, 0xFF2B2B2B);
            // 2. 使用与 1.21.1 完全一致的布局算法绘制二维码
            if (imageWidth > 0 && imageHeight > 0) {
                MinecraftClient client = MinecraftClient.getInstance();
                // 为底部留出 80 像素的空白区域（这个区域不绘制任何内容，相当于预留文字空间）
                int bottomMargin = 80;
                // 由于不再绘制文字，textHeight 设为 0，但保留 bottomMargin 以确保二维码偏上
                int availableHeight = height - bottomMargin;
                int maxSize = Math.min(width, availableHeight) - 40;
                float scale = Math.min((float) maxSize / imageWidth, (float) maxSize / imageHeight);
                int drawWidth = (int) (imageWidth * scale);
                int drawHeight = (int) (imageHeight * scale);
                int x = (width - drawWidth) / 2;
                int y = (availableHeight - drawHeight) / 2 + 10;
                context.drawTexture(RenderPipelines.GUI_TEXTURED, QR_TEXTURE, x, y, 0, 0, drawWidth, drawHeight, drawWidth, drawHeight);
            }
        // 3. 绘制文字区域 (高35px 的背景条 + 两行文字)
        int textY = height - 45;
        int textHeight = 35;
        // 文字背景条
        //context.fill(0, textY - 5, width, textY + textHeight, 0xCC000000);
        // 绘制文字
        context.drawCenteredTextWithShadow(client.textRenderer, Text.literal("请使用酷狗App扫描二维码"), width / 2, textY, 0xFFFFFF);
        context.drawCenteredTextWithShadow(client.textRenderer, Text.literal("按 ESC 取消，二维码将在3分钟后过期，请尽快扫码"), width / 2, textY + 15, 0xFFFFFF);
        // 4. 最后调用 super.render (防止按钮等覆盖文字)
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        MinecraftClient.getInstance().getTextureManager().destroyTexture(QR_TEXTURE);
        super.close();
    }
}