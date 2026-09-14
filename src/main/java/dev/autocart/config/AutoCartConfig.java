package dev.autocart.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public class AutoCartConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("autocart.json");

    public String mode = "Bow";
    public float maxDistance = 4.5f;
    public int startDelay = 25;
    public int delay = 25;
    public int cartAuraDelay = 0;
    public int refillSlot = 9;
    public boolean swapBack = true;
    public boolean totemCheck = true;
    public boolean changeLook = false;
    public boolean cartAura = false;
    public String reFill = "None";
    public boolean cartAuraTarget = true;
    public boolean cartOtherPlayer = false;

    public static AutoCartConfig load() {
        if (Files.exists(CONFIG_PATH)) {
            try (Reader r = Files.newBufferedReader(CONFIG_PATH)) {
                AutoCartConfig cfg = GSON.fromJson(r, AutoCartConfig.class);
                if (cfg != null) return cfg;
            } catch (IOException ignored) {}
        }
        AutoCartConfig def = new AutoCartConfig();
        def.save();
        return def;
    }

    public void save() {
        try (Writer w = Files.newBufferedWriter(CONFIG_PATH)) {
            GSON.toJson(this, w);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public enum Mode { Bow, CrossBow }
    public enum ReFillMode { None, Normal, Legit }

    public Mode getMode() {
        try { return Mode.valueOf(mode); } catch (Exception e) { return Mode.Bow; }
    }

    public ReFillMode getReFill() {
        try { return ReFillMode.valueOf(reFill); } catch (Exception e) { return ReFillMode.None; }
    }
}
