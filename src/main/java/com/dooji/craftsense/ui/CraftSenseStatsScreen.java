package com.dooji.craftsense.ui;

import com.dooji.craftsense.CraftSenseClient;
import com.dooji.craftsense.manager.CategoryHabitsTracker;
import com.dooji.craftsense.manager.CategoryManager;
import com.dooji.craftsense.omnilib.OmniButton;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import com.mojang.blaze3d.platform.NativeImage;

import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.awt.*;
import java.util.*;
import java.util.List;

public class CraftSenseStatsScreen extends Screen {
    private final CategoryHabitsTracker tracker = CategoryHabitsTracker.getInstance();

    private Map<String, Integer> categoryTotals;
    private Map<String, List<Map.Entry<String, Integer>>> categoryItemsMap;
    private final Set<String> hiddenCategories = new HashSet<>();
    private List<String> visibleCategories;
    private final Map<String, Integer> categoryColors = new HashMap<>();

    private NativeImage pieImage;
    private DynamicTexture pieTexture;
    private ResourceLocation pieTextureId;
    private int pieTextureWidth, pieTextureHeight;
    private boolean pieNeedsUpdate = true;

    private OmniButton legendPrevBtn, legendNextBtn, closeButton;
    private int legendPage = 0;
    private int legendPages = 1;

    public CraftSenseStatsScreen() {
        super(Component.translatable("screen.craftsense.stats"));

        categoryTotals = new LinkedHashMap<>();
        tracker.categoryCraftCount.entrySet()
                .stream()
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .forEachOrdered(entry -> categoryTotals.put(entry.getKey(), entry.getValue()));
        categoryItemsMap = createCategoryItemsMap();
    }

    private Map<String, List<Map.Entry<String, Integer>>> createCategoryItemsMap() {
        Map<String, List<Map.Entry<String, Integer>>> categoryMap = new HashMap<>();

        for (String category : tracker.categoryCraftCount.keySet()) {
            List<Map.Entry<String, Integer>> items = new ArrayList<>();

            for (Map.Entry<String, Integer> entry : tracker.itemCraftCount.entrySet()) {
                String descriptionId = entry.getKey();
                Optional<Item> optionalItem = BuiltInRegistries.ITEM.stream()
                        .filter(item -> item.getDescriptionId().equals(descriptionId))
                        .findFirst();

                if (optionalItem.isPresent() && category.equals(CategoryManager.getCategory(optionalItem.get()))) {
                    items.add(entry);
                }
            }

            items.sort((e1, e2) -> Integer.compare(e2.getValue(), e1.getValue()));
            categoryMap.put(category, items);
        }

        return categoryMap;
    }

    @Override
    protected void init() {
        recalculateStats();

        closeButton = CraftSenseClient.createOmniButton(
                this.width / 2 - 40, this.height - 40, 80, 20,
                Component.translatable("screen.craftsense.stats.done"),
                0x99000000, 0xBB000000, 0xFFFFFFFF, 0xFFEFEFEF,
                this::onClose
        );
        this.addRenderableWidget(closeButton);

        if (!categoryTotals.isEmpty()) {
            legendPrevBtn = CraftSenseClient.createOmniButton(0, 0, 20, 20,
                    Component.literal("<"), 0x99000000, 0xBB000000, 0xFFFFFFFF, 0xFFEFEFEF,
                    () -> { if (legendPage > 0) legendPage--; });
            this.addRenderableWidget(legendPrevBtn);

            legendNextBtn = CraftSenseClient.createOmniButton(0, 0, 20, 20,
                    Component.literal(">"), 0x99000000, 0xBB000000, 0xFFFFFFFF, 0xFFEFEFEF,
                    () -> { if (legendPage < legendPages - 1) legendPage++; });
            this.addRenderableWidget(legendNextBtn);
        }
    }

