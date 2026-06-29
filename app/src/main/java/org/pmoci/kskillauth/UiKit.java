package org.pmoci.kskillauth;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputLayout;

final class UiKit {
    static final int COLOR_PRIMARY = Color.parseColor("#1A73E8");
    static final int COLOR_PRIMARY_DARK = Color.parseColor("#0B3D91");
    static final int COLOR_SECONDARY = Color.parseColor("#00897B");
    static final int COLOR_BACKGROUND = Color.parseColor("#F7F9FC");
    static final int COLOR_SURFACE = Color.WHITE;
    static final int COLOR_SURFACE_VARIANT = Color.parseColor("#EEF3F8");
    static final int COLOR_ON_SURFACE = Color.parseColor("#172033");
    static final int COLOR_MUTED = Color.parseColor("#5F6C7B");
    static final int COLOR_OUTLINE = Color.parseColor("#D6DEE8");
    static final int COLOR_SUCCESS = Color.parseColor("#137333");
    static final int COLOR_ERROR = Color.parseColor("#B3261E");

    private UiKit() {
    }

    static void applyLightSystemBars(Activity activity) {
        Window window = activity.getWindow();
        window.setStatusBarColor(COLOR_BACKGROUND);
        window.setNavigationBarColor(COLOR_BACKGROUND);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            window.getDecorView().setSystemUiVisibility(
                    window.getDecorView().getSystemUiVisibility()
                            | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }
    }

    static void applyDarkSystemBars(Activity activity) {
        Window window = activity.getWindow();
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.BLACK);
        window.getDecorView().setSystemUiVisibility(0);
    }

    static LinearLayout screenRoot(Context context) {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(context, 24), dp(context, 28), dp(context, 24), dp(context, 28));
        root.setBackgroundColor(COLOR_BACKGROUND);
        return root;
    }

    static MaterialCardView card(Context context) {
        MaterialCardView card = new MaterialCardView(context);
        card.setCardBackgroundColor(COLOR_SURFACE);
        card.setRadius(dp(context, 8));
        card.setStrokeWidth(dp(context, 1));
        card.setStrokeColor(COLOR_OUTLINE);
        card.setCardElevation(dp(context, 1));
        card.setUseCompatPadding(false);
        return card;
    }

    static LinearLayout cardContent(Context context) {
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(context, 18), dp(context, 18), dp(context, 18), dp(context, 18));
        return content;
    }

    static TextView title(Context context, String text) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextColor(COLOR_ON_SURFACE);
        view.setTextSize(26);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setIncludeFontPadding(false);
        return view;
    }

    static TextView subtitle(Context context, String text) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextColor(COLOR_MUTED);
        view.setTextSize(15);
        view.setLineSpacing(dp(context, 2), 1.0f);
        return view;
    }

    static TextView sectionTitle(Context context, String text) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextColor(COLOR_ON_SURFACE);
        view.setTextSize(17);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setIncludeFontPadding(false);
        return view;
    }

    static TextView statusText(Context context) {
        TextView view = new TextView(context);
        view.setTextColor(COLOR_MUTED);
        view.setTextSize(14);
        view.setLineSpacing(dp(context, 2), 1.0f);
        return view;
    }

    static TextView chip(Context context, String text, int color) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextColor(color);
        view.setTextSize(13);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setPadding(dp(context, 12), dp(context, 7), dp(context, 12), dp(context, 7));
        view.setBackground(rounded(colorWithAlpha(color, 0x18), dp(context, 100), 0, 0));
        return view;
    }

    static MaterialButton primaryButton(Context context, String text) {
        MaterialButton button = baseButton(context, text);
        button.setBackgroundTintList(ColorStateList.valueOf(COLOR_PRIMARY));
        button.setTextColor(Color.WHITE);
        return button;
    }

    static MaterialButton secondaryButton(Context context, String text) {
        MaterialButton button = baseButton(context, text);
        button.setBackgroundTintList(ColorStateList.valueOf(COLOR_SURFACE));
        button.setTextColor(COLOR_PRIMARY);
        button.setStrokeColor(ColorStateList.valueOf(COLOR_PRIMARY));
        button.setStrokeWidth(dp(context, 1));
        return button;
    }

    static MaterialButton dangerButton(Context context, String text) {
        MaterialButton button = baseButton(context, text);
        button.setBackgroundTintList(ColorStateList.valueOf(COLOR_SURFACE));
        button.setTextColor(COLOR_ERROR);
        button.setStrokeColor(ColorStateList.valueOf(COLOR_ERROR));
        button.setStrokeWidth(dp(context, 1));
        return button;
    }

    static void setButtonEnabled(MaterialButton button, boolean enabled) {
        button.setEnabled(enabled);
        button.setBackgroundTintList(ColorStateList.valueOf(enabled ? COLOR_PRIMARY : COLOR_SURFACE_VARIANT));
        button.setTextColor(enabled ? Color.WHITE : COLOR_MUTED);
    }

    static void styleInput(TextInputLayout layout) {
        layout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        layout.setBoxBackgroundColor(COLOR_SURFACE);
        layout.setBoxStrokeColor(COLOR_PRIMARY);
        layout.setHintTextColor(ColorStateList.valueOf(COLOR_MUTED));
        int radius = dp(layout.getContext(), 8);
        layout.setBoxCornerRadii(radius, radius, radius, radius);
    }

    static GradientDrawable rounded(int color, int radius, int strokeColor, int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        if (strokeWidth > 0) {
            drawable.setStroke(strokeWidth, strokeColor);
        }
        return drawable;
    }

    static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
    }

    static LinearLayout.LayoutParams topMargin(Context context, int value) {
        LinearLayout.LayoutParams params = matchWrap();
        params.setMargins(0, dp(context, value), 0, 0);
        return params;
    }

    static LinearLayout.LayoutParams weight() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
    }

    static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static MaterialButton baseButton(Context context, String text) {
        MaterialButton button = new MaterialButton(context);
        button.setText(text);
        button.setAllCaps(false);
        button.setMinHeight(dp(context, 48));
        button.setMinimumHeight(dp(context, 48));
        button.setCornerRadius(dp(context, 8));
        button.setTextSize(15);
        button.setInsetTop(0);
        button.setInsetBottom(0);
        return button;
    }

    private static int colorWithAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }
}
