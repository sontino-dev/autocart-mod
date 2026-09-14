package dev.autocart.config.screen;

import dev.autocart.AutoCartKeybinds;
import dev.autocart.AutoCartMod;
import dev.autocart.config.AutoCartConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

import java.util.function.Consumer;

public class AutoCartConfigScreen extends Screen {

    private final Screen parent;
    private final AutoCartConfig cfg;

    private static final int LX = 20;
    private static final int WX = 220;
    private static final int W  = 160;
    private static final int H  = 20;
    private static final int G  = 24;

    public AutoCartConfigScreen(Screen parent) {
        super(Text.literal("AutoCart Config"));
        this.parent = parent;
        this.cfg    = AutoCartMod.config;
    }

    @Override
    protected void init() {
        int y = 30;

        addDrawableChild(CyclingButtonWidget.<AutoCartConfig.Mode>builder(m -> Text.literal(m.name()))
                .values(AutoCartConfig.Mode.values()).initially(cfg.getMode())
                .build(WX, y, W, H, Text.literal("Mode"), (b, v) -> { cfg.mode = v.name(); cfg.save(); }));

        y += G;
        addDrawableChild(new ACSlider(WX, y, W, H, 2.0, 6.0, cfg.maxDistance,
                "Max Distance: ", v -> { cfg.maxDistance = v.floatValue(); cfg.save(); }));

        y += G;
        addDrawableChild(new IntSlider(WX, y, W, H, "Start Delay (ms): ", 0, 200, cfg.startDelay,
                v -> { cfg.startDelay = v; cfg.save(); }));

        y += G;
        addDrawableChild(new IntSlider(WX, y, W, H, "Delay (ms): ", 0, 200, cfg.delay,
                v -> { cfg.delay = v; cfg.save(); }));

        y += G;
        addDrawableChild(new IntSlider(WX, y, W, H, "Cart Aura Delay (ticks): ", 0, 20, cfg.cartAuraDelay,
                v -> { cfg.cartAuraDelay = v; cfg.save(); }));

        y += G;
        addDrawableChild(new IntSlider(WX, y, W, H, "Refill Slot: ", 1, 9, cfg.refillSlot,
                v -> { cfg.refillSlot = v; cfg.save(); }));

        y += G;
        addDrawableChild(CyclingButtonWidget.<AutoCartConfig.ReFillMode>builder(m -> Text.literal(m.name()))
                .values(AutoCartConfig.ReFillMode.values()).initially(cfg.getReFill())
                .build(WX, y, W, H, Text.literal("ReFill"), (b, v) -> { cfg.reFill = v.name(); cfg.save(); }));

        y += G; addToggle(y, "Swap Back",    cfg.swapBack,       v -> { cfg.swapBack       = v; cfg.save(); });
        y += G; addToggle(y, "Totem Check",  cfg.totemCheck,     v -> { cfg.totemCheck     = v; cfg.save(); });
        y += G; addToggle(y, "Change Look",  cfg.changeLook,     v -> { cfg.changeLook     = v; cfg.save(); });
        y += G; addToggle(y, "Cart Aura",    cfg.cartAura,       v -> { cfg.cartAura       = v; cfg.save(); });
        y += G; addToggle(y, "Aura Target",  cfg.cartAuraTarget, v -> { cfg.cartAuraTarget = v; cfg.save(); });
        y += G; addToggle(y, "Other Player", cfg.cartOtherPlayer,v -> { cfg.cartOtherPlayer= v; cfg.save(); });

        y += G + 8;
        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> close())
                .dimensions(this.width / 2 - 80, y, 160, H).build());
    }

    private void addToggle(int y, String label, boolean val, Consumer<Boolean> cb) {
        addDrawableChild(CyclingButtonWidget.onOffBuilder(val)
                .build(WX, y, W, H, Text.literal(label), (b, v) -> cb.accept(v)));
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        renderBackground(ctx, mx, my, delta);
        ctx.drawCenteredTextWithShadow(textRenderer, this.title, this.width / 2, 10, 0xFFFFFF);

        String toggleKey  = AutoCartKeybinds.TOGGLE.getBoundKeyLocalizedText().getString();
        String crossbowKey= AutoCartKeybinds.CROSSBOW_USE.getBoundKeyLocalizedText().getString();
        ctx.drawTextWithShadow(textRenderer,
                Text.literal("§7Toggle: §f[" + toggleKey + "]   §7CrossBow: §f[" + crossbowKey + "]  §8(change in Controls)"),
                LX, this.height - 22, 0xFFFFFF);

        int y = 30;
        String[] labels = { "Mode:", "Max Distance:", "Start Delay:", "Delay:",
                "Aura Delay:", "Refill Slot:", "ReFill:",
                "Swap Back:", "Totem Check:", "Change Look:",
                "Cart Aura:", "Aura Target:", "Other Player:" };
        for (String l : labels) {
            ctx.drawTextWithShadow(textRenderer, Text.literal(l), LX, y + 6, 0xCCCCCC);
            y += G;
        }
        super.render(ctx, mx, my, delta);
    }

    @Override
    public void close() { assert client != null; client.setScreen(parent); }

    private static class ACSlider extends SliderWidget {
        private final double min, max;
        private final String prefix;
        private final Consumer<Double> cb;
        ACSlider(int x, int y, int w, int h, double min, double max, double init, String prefix, Consumer<Double> cb) {
            super(x, y, w, h, Text.literal(prefix + String.format("%.1f", init)), (init-min)/(max-min));
            this.min = min; this.max = max; this.prefix = prefix; this.cb = cb;
        }
        @Override protected void updateMessage() {
            setMessage(Text.literal(prefix + String.format("%.1f", min + value * (max-min))));
        }
        @Override protected void applyValue() { cb.accept(min + value * (max-min)); }
    }

    private static class IntSlider extends SliderWidget {
        private final int min, max;
        private final String prefix;
        private final Consumer<Integer> cb;
        IntSlider(int x, int y, int w, int h, String prefix, int min, int max, int init, Consumer<Integer> cb) {
            super(x, y, w, h, Text.literal(prefix + init), (double)(init-min)/(max-min));
            this.min = min; this.max = max; this.prefix = prefix; this.cb = cb;
        }
        @Override protected void updateMessage() {
            setMessage(Text.literal(prefix + (int)Math.round(min + value*(max-min))));
        }
        @Override protected void applyValue() { cb.accept((int)Math.round(min + value*(max-min))); }
    }
}