    private void recalculateStats() {
        categoryTotals = new LinkedHashMap<>();
        tracker.categoryCraftCount.entrySet()
                .stream()
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .forEachOrdered(entry -> categoryTotals.put(entry.getKey(), entry.getValue()));
        categoryItemsMap = new HashMap<>();

        for (String category : tracker.categoryCraftCount.keySet()) {
            List<Map.Entry<String, Integer>> items = new ArrayList<>();

            for (Map.Entry<String, Integer> entry : tracker.itemCraftCount.entrySet()) {
                String descriptionId = entry.getKey();
                Optional<Item> optionalItem = BuiltInRegistries.ITEM.stream()
                        .filter(item -> item.getDescriptionId().equals(descriptionId))
                        .findFirst();

                if (optionalItem.isPresent() && category.equals(CategoryManager.getCategory(optionalItem.get()))) {
                    items.add(entry);
                }
            }

            items.sort((e1, e2) -> Integer.compare(e2.getValue(), e1.getValue()));
            categoryItemsMap.put(category, items);
        }

        visibleCategories = new ArrayList<>(categoryTotals.keySet());

        for (String category : categoryTotals.keySet()) {
            if (!categoryColors.containsKey(category)) {
                categoryColors.put(category, generateColorFromString(category));
            }
        }

        int catsPerPage = 8;
        legendPages = (int) Math.ceil(visibleCategories.size() / (double) catsPerPage);

        if (legendPage >= legendPages && legendPages > 0) legendPage = legendPages - 1;
        pieNeedsUpdate = true;
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredString(this.font, this.title, this.width / 2, 15, 0xFFFFFF);

        if (pieNeedsUpdate) {
            generatePieTexture();
            pieNeedsUpdate = false;
        }

        int titleBottom = 30;
        int doneButtonTop = this.height - 40;
        int areaHeight = doneButtonTop - titleBottom;
        int areaCenterY = titleBottom + areaHeight / 2;

        renderPieChart(context, mouseX, mouseY, areaCenterY);
        renderLegend(context, areaCenterY);

        if (categoryTotals.isEmpty()) {
            context.drawCenteredString(this.font, Component.translatable("screen.craftsense.no_history"), this.width / 2, areaCenterY, 0xAAAAAA);
        }
    }

    private void renderPieChart(GuiGraphics context, int mouseX, int mouseY, int areaCenterY) {
        if (pieTexture == null) return;

        int drawY = areaCenterY - pieTextureHeight / 2;
        int totalWidth = pieTextureWidth + 100;
        int cx = (this.width - totalWidth) / 2;
        context.blit(pieTextureId, cx, drawY, 0, 0, pieTextureWidth, pieTextureHeight, pieTextureWidth, pieTextureHeight);

        int radius = Math.min(100, (pieTextureHeight - 40) / 2);
        int centerX = cx + (radius + 20);
        int centerY = drawY + (radius + 20);
        handlePieHover(context, mouseX, mouseY, centerX, centerY, radius);
    }

