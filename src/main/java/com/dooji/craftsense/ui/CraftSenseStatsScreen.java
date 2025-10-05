package com.dooji.craftsense.ui;

import com.dooji.craftsense.CraftSenseClient;
import com.dooji.craftsense.manager.CategoryHabitsTracker;
import com.dooji.craftsense.manager.CategoryManager;
import com.dooji.craftsense.omnilib.OmniButton;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

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
    private NativeImageBackedTexture pieTexture;
    private Identifier pieTextureId;
    private int pieTextureWidth, pieTextureHeight;
    private boolean pieNeedsUpdate = true;

    private OmniButton legendPrevBtn, legendNextBtn, closeButton;
    private int legendPage = 0;
    private int legendPages = 1;

    public CraftSenseStatsScreen() {
        super(Text.translatable("screen.craftsense.stats"));

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
                String translationKey = entry.getKey();
                Optional<Item> optionalItem = Registries.ITEM.stream()
                        .filter(item -> item.getTranslationKey().equals(translationKey))
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
                this.width / 2 - 40,
                this.height - 40,
                80,
                20,
                Text.translatable("screen.craftsense.stats.done"),
                0x99000000, 0xBB000000, 0xFFFFFFFF, 0xFFEFEFEF,
                this::close
        );
        this.addDrawableChild(closeButton);

        if (!categoryTotals.isEmpty()) {
            legendPrevBtn = CraftSenseClient.createOmniButton(
                    0,
                    0,
                    20,
                    20,
                    Text.literal("<"),
                    0x99000000,
                    0xBB000000,
                    0xFFFFFFFF,
                    0xFFEFEFEF,
                    () -> {
                        if (legendPage > 0) legendPage--;
                    }
            );
            this.addDrawableChild(legendPrevBtn);

            legendNextBtn = CraftSenseClient.createOmniButton(
                    0,
                    0,
                    20,
                    20,
                    Text.literal(">"),
                    0x99000000,
                    0xBB000000,
                    0xFFFFFFFF,
                    0xFFEFEFEF,
                    () -> {
                        if (legendPage < legendPages - 1) legendPage++;
                    }
            );
            this.addDrawableChild(legendNextBtn);
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
                String translationKey = entry.getKey();
                Optional<Item> optionalItem = Registries.ITEM.stream()
                        .filter(item -> item.getTranslationKey().equals(translationKey))
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
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 15, 0xFFFFFF);

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
            context.drawCenteredTextWithShadow(this.textRenderer, Text.translatable("screen.craftsense.no_history"), this.width / 2, areaCenterY, 0xAAAAAA);
        }
    }

    private void renderPieChart(DrawContext context, int mouseX, int mouseY, int areaCenterY) {
        if (pieTexture == null) return;

        int drawY = areaCenterY - pieTextureHeight / 2;
        int totalWidth = pieTextureWidth + 100;
        int cx = (this.width - totalWidth) / 2;
        context.drawTexture(pieTextureId, cx, drawY, 0, 0, pieTextureWidth, pieTextureHeight, pieTextureWidth, pieTextureHeight);

        int radius = Math.min(100, (pieTextureHeight - 40) / 2);
        int centerX = cx + (radius + 20);
        int centerY = drawY + (radius + 20);
        handlePieHover(context, mouseX, mouseY, centerX, centerY, radius);
    }

    private void renderLegend(DrawContext context, int areaCenterY) {
        if (categoryTotals.isEmpty()) {
            return;
        }

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
        context.drawCenteredTextWithShadow(this.textRenderer, pageText, midX, paginationY + 5, 0xFFFFFF);

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
            Text display = hidden ? Text.literal(displayStr).styled(style -> style.withItalic(true).withColor(0xFFAAAAAA)) : Text.literal(displayStr).styled(style -> style.withColor(0xFFFFFFFF));

            int textStartX = cx + 12;
            int textEndX = cx + legendWidth;
            int textStartY = lineY + 2;
            int textEndY = lineY + 12;

            context.enableScissor(textStartX, textStartY, textEndX, textEndY);
            renderScrollableText(context, this.textRenderer, display, textStartX, textStartY, textEndX, textEndY, 0xFFFFFF);
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

        float saturation = 0.7f;
        float brightness = 0.9f;

        int rgb = Color.HSBtoRGB(hue, saturation, brightness);

        return 0xFF000000 | (rgb & 0x00FFFFFF);
    }

    private void generatePieTexture() {
        if (pieTexture != null) {
            MinecraftClient.getInstance().getTextureManager().destroyTexture(pieTextureId);

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
                .rotate(RotationAxis.POSITIVE_X.rotationDegrees(45f))
                .translate(-(radius + margin), -(radius + margin), 0);

        int total = 0;
        for (var e : categoryTotals.entrySet()) {
            if (!hiddenCategories.contains(e.getKey())) {
                total += e.getValue();
            }
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
            SliceInfo singleSlice = slices.getFirst();
            int color = getCategoryColor(singleSlice.category);

            fillCircleWithThickness(pieImage, radius + margin, radius + margin, radius, color, transform, 6);
        } else {
            int thickness = 6;

            int centerX = radius + margin;
            int centerY = radius + margin;

            int minX = centerX - radius;
            int maxX = centerX + radius;
            int minY = centerY - radius;
            int maxY = centerY + radius;

            for (int px = minX; px <= maxX; px++) {
                float dx = px - centerX;

                for (int py = minY; py <= maxY; py++) {
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
                        int color = (colorArgb & 0xFF00FF00) | ((colorArgb & 0x00FF0000) >> 16) | ((colorArgb & 0x000000FF) << 16);

                        Vector4f pos = new Vector4f(px, py, 0, 1).mul(transform);
                        int rx = (int) pos.x;
                        int ry = (int) pos.y;

                        if (rx >= 0 && rx < pieImage.getWidth() && ry >= 0 && ry < pieImage.getHeight()) {
                            pieImage.setColor(rx, ry, color);
                        }

                        int darkColor = darken(colorArgb, 0.5f);
                        color = (darkColor & 0xFF00FF00) | ((darkColor & 0x00FF0000) >> 16) | ((darkColor & 0x000000FF) << 16);

                        for (int t = 1; t <= thickness; t++) {
                            int wallY = ry + t;
                            if (wallY >= 0 && wallY < pieImage.getHeight()) {
                                pieImage.setColor(rx, wallY, color);
                            }
                        }
                    }
                }
            }
        }

        pieTexture = new NativeImageBackedTexture(pieImage);
        pieTextureId = MinecraftClient.getInstance().getTextureManager().registerDynamicTexture("craftsense_pie", pieTexture);
    }

    private void fillCircleWithThickness(NativeImage image, int centerX, int centerY, int radius, int colorArgb, Matrix4f transform, int thickness) {
        int minX = centerX - radius;
        int maxX = centerX + radius;
        int minY = centerY - radius;
        int maxY = centerY + radius;

        for (int px = minX; px <= maxX; px++) {
            float dx = px - centerX;

            for (int py = minY; py <= maxY; py++) {
                float dy = py - centerY;

                if (dx * dx + dy * dy <= radius * radius) {
                    Vector4f pos = new Vector4f(px, py, 0, 1).mul(transform);
                    int rx = (int) pos.x;
                    int ry = (int) pos.y;

                    int color = (colorArgb & 0xFF00FF00) | ((colorArgb & 0x00FF0000) >> 16) | ((colorArgb & 0x000000FF) << 16);

                    if (rx >= 0 && rx < image.getWidth() && ry >= 0 && ry < image.getHeight()) {
                        image.setColor(rx, ry, color);
                    }

                    int darkColor = darken(colorArgb, 0.5f);
                    color = (darkColor & 0xFF00FF00) | ((darkColor & 0x00FF0000) >> 16) | ((darkColor & 0x000000FF) << 16);
                    for (int t = 1; t <= thickness; t++) {
                        int wallY = ry + t;
                        if (wallY >= 0 && wallY < image.getHeight()) {
                            image.setColor(rx, wallY, color);
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
        int b = (color)        & 0xFF;
        r = (int)(r * factor);
        g = (int)(g * factor);
        b = (int)(b * factor);

        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private void handlePieHover(DrawContext context, int mouseX, int mouseY, int cx, int cy, int radius) {
        float dx = mouseX - cx;
        float dy = mouseY - cy;

        if (dx * dx + dy * dy <= radius * radius) {
            List<Map.Entry<String, Integer>> visibleSlices = categoryTotals.entrySet()
                    .stream()
                    .filter(e -> !hiddenCategories.contains(e.getKey()))
                    .toList();

            if (visibleSlices.size() == 1) {
                String category = visibleSlices.getFirst().getKey();
                showTooltipForCategory(context, category, mouseX, mouseY);
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
                    float start = accum;
                    float end = accum + sliceAngle;

                    if (isAngleInSlice(angle, start, end)) {
                        String category = slice.getKey();
                        showTooltipForCategory(context, category, mouseX, mouseY);
                        break;
                    }

                    accum += sliceAngle;
                }
            }
        }
    }

    private void showTooltipForCategory(DrawContext context, String category, int mouseX, int mouseY) {
        var items = categoryItemsMap.getOrDefault(category, List.of());

        List<ItemStack> stacks = new ArrayList<>();
        List<Text> lines = new ArrayList<>();

        for (var itE : items) {
            Registries.ITEM.stream()
                    .filter(it -> it.getTranslationKey().equals(itE.getKey()))
                    .findFirst()
                    .ifPresent(it -> {
                        stacks.add(it.getDefaultStack());
                        lines.add(Text.literal(it.getName().getString() + " - " + formatNumberShorthand(itE.getValue())));
                    });
        }

        CraftSenseClient.showTooltip(
                context,
                this.textRenderer,
                category,
                stacks,
                lines.isEmpty()
                        ? List.of(Text.translatable("tooltip.craftsense.no_items_found"))
                        : lines,
                0x99000000,
                null,
                0xFFFFFF,
                null,
                mouseX + 10,
                mouseY + 10
        );
    }

    private String formatNumberShorthand(int number) {
        if (number >= 1_000_000_000) {
            return String.format("%.1fB", number / 1_000_000_000.0);
        } else if (number >= 1_000_000) {
            return String.format("%.1fM", number / 1_000_000.0);
        } else if (number >= 1_000) {
            return String.format("%.1fK", number / 1_000.0);
        }

        return String.valueOf(number);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            this.close();
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private static void renderScrollableText(DrawContext context, TextRenderer textRenderer, Text text, int startX, int startY, int endX, int endY, int color) {
        int textWidth = textRenderer.getWidth(text);
        int availableWidth = endX - startX;
        int pauseTime = 1000;
        double scrollSpeed = 5.0;

        if (textWidth > availableWidth) {
            int overflowWidth = textWidth - availableWidth;

            long currentTime = System.currentTimeMillis();
            long totalCycleTime = (long) ((overflowWidth / scrollSpeed) * 1000) * 2 + pauseTime * 2;
            long timeInCycle = currentTime % totalCycleTime;

            int scrollOffset;

            if (timeInCycle < pauseTime) {
                scrollOffset = 0;
            } else if (timeInCycle < pauseTime + (overflowWidth / scrollSpeed) * 1000) {
                double elapsed = timeInCycle - pauseTime;
                scrollOffset = (int) (elapsed * scrollSpeed / 1000);
            } else if (timeInCycle < pauseTime + (overflowWidth / scrollSpeed) * 1000 + pauseTime) {
                scrollOffset = overflowWidth;
            } else {
                double elapsed = timeInCycle - pauseTime - (overflowWidth / scrollSpeed) * 1000 - pauseTime;
                scrollOffset = overflowWidth - (int) (elapsed * scrollSpeed / 1000);
            }

            context.enableScissor(startX, startY, endX, endY);
            context.drawTextWithShadow(textRenderer, text, startX - scrollOffset, startY + (endY - startY - 9) / 2, color);
            context.disableScissor();
        } else {
            context.drawTextWithShadow(textRenderer, text, startX, startY + (endY - startY - 9) / 2, color);
        }
    }
}