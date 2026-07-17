package com.azlight.kugoumusicapimod.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.TextureManager;
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

    // 新增支持自定义标题的构造器
    public QrCodeScreen(String base64Image, String title) {
        super(Text.literal(title));
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
                    // 1.21 兼容的构造器（仅接受 NativeImage）
                    TextureManager tm = MinecraftClient.getInstance().getTextureManager();
                    tm.registerTexture(QR_TEXTURE, new NativeImageBackedTexture(nativeImage));
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
        if (imageWidth > 0 && imageHeight > 0) {
            // 底部预留文字空间
            int bottomMargin = 60;
            int textHeight = this.textRenderer.fontHeight * 2 + 10; // 两行文字
            int availableHeight = height - bottomMargin - textHeight;
            int maxSize = Math.min(width, availableHeight) - 40;
            float scale = Math.min((float) maxSize / imageWidth, (float) maxSize / imageHeight);
            int drawWidth = (int) (imageWidth * scale);
            int drawHeight = (int) (imageHeight * scale);
            int x = (width - drawWidth) / 2;
            int y = (availableHeight - drawHeight) / 2 + 10;

            RenderSystem.setShaderTexture(0, QR_TEXTURE);
            context.drawTexture(QR_TEXTURE, x, y, 0, 0, drawWidth, drawHeight, drawWidth, drawHeight);
        }

        // 底部文字，半透明背景
        int textY = height - 45;
        context.fill(0, textY - 5, width, height, 0x80000000);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("请使用手机扫码登录"), this.width / 2, textY, 0xFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("按 ESC 取消，二维码3分钟后过期"), this.width / 2, textY + 15, 0xAAAAAA);
    }

    @Override
    public void close() {
        TextureManager tm = MinecraftClient.getInstance().getTextureManager();
        if (tm != null) tm.destroyTexture(QR_TEXTURE);
        super.close();
    }
}