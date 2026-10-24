package com.com.boffo.addon.modules;

import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import java.util.Random;

public class BoffoAntiVanishModule extends Module {
    // Greift direkt auf dein hochgeladenes Donut-Bild in den Texturen zu
    private static final Identifier DONUT_TEX = new Identifier("boffo-addon", "textures/blue_donut.png");
    private final Minecraft mc = Minecraft.getInstance();
    private final Random random = new Random();

    private int requiredRelogs = 0;
    private int currentRelogCount = 0;
    private boolean wasBelowMinusOne = false;

    private boolean isAnimating = false;
    private long animationStartTime = 0;

    public BoffoAntiVanishModule(Category category) {
        super(category, "Boffo-AntiVanish", "Zeigt die TikTok Anubis-Animation nach 15-30 Relogs unter Y -1.");
        resetRandomCounter();
    }

    @Override
    public void onActivate() {
        resetRandomCounter(); // Setzt bei jedem Modul-Start im Menue alles auf 0 zurueck
    }

    private void resetRandomCounter() {
        this.requiredRelogs = random.nextInt(16) + 15; // Random Zahl von 15 bis 30
        this.currentRelogCount = 0; // Zaehler startet garantiert bei 0
        this.wasBelowMinusOne = false;
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        if (mc.player != null) {
            this.wasBelowMinusOne = mc.player.getY() < -1.0;
        }
    }

    @EventHandler
    private void onGameJoined(GameJoinedEvent event) {
        if (mc.getCurrentServerEntry() == null || !mc.getCurrentServerEntry().address.toLowerCase().contains("donutsmp.net")) {
            return;
        }

        if (wasBelowMinusOne) {
            currentRelogCount++;
            wasBelowMinusOne = false;

            if (currentRelogCount >= requiredRelogs) {
                this.animationStartTime = System.currentTimeMillis();
                this.isAnimating = true;
                resetRandomCounter(); // Nach der Animation wird direkt wieder auf 0 gesetzt
            }
        }
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (!isAnimating || mc.world == null) return;

        long elapsed = System.currentTimeMillis() - animationStartTime;
        
        if (elapsed > 7800) {
            isAnimating = false;
            return;
        }

        DrawContext context = event.drawContext;
        int screenWidth = mc.getWindow().getScaledWidth();
        int screenHeight = mc.getWindow().getScaledHeight();

        // PHASE 1: DER DONUT (0ms - 4800ms)
        if (elapsed <= 4800) {
            int donutSize = 250;
            int targetX = (screenWidth - donutSize) / 2;
            int targetY = (screenHeight - donutSize) / 2;

            int currentX = targetX;
            int currentY = -donutSize; 
            float scale = 1.0f;
            float rotation = 0.0f;

            if (elapsed < 576) { 
                float t = elapsed / 576f;
                currentY = (int) (-donutSize + (targetY - (-donutSize)) * t);
                scale = 0.6f + (1.12f - 0.6f) * t; 
            } 
            else if (elapsed < 768) { 
                currentY = targetY;
                float t = (elapsed - 576) / 192f;
                scale = 1.12f - (0.12f * t);
            } 
            else if (elapsed < 1584) { // Der optimierte, schnelle Shake
                currentY = targetY;
                int shakeCycle = (int) ((elapsed - 768) / 96); 
                currentX = (shakeCycle % 2 == 0) ? targetX - 10 : targetX + 10;
            } 
            else if (elapsed < 2640) { 
                currentX = targetX;
                currentY = targetY;
            } 
            else { 
                currentX = targetX;
                float t = (elapsed - 2640) / 2160f; 
                currentY = (int) (targetY + (screenHeight + donutSize - targetY) * t);
                scale = 1.0f - (0.25f * t);
                rotation = t * 360f; 
            }

            context.getMatrices().push();
            context.getMatrices().translate(currentX + (donutSize / 2f), currentY + (donutSize / 2f), 0);
            context.getMatrices().scale(scale, scale, 1);
            if (rotation != 0.0f) {
                context.getMatrices().multiply(net.minecraft.client.util.math.RotationAxis.POSITIVE_Z.rotationDegrees(rotation));
            }
            context.getMatrices().translate(-(donutSize / 2f), -(donutSize / 2f), 0);

            // Zeichnet das Bild auf den Bildschirm
            context.drawTexture(DONUT_TEX, 0, 0, 0, 0, donutSize, donutSize, donutSize, donutSize);

            String text = "BOFFO";
            int textWidth = mc.textRenderer.getWidth(text);
            context.getMatrices().push();
            float textScale = 2.5f; 
            float textX = (donutSize - (textWidth * textScale)) / 2f / textScale;
            float textY = (donutSize - (mc.textRenderer.fontHeight * textScale)) / 2f / textScale;
            context.getMatrices().scale(textScale, textScale, 1);
            context.drawTextWithShadow(mc.textRenderer, text, (int)textX + 2, (int)textY + 2, 0x00FFFF);
            context.drawTextWithShadow(mc.textRenderer, text, (int)textX, (int)textY, 0xFFFFFF);
            context.getMatrices().pop();
            
            context.getMatrices().pop();
        } 
        // PHASE 2: FLÜSSIG VERBLASSENDER WARN-TEXT (4800ms - 7800ms)
        else {
            long textElapsed = elapsed - 4800;
            String warnText = "WATCH OUT BOFFO ADDON USER YOU'RE ON /SUS NOW";
            int textWidth = mc.textRenderer.getWidth(warnText);
            
            float textScale = 2.0f;
            float targetX = (screenWidth - (textWidth * textScale)) / 2f;
            float targetY = (screenHeight - (mc.textRenderer.fontHeight * textScale)) / 2f;
            
            float alpha = 1.0f;
            float pulseScale = 1.0f;

            if (textElapsed < 500) { 
                float t = textElapsed / 500f;
                alpha = t;
                pulseScale = 0.8f + (0.2f * t);
            } else if (textElapsed > 2200) { 
                float t = (textElapsed - 2200) / 800f;
                alpha = 1.0f - t;
            }

            if (alpha < 0.0f) alpha = 0.0f;
            if (alpha > 1.0f) alpha = 1.0f;

            int alphaBits = ((int) (alpha * 255)) << 24;
            int textColor = 0xFFFFFF | alphaBits;
            int glowColor = 0xFF0055 | alphaBits; 

            context.getMatrices().push();
            context.getMatrices().translate(screenWidth / 2f, screenHeight / 2f, 0);
            context.getMatrices().scale(pulseScale, pulseScale, 1);
            context.getMatrices().translate(-screenWidth / 2f, -screenHeight / 2f, 0);
            context.getMatrices().translate(targetX, targetY, 0);
            context.getMatrices().scale(textScale, textScale, 1);

            context.drawTextWithShadow(mc.textRenderer, warnText, 2, 2, glowColor);
            context.drawTextWithShadow(mc.textRenderer, warnText, 0, 0, textColor);
            context.getMatrices().pop();
        }
    }
}