    private void renderLegend(GuiGraphics context, int areaCenterY) {
        if (categoryTotals.isEmpty()) return;

        int catsPerPage = 8;
        int startIndex = legendPage * catsPerPage;
        int endIndex = Math.min(visibleCategories.size(), startIndex + catsPerPage);

        String pageText = (legendPage + 1) + "/" + legendPages;

        int totalWidth = pieTextureWidth + 100;
        int cx = (this.width - totalWidth) / 2 + pieTextureWidth + 10;
        int legendWidth = 80;

        int legendBoxHeight = 12 * catsPerPage;
        int paginationHeight = 20;
        int totalLegendHeight = legendBoxHeight + paginationHeight + 5;
        int legendTop = areaCenterY - (totalLegendHeight / 2) + paginationHeight + 5;
        int paginationY = legendTop - paginationHeight - 5;

        legendPrevBtn.setX(cx);
        legendNextBtn.setX(cx + legendWidth - 20);
        legendPrevBtn.setY(paginationY);
        legendNextBtn.setY(paginationY);

        int midX = cx + legendWidth / 2;
        context.drawCenteredString(this.font, pageText, midX, paginationY + 5, 0xFFFFFF);

        for (int i = startIndex; i < endIndex; i++) {
            String category = visibleCategories.get(i);
            boolean hidden = hiddenCategories.contains(category);

            int color = getCategoryColor(category);
            if (hidden) {
                color = darken(color, 0.4f);
            }

            int lineY = legendTop + (i - startIndex) * 12;
            context.fill(cx, lineY, cx + 10, lineY + 10, color);

            String displayStr = category.substring(0, 1).toUpperCase() + category.substring(1).toLowerCase();
            Component display = hidden
                    ? Component.literal(displayStr).withStyle(style -> style.withItalic(true).withColor(0xFFAAAAAA))
                    : Component.literal(displayStr).withStyle(style -> style.withColor(0xFFFFFFFF));

            context.enableScissor(cx + 12, lineY + 2, cx + legendWidth, lineY + 12);
            renderScrollableText(context, this.font, display, cx + 12, lineY + 2, cx + legendWidth, lineY + 12, 0xFFFFFF);
            context.disableScissor();
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        super.mouseClicked(mouseX, mouseY, button);

        int catsPerPage = 8;
        int startIndex = legendPage * catsPerPage;
        int endIndex = Math.min(visibleCategories.size(), startIndex + catsPerPage);

        int totalWidth = pieTextureWidth + 100;
        int cx = (this.width - totalWidth) / 2 + pieTextureWidth + 10;
        int legendWidth = 80;

        int legendBoxHeight = 12 * catsPerPage;
        int paginationHeight = 20;
        int totalLegendHeight = legendBoxHeight + paginationHeight + 5;
        int legendTop = (this.height - totalLegendHeight) / 2 + paginationHeight + 5;

        for (int i = startIndex; i < endIndex; i++) {
            int lineY = legendTop + (i - startIndex) * 12;

            if (mouseX >= cx && mouseX < cx + legendWidth && mouseY >= lineY && mouseY < lineY + 10) {
                String category = visibleCategories.get(i);
                if (hiddenCategories.contains(category)) hiddenCategories.remove(category);
                else hiddenCategories.add(category);

                pieNeedsUpdate = true;
                break;
            }
        }

        return true;
    }

    private int getCategoryColor(String category) {
        return categoryColors.getOrDefault(category, generateColorFromString(category));
    }

    private int generateColorFromString(String str) {
        int hash = str.hashCode();
        float hue = (Math.abs(hash) % 360) / 360.0f;
        int rgb = Color.HSBtoRGB(hue, 0.7f, 0.9f);
        return 0xFF000000 | (rgb & 0x00FFFFFF);
    }

    private void generatePieTexture() {
        if (pieTexture != null) {
            Minecraft.getInstance().getTextureManager().release(pieTextureId);
            pieTexture.close();
            pieTexture = null;
        }

        int radius = 80;
        int margin = 20;
        pieTextureWidth = radius * 2 + margin * 2;
        pieTextureHeight = radius * 2 + margin * 2;

        pieImage = new NativeImage(NativeImage.Format.RGBA, pieTextureWidth, pieTextureHeight, false);
        pieImage.fillRect(0, 0, pieTextureWidth, pieTextureHeight, 0x00000000);

        Matrix4f transform = new Matrix4f()
                .identity()
                .translate(radius + margin, radius + margin, 0)
                .rotate(new org.joml.AxisAngle4f((float) Math.toRadians(45f), 1, 0, 0))
                .translate(-(radius + margin), -(radius + margin), 0);

        int total = 0;
        for (var e : categoryTotals.entrySet()) {
            if (!hiddenCategories.contains(e.getKey())) total += e.getValue();
        }

        List<SliceInfo> slices = new ArrayList<>();
        float accumulatedAngle = 0f;

        for (var e : categoryTotals.entrySet()) {
            if (hiddenCategories.contains(e.getKey())) continue;
            float fraction = (total == 0) ? 0 : (e.getValue() / (float) total);
            float angle = 360f * fraction;
            slices.add(new SliceInfo(e.getKey(), accumulatedAngle, accumulatedAngle + angle));
            accumulatedAngle += angle;
        }

        if (slices.size() == 1) {
            fillCircleWithThickness(pieImage, radius + margin, radius + margin, radius, getCategoryColor(slices.getFirst().category), transform, 6);
        } else {
            int thickness = 6;
            int centerX = radius + margin;
            int centerY = radius + margin;

            for (int px = centerX - radius; px <= centerX + radius; px++) {
                float dx = px - centerX;
                for (int py = centerY - radius; py <= centerY + radius; py++) {
                    float dy = py - centerY;

                    if (dx * dx + dy * dy <= radius * radius) {
                        float angle = (float) Math.toDegrees(Math.atan2(dy, dx));
                        if (angle < 0) angle += 360f;

                        int colorArgb = 0x00000000;
                        for (SliceInfo slice : slices) {
                            if (isAngleInSlice(angle, slice.startAngle, slice.endAngle)) {
                                colorArgb = getCategoryColor(slice.category);
                                break;
                            }
                        }

                        if (colorArgb == 0x00000000) continue;
                        int color = abgrFromArgb(colorArgb);

                        Vector4f pos = new Vector4f(px, py, 0, 1).mul(transform);
                        int rx = (int) pos.x;
                        int ry = (int) pos.y;

                        if (rx >= 0 && rx < pieImage.getWidth() && ry >= 0 && ry < pieImage.getHeight()) {
                            pieImage.setPixelRGBA(rx, ry, color);
                        }

                        int darkColor = abgrFromArgb(darken(colorArgb, 0.5f));
                        for (int t = 1; t <= thickness; t++) {
                            int wallY = ry + t;
                            if (wallY >= 0 && wallY < pieImage.getHeight()) {
                                pieImage.setPixelRGBA(rx, wallY, darkColor);
                            }
                        }
                    }
                }
            }
        }

        pieTextureId = ResourceLocation.fromNamespaceAndPath("craftsense", "craftsense_pie");
        pieTexture = new DynamicTexture(pieImage);
        Minecraft.getInstance().getTextureManager().register(pieTextureId, pieTexture);
    }

    private int abgrFromArgb(int argb) {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8)  & 0xFF;
        int b = argb & 0xFF;
        return (a << 24) | (b << 16) | (g << 8) | r;
    }

