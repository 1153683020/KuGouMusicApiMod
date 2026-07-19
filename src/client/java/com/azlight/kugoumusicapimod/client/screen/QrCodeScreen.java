package com.azlight.kugoumusicapimod.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.lang.reflect.Method;
import java.util.Base64;
import java.util.function.Function;

public class QrCodeScreen extends Screen {
    private static final Identifier QR_TEXTURE = Identifier.of("kugoumusicapimod", "qr_login");
    private final String base64Image;
    private int imageWidth = 0, imageHeight = 0;
    private int textureTotalSize = 1;
    private boolean textureReady = false;

    private static final Function<Identifier, RenderLayer> LAYER_FUNCTION =
            RenderLayer::getGuiTextured;

    public QrCodeScreen(String base64Image) {
        this(base64Image, "酷狗扫码登录");
    }

    public QrCodeScreen(String base64Image, String title) {
        super(Text.literal(title));
        this.base64Image = base64Image;
    }

    @Override
    protected void init() {
        super.init();
        loadTexture();
    }

    private void loadTexture() {
        try {

            if (base64Image == null || base64Image.isEmpty()) {
                System.err.println("[QrCodeScreen] base64Image 为空");
                return;
            }

            // ★★★ 兼容性解析：如果没有逗号，直接使用；否则取逗号后部分 ★★★
            String pureBase64;
            if (base64Image.contains(",")) {
                pureBase64 = base64Image.split(",")[1];

            } else {
                pureBase64 = base64Image;
            }

            // 清理可能的空白字符（如换行、空格）
            pureBase64 = pureBase64.replaceAll("\\s+", "");

            byte[] imgBytes = Base64.getDecoder().decode(pureBase64);

            BufferedImage srcImage = ImageIO.read(new ByteArrayInputStream(imgBytes));
            if (srcImage == null) {
                System.err.println("[QrCodeScreen] ImageIO.read 返回 null，可能图片格式不支持或数据损坏");
                return;
            }

            this.imageWidth = srcImage.getWidth();
            this.imageHeight = srcImage.getHeight();

            textureTotalSize = 1;
            while (textureTotalSize < Math.max(imageWidth, imageHeight)) {
                textureTotalSize <<= 1;
            }

            // ★★★ 使用反射调用 private setColor，并转换为 ABGR ★★★
            NativeImage nativeImage = new NativeImage(imageWidth, imageHeight, false);
            Method setColorMethod = NativeImage.class.getDeclaredMethod("setColor", int.class, int.class, int.class);
            setColorMethod.setAccessible(true);

            for (int y = 0; y < imageHeight; y++) {
                for (int x = 0; x < imageWidth; x++) {
                    int argb = srcImage.getRGB(x, y);
                    int a = (argb >> 24) & 0xFF;
                    int r = (argb >> 16) & 0xFF;
                    int g = (argb >> 8) & 0xFF;
                    int b = argb & 0xFF;
                    int abgr = (a << 24) | (b << 16) | (g << 8) | r;
                    setColorMethod.invoke(nativeImage, x, y, abgr);
                }
            }

            // ★★★ 注册纹理并同步加载 ★★★
            TextureManager tm = MinecraftClient.getInstance().getTextureManager();
            NativeImageBackedTexture texture = new NativeImageBackedTexture(nativeImage);
            tm.registerTexture(QR_TEXTURE, texture);

            // 强制同步加载纹理数据
            texture.load(MinecraftClient.getInstance().getResourceManager());

            // 设置过滤为最近邻
            texture.setFilter(false, false);

            textureReady = true;

        } catch (Exception e) {
            System.err.println("[QrCodeScreen] loadTexture 发生异常: " + e.getClass().getName() + " - " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xC0101010);

        if (!textureReady || imageWidth == 0 || imageHeight == 0) {
            super.render(context, mouseX, mouseY, delta);
            return;
        }

        // 缩放计算
        int bottomMargin = 60;
        int textHeight = this.textRenderer.fontHeight * 2 + 10;
        int availableHeight = height - bottomMargin - textHeight;
        int maxSize = Math.min(width, availableHeight) - 40;
        float scale = Math.min((float) maxSize / imageWidth, (float) maxSize / imageHeight);
        int drawWidth = (int) (imageWidth * scale);
        int drawHeight = (int) (imageHeight * scale);
        int x = (width - drawWidth) / 2;
        int y = (availableHeight - drawHeight) / 2 + 10;

        // 禁用混合
        RenderSystem.disableBlend();

        // ★★★ 关键：使用 drawWidth/drawHeight 作为纹理总尺寸 ★★★
        context.drawTexture(
                LAYER_FUNCTION,
                QR_TEXTURE,
                x, y,
                0.0f, 0.0f,
                drawWidth, drawHeight,
                drawWidth, drawHeight
        );

        RenderSystem.enableBlend();

        // 底部文字...
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