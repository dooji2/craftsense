package com.dooji.craftsense.ui;

import com.dooji.craftsense.manager.CategoryHabitsTracker;
import com.dooji.craftsense.manager.CategoryManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.client.gui.DrawContext;

import java.util.*;
import java.util.stream.Collectors;

public class CraftSenseStatsScreen extends Screen {
    private final CategoryHabitsTracker tracker = CategoryHabitsTracker.getInstance();
    private final Map<String, Integer> categoryTotals;
    private final Map<String, List<Map.Entry<String, Integer>>> categoryItemsMap;
    private final List<Integer> barColors;
    private static final int BASE_GRAPH_HEIGHT = 150;
    private static final int BASE_GRAPH_WIDTH = 300;
    private static final int BAR_WIDTH = 30;
    private int categoriesPerPage;
    private int graphHeight;
    private int graphWidth;
    private int currentPage = 0;
    private int maxPages;
    private ButtonWidget prevButton;
    private ButtonWidget nextButton;
    private Text pageIndicator;

    public CraftSenseStatsScreen() {
        super(Text.translatable("screen.craftsense.stats"));
        categoryTotals = tracker.categoryCraftCount.entrySet()
                .stream()
                .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        LinkedHashMap::new
                ));
        categoryItemsMap = createCategoryItemsMap();
        barColors = generateColorPalette(50);
        setCategoriesPerPageAndGraphSize();
        maxPages = (int) Math.ceil((double) categoryTotals.size() / categoriesPerPage);
    }

    private List<Integer> generateColorPalette(int count) {
        List<Integer> colors = new ArrayList<>();
        Random random = new Random();

        for (int i = 0; i < count; i++) {
            float hue = random.nextFloat();
            float saturation = 0.7f + (random.nextFloat() * 0.3f);
            float brightness = 0.7f + (random.nextFloat() * 0.3f);

            int color = java.awt.Color.HSBtoRGB(hue, saturation, brightness);
            colors.add(color);
        }

        return colors;
    }

    private void setCategoriesPerPageAndGraphSize() {
        int guiScale = MinecraftClient.getInstance().options.getGuiScale().getValue();

        switch (guiScale) {
            case 4, 0:
                categoriesPerPage = 8;
                graphHeight = BASE_GRAPH_HEIGHT * 3 / 4 - (BASE_GRAPH_HEIGHT * 3 / 4) / 4;
                break;
            case 3:
                categoriesPerPage = 10;
                graphHeight = BASE_GRAPH_HEIGHT;
                break;
            case 2:
                categoriesPerPage = 14;
                graphHeight = BASE_GRAPH_HEIGHT * 5 / 4;
                break;
            case 1:
                categoriesPerPage = 20;
                graphHeight = BASE_GRAPH_HEIGHT * 3 / 2;
                break;
            default:
                categoriesPerPage = 8;
                graphHeight = BASE_GRAPH_HEIGHT;
                break;
        }

        graphWidth = BASE_GRAPH_WIDTH * guiScale / 4;
        if (graphHeight < 100) graphHeight = 100;
        if (graphWidth < 200) graphWidth = 200;
    }

    private Map<String, List<Map.Entry<String, Integer>>> createCategoryItemsMap() {
        Map<String, List<Map.Entry<String, Integer>>> categoryMap = new HashMap<>();
        tracker.categoryCraftCount.keySet().forEach(category -> {
            List<Map.Entry<String, Integer>> items = tracker.itemCraftCount.entrySet().stream()
                    .filter(entry -> {
                        String translationKey = entry.getKey();
                        return Registries.ITEM.stream()
                                .filter(item -> item.getTranslationKey().equals(translationKey))
                                .findFirst()
                                .map(item -> category.equals(CategoryManager.getCategory(item)))
                                .orElse(false);
                    })
                    .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                    .collect(Collectors.toList());
            categoryMap.put(category, items);
        });
        return categoryMap;
    }

    @Override
    protected void init() {
        int padding = 10;
        int paginationButtonY = 50;

        prevButton = ButtonWidget.builder(
                        Text.translatable("screen.craftsense.stats.previous"),
                        button -> {
                            if (currentPage > 0) {
                                currentPage--;
                                updateButtonStates();
                            }
                        }
                ).dimensions(this.width / 2 - 150, paginationButtonY, 80, 20)
                .build();
        this.addDrawableChild(prevButton);

        pageIndicator = Text.translatable("screen.craftsense.stats.page", currentPage + 1, maxPages);

        nextButton = ButtonWidget.builder(
                        Text.translatable("screen.craftsense.stats.next"),
                        button -> {
                            if (currentPage < maxPages - 1) {
                                currentPage++;
                                updateButtonStates();
                            }
                        }
                ).dimensions(this.width / 2 + 70, paginationButtonY, 80, 20)
                .build();
        this.addDrawableChild(nextButton);

        ButtonWidget closeButton = ButtonWidget.builder(
                        Text.translatable("screen.craftsense.stats.done"),
                        button -> this.close()
                ).dimensions(this.width / 2 - 50, this.height - 30, 100, 20)
                .build();
        this.addDrawableChild(closeButton);

        updateButtonStates();
    }

    private void updateButtonStates() {
        prevButton.active = currentPage > 0;
        nextButton.active = currentPage < maxPages - 1;
        pageIndicator = Text.translatable("screen.craftsense.stats.page", currentPage + 1, maxPages);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 20, 0xFFFFFF);

        int paginationY = 55;
        context.drawCenteredTextWithShadow(this.textRenderer, pageIndicator, this.width / 2, paginationY, 0xFFFFFF);

        int graphTop = paginationY + 20;
        int graphBottom = this.height - 45;
        int graphCenterY = (graphTop + graphBottom) / 2 - (graphHeight / 2);
        renderGraph(context, mouseX, mouseY, graphCenterY);
    }

    private void renderGraph(DrawContext context, int mouseX, int mouseY, int graphY) {
        int maxVisibleCategories = Math.min(categoriesPerPage, categoryTotals.size());
        int totalGraphWidth = maxVisibleCategories * (BAR_WIDTH + 5) - 5;
        int graphX = (this.width - totalGraphWidth) / 2;
        int maxValue = categoryTotals.values().stream().max(Integer::compare).orElse(1);
        int colorIndex = 0;
        int xOffset = graphX;
        int legendYOffset = graphY + graphHeight + 10;

        String tooltipCategory = null;
        List<ItemStack> tooltipItemStacks = new ArrayList<>();
        List<Text> tooltipItemTexts = new ArrayList<>();
        boolean showTooltip = false;

        List<Map.Entry<String, Integer>> categoriesOnPage = new ArrayList<>(categoryTotals.entrySet())
                .subList(currentPage * categoriesPerPage, Math.min((currentPage + 1) * categoriesPerPage, categoryTotals.size()));

        for (Map.Entry<String, Integer> entry : categoriesOnPage) {
            String category = entry.getKey();
            int count = entry.getValue();
            int barHeight = (int) ((double) count / maxValue * graphHeight);
            int barColor = barColors.get(colorIndex % barColors.size());

            context.fillGradient(xOffset, graphY + (graphHeight - barHeight), xOffset + BAR_WIDTH, graphY + graphHeight, barColor, barColor);

            int maxTextWidth = BAR_WIDTH + 2;
            String displayText = category;
            if (textRenderer.getWidth(category) > maxTextWidth) {
                displayText = trimTextToFit(category, maxTextWidth, textRenderer);
            }

            context.drawCenteredTextWithShadow(
                    this.textRenderer,
                    Text.literal(displayText),
                    xOffset + BAR_WIDTH / 2,
                    legendYOffset,
                    0xFFFFFF
            );

            context.drawCenteredTextWithShadow(
                    this.textRenderer,
                    Text.literal(String.valueOf(count)),
                    xOffset + BAR_WIDTH / 2,
                    graphY + (graphHeight - barHeight) - 10,
                    0xFFFFFF
            );

            if (mouseX >= xOffset && mouseX <= xOffset + BAR_WIDTH && mouseY >= graphY && mouseY <= graphY + graphHeight) {
                showTooltip = true;
                tooltipCategory = category;

                List<Map.Entry<String, Integer>> items = categoryItemsMap.get(category);
                if (items != null && !items.isEmpty()) {
                    for (Map.Entry<String, Integer> itemEntry : items) {
                        String itemTranslationKey = itemEntry.getKey();
                        Registries.ITEM.stream()
                                .filter(item -> item.getTranslationKey().equals(itemTranslationKey))
                                .findFirst()
                                .ifPresent(item -> {
                                    tooltipItemStacks.add(item.getDefaultStack());
                                    tooltipItemTexts.add(Text.literal(item.getName().getString() + " - " + itemEntry.getValue()));
                                });
                    }
                } else {
                    tooltipItemTexts.add(Text.translatable("tooltip.craftsense.no_items_found"));
                }
            }

            xOffset += BAR_WIDTH + 5;
            colorIndex++;
        }

        if (showTooltip && tooltipCategory != null) {
            CustomTooltip customTooltip = new CustomTooltip(tooltipCategory, tooltipItemStacks, tooltipItemTexts);
            customTooltip.render(context, this.textRenderer, mouseX + 10, mouseY + 10);
        }
    }

    private String trimTextToFit(String text, int maxWidth, TextRenderer textRenderer) {
        String ellipsis = "...";
        int ellipsisWidth = textRenderer.getWidth(ellipsis);

        for (int i = text.length(); i > 0; i--) {
            String subText = text.substring(0, i);
            if (textRenderer.getWidth(subText) + ellipsisWidth <= maxWidth) {
                return subText + ellipsis;
            }
        }

        return ellipsis;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            this.close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}