    private void fillCircleWithThickness(NativeImage image, int centerX, int centerY, int radius, int colorArgb, Matrix4f transform, int thickness) {
        for (int px = centerX - radius; px <= centerX + radius; px++) {
            float dx = px - centerX;
            for (int py = centerY - radius; py <= centerY + radius; py++) {
                float dy = py - centerY;
                if (dx * dx + dy * dy <= radius * radius) {
                    Vector4f pos = new Vector4f(px, py, 0, 1).mul(transform);
                    int rx = (int) pos.x;
                    int ry = (int) pos.y;

                    int color = abgrFromArgb(colorArgb);
                    if (rx >= 0 && rx < image.getWidth() && ry >= 0 && ry < image.getHeight()) {
                        image.setPixelRGBA(rx, ry, color);
                    }

                    int darkColor = abgrFromArgb(darken(colorArgb, 0.5f));
                    for (int t = 1; t <= thickness; t++) {
                        int wallY = ry + t;
                        if (wallY >= 0 && wallY < image.getHeight()) {
                            image.setPixelRGBA(rx, wallY, darkColor);
                        }
                    }
                }
            }
        }
    }

    private boolean isAngleInSlice(float angle, float startDeg, float endDeg) {
        startDeg %= 360; if (startDeg < 0) startDeg += 360;
        endDeg %= 360; if (endDeg < 0) endDeg += 360;

        if (endDeg >= startDeg) {
            return angle >= startDeg && angle <= endDeg;
        } else {
            return (angle >= startDeg && angle < 360) || (angle >= 0 && angle <= endDeg);
        }
    }

    private int darken(int color, float factor) {
        int a = (color >>> 24) & 0xFF;
        int r = (color >>> 16) & 0xFF;
        int g = (color >>> 8)  & 0xFF;
        int b = color & 0xFF;
        return (a << 24) | ((int)(r * factor) << 16) | ((int)(g * factor) << 8) | (int)(b * factor);
    }

