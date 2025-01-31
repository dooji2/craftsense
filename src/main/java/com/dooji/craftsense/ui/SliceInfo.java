package com.dooji.craftsense.ui;

public class SliceInfo {
    final String category;
    final float startAngle;
    final float endAngle;

    SliceInfo(String category, float startAngle, float endAngle) {
        this.category = category;
        this.startAngle = startAngle;
        this.endAngle = endAngle;
    }
}