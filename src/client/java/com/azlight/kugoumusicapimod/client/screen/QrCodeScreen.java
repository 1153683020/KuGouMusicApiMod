package com.azlight.kugoumusicapimod.client.screen;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gl.RenderPipelines;
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

    // 默认标题构造器
    public QrCodeScreen(String base64Image) {
        this(base64Image, "酷狗扫码登录");
    }

    // 带标题的构造器
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
                    // 1.21.11 专用构造器
                    TextureManager tm = MinecraftClient.getInstance().getTextureManager();
                    tm.registerTexture(QR_TEXTURE, new NativeImageBackedTexture(() -> "kugoumusicapimod:qr_login", nativeImage));
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // 深色背景
        context.fill(0, 0, width, height, 0xC0101010);

        if (imageWidth > 0 && imageHeight > 0) {
            // 预留底部文字区域
            int textAreaHeight = 60;
            int availableHeight = height - textAreaHeight;
            int maxWidth = width - 20;
            float scale = Math.min((float) maxWidth / imageWidth, (float) availableHeight / imageHeight);
            int drawWidth = (int)(imageWidth * scale);
            int drawHeight = (int)(imageHeight * scale);
            int x = (width - drawWidth) / 2;
            int y = (availableHeight - drawHeight) / 2 + 10;

            // 使用 RenderPipelines.GUI_TEXTURED 绘制，1.21.11 必须使用这种方式
            context.drawTexture(RenderPipelines.GUI_TEXTURED, QR_TEXTURE, x, y, 0.0f, 0.0f, drawWidth, drawHeight, drawWidth, drawHeight);
        }

        // 底部文字区域
        int textY = height - 45;
        context.fill(0, textY - 5, width, height, 0x80000000); // 半透明黑色背景
        context.drawCenteredTextWithShadow(client.textRenderer, Text.literal("请使用手机扫码登录"), width / 2, textY, 0xFFFFFF);
        context.drawCenteredTextWithShadow(client.textRenderer, Text.literal("按 ESC 取消，二维码3分钟后过期"), width / 2, textY + 15, 0xAAAAAA);

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        TextureManager tm = MinecraftClient.getInstance().getTextureManager();
        if (tm != null) tm.destroyTexture(QR_TEXTURE);
        super.close();
    }
}