    private void handlePieHover(GuiGraphics context, int mouseX, int mouseY, int cx, int cy, int radius) {
        float dx = mouseX - cx;
        float dy = mouseY - cy;

        if (dx * dx + dy * dy <= radius * radius) {
            List<Map.Entry<String, Integer>> visibleSlices = categoryTotals.entrySet()
                    .stream()
                    .filter(e -> !hiddenCategories.contains(e.getKey()))
                    .toList();

            if (visibleSlices.size() == 1) {
                showTooltipForCategory(context, visibleSlices.getFirst().getKey(), mouseX, mouseY);
            } else {
                float angle = (float) Math.toDegrees(Math.atan2(dy, dx));
                if (angle < 0) angle += 360;

                int total = 0;
                List<Map.Entry<String, Integer>> slices = new ArrayList<>();
                for (var e : categoryTotals.entrySet()) {
                    if (!hiddenCategories.contains(e.getKey())) {
                        total += e.getValue();
                        slices.add(e);
                    }
                }

                float accum = 0f;
                for (var slice : slices) {
                    float fraction = total == 0 ? 0 : (slice.getValue() / (float) total);
                    float sliceAngle = 360 * fraction;
                    if (isAngleInSlice(angle, accum, accum + sliceAngle)) {
                        showTooltipForCategory(context, slice.getKey(), mouseX, mouseY);
                        break;
                    }
                    accum += sliceAngle;
                }
            }
        }
    }

    private void showTooltipForCategory(GuiGraphics context, String category, int mouseX, int mouseY) {
        var items = categoryItemsMap.getOrDefault(category, List.of());

        List<ItemStack> stacks = new ArrayList<>();
        List<Component> lines = new ArrayList<>();

        for (var itE : items) {
            BuiltInRegistries.ITEM.stream()
                    .filter(it -> it.getDescriptionId().equals(itE.getKey()))
                    .findFirst()
                    .ifPresent(it -> {
                        stacks.add(it.getDefaultInstance());
                        lines.add(Component.literal(it.getName(it.getDefaultInstance()).getString() + " - " + formatNumberShorthand(itE.getValue())));
                    });
        }

        CraftSenseClient.showTooltip(context, this.font, category, stacks,
                lines.isEmpty() ? List.of(Component.translatable("tooltip.craftsense.no_items_found")) : lines,
                0x99000000, null, 0xFFFFFF, null, mouseX + 10, mouseY + 10);
    }

    private String formatNumberShorthand(int number) {
        if (number >= 1_000_000_000) return String.format("%.1fB", number / 1_000_000_000.0);
        if (number >= 1_000_000) return String.format("%.1fM", number / 1_000_000.0);
        if (number >= 1_000) return String.format("%.1fK", number / 1_000.0);
        return String.valueOf(number);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            this.onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private static void renderScrollableText(GuiGraphics context, net.minecraft.client.gui.Font font, Component text, int startX, int startY, int endX, int endY, int color) {
        int textWidth = font.width(text);
        int availableWidth = endX - startX;
        int pauseTime = 1000;
        double scrollSpeed = 5.0;

        if (textWidth > availableWidth) {
            int overflowWidth = textWidth - availableWidth;
            long currentTime = System.currentTimeMillis();
            long totalCycleTime = (long) ((overflowWidth / scrollSpeed) * 1000) * 2 + pauseTime * 2L;
            long timeInCycle = currentTime % totalCycleTime;

            int scrollOffset;
            if (timeInCycle < pauseTime) {
                scrollOffset = 0;
            } else if (timeInCycle < pauseTime + (overflowWidth / scrollSpeed) * 1000) {
                scrollOffset = (int) ((timeInCycle - pauseTime) * scrollSpeed / 1000);
            } else if (timeInCycle < pauseTime + (overflowWidth / scrollSpeed) * 1000 + pauseTime) {
                scrollOffset = overflowWidth;
            } else {
                scrollOffset = overflowWidth - (int) ((timeInCycle - pauseTime - (overflowWidth / scrollSpeed) * 1000 - pauseTime) * scrollSpeed / 1000);
            }

            context.enableScissor(startX, startY, endX, endY);
            context.drawString(font, text, startX - scrollOffset, startY + (endY - startY - 9) / 2, color, false);
            context.disableScissor();
        } else {
            context.drawString(font, text, startX, startY + (endY - startY - 9) / 2, color, false);
        }
    }
}
