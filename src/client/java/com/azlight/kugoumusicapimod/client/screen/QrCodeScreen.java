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
    private final Text title;  // 新增

    public QrCodeScreen(String base64Image, String title) {
        super(Text.literal(title));
        this.base64Image = base64Image;
        this.title = Text.literal(title);
    }

    // 兼容旧调用（酷狗扫码）
    public QrCodeScreen(String base64Image) {
        this(base64Image, "酷狗扫码登录");
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
        // 背景
        context.fill(0, 0, width, height, 0xFF2B2B2B);

        if (imageWidth > 0 && imageHeight > 0) {
            int maxSize = Math.min(width, height) - 80;
            float scale = Math.min((float) maxSize / imageWidth, (float) maxSize / imageHeight);
            int drawWidth = (int)(imageWidth * scale);
            int drawHeight = (int)(imageHeight * scale);
            int x = (width - drawWidth) / 2;
            int y = (height - drawHeight) / 2 - 20;

            context.drawTexture(RenderPipelines.GUI_TEXTURED, QR_TEXTURE, x, y, 0, 0, drawWidth, drawHeight, drawWidth, drawHeight);
        }

        // 底部文字
        context.fill(0, height - 50, width, height, 0x80000000);
        context.drawCenteredTextWithShadow(client.textRenderer, title, width / 2, height - 40, 0xFFFFFF);
        context.drawCenteredTextWithShadow(client.textRenderer, Text.literal("按 ESC 取消"), width / 2, height - 25, 0xAAAAAA);

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        MinecraftClient.getInstance().getTextureManager().destroyTexture(QR_TEXTURE);
        super.close();
    }
}