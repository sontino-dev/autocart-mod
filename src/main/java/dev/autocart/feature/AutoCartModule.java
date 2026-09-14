package dev.autocart.feature;

import dev.autocart.AutoCartMod;
import dev.autocart.accessor.IInteractionManager;
import dev.autocart.config.AutoCartConfig;
import dev.autocart.core.AsyncManager;
import dev.autocart.events.ACEventHandler;
import dev.autocart.events.impl.EventKeyboardInput;
import dev.autocart.events.impl.EventSync;
import dev.autocart.events.impl.PacketEvent;
import dev.autocart.util.InteractionUtil;
import dev.autocart.util.InventoryUtil;
import dev.autocart.util.MovementUtil;
import dev.autocart.util.PlayerUtil;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ChargedProjectilesComponent;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.item.ArrowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public class AutoCartModule {

    private final AsyncManager async = new AsyncManager();

    private volatile float[] silentRotation = null;
    private volatile boolean rotating       = false;

    private volatile boolean cartAuraExecuting   = false;
    private boolean          scheduledRefillSwap  = false;
    private int              scheduledRefillInvSlot  = -1;
    private int              scheduledRefillHotSlot  = -1;
    private long             scheduledRefillAt    = -1L;

    private UUID lastAuraTargetUuid = null;
    private int  lastAuraTargetId   = -1;
    private long lastAuraTargetAt   = -1L;

    private boolean enabled = false;

    public void enable()  { this.enabled = true;  resetState(); }
    public void disable() { this.enabled = false; resetState(); }
    public boolean isEnabled() { return enabled; }

    private void resetState() {
        silentRotation     = null;
        rotating           = false;
        cartAuraExecuting  = false;
        lastAuraTargetUuid = null;
        lastAuraTargetId   = -1;
        lastAuraTargetAt   = -1L;
        clearRefillState();
    }

    private AutoCartConfig cfg() { return AutoCartMod.config; }

    // ─── called every client tick from AutoCartMod ────────────────────────────
    public void onTick() {
        if (!enabled || AutoCartMod.nullCheck()) return;
        handleScheduledRefill();
        handleRefill();
        if (cfg().getMode() == AutoCartConfig.Mode.CrossBow) {
            updateCartAuraTargetMemory();
        }
    }

    // ─── bow release — called from MixinClientPlayerInteractionManager ─────────
    public void onBowRelease() {
        if (!enabled || AutoCartMod.nullCheck()) return;
        if (cfg().getMode() != AutoCartConfig.Mode.Bow) return;
        executeBowMode();
    }

    // ─── crossbow trigger — called from keybind in AutoCartMod ────────────────
    public void triggerCrossBow() {
        if (!enabled || AutoCartMod.nullCheck()) return;
        if (cfg().getMode() != AutoCartConfig.Mode.CrossBow) return;
        executeCrossBowMode();
    }

    // ─── events ───────────────────────────────────────────────────────────────

    @ACEventHandler
    public void onSync(EventSync e) {
        if (!AutoCartMod.nullCheck() && rotating && silentRotation != null) {
            AutoCartMod.mc.player.setYaw(silentRotation[0]);
            AutoCartMod.mc.player.setPitch(silentRotation[1]);
        }
    }

    @ACEventHandler
    public void onKeyboardInput(EventKeyboardInput event) {
        if (!enabled || AutoCartMod.nullCheck()) return;
        float[] rotation = getMoveFixRotation();
        if (rotation == null) return;
        float fwd  = AutoCartMod.mc.player.input.movementForward;
        float side = AutoCartMod.mc.player.input.movementSideways;
        float delta = (AutoCartMod.mc.player.getYaw() - rotation[0]) * (float) (Math.PI / 180.0);
        float cos = MathHelper.cos(delta);
        float sin = MathHelper.sin(delta);
        AutoCartMod.mc.player.input.movementSideways = Math.round(side * cos - fwd * sin);
        AutoCartMod.mc.player.input.movementForward  = Math.round(fwd  * cos + side * sin);
    }

    @ACEventHandler
    public void onPacketReceive(PacketEvent.Receive event) {
        if (!enabled || AutoCartMod.nullCheck()) return;
        if (cfg().getMode() != AutoCartConfig.Mode.CrossBow || !cfg().cartAura) return;
        if (event.getPacket() instanceof EntityStatusS2CPacket pkt
                && pkt.getStatus() == 35
                && pkt.getEntity(AutoCartMod.mc.world) instanceof PlayerEntity player) {
            handleCartAuraPop(player);
        }
    }

    // ─── bow mode ─────────────────────────────────────────────────────────────

    private void executeBowMode() {
        if (AutoCartMod.nullCheck()) return;
        InventoryUtil.InvResult bowResult  = InventoryUtil.findItemInHotbar(Items.BOW);
        InventoryUtil.InvResult cartResult = InventoryUtil.findItemInHotbar(Items.TNT_MINECART);
        if (!bowResult.found() || !cartResult.found()) return;

        BlockPos targetPos = calcBowTrajectory(AutoCartMod.mc.player.getYaw());
        if (targetPos == null) return;

        BlockPos basePos    = getCartBasePos(targetPos);
        boolean  railExists = isRailBlock(AutoCartMod.mc.world.getBlockState(basePos.up()).getBlock());
        if (!railExists && !findRailInHotbar().found()) return;

        float distSq    = PlayerUtil.squaredDistanceFromEyes(basePos.up().toCenterPos());
        float maxDistSq = cfg().maxDistance * cfg().maxDistance;
        if (distSq > maxDistSq || distSq < 4.0f) return;

        async.run(() -> executeBowPlacement(targetPos), cfg().startDelay);
    }

    private void executeBowPlacement(BlockPos targetPos) {
        if (AutoCartMod.nullCheck() || cfg().getMode() != AutoCartConfig.Mode.Bow) return;

        BlockPos basePos    = getCartBasePos(targetPos);
        boolean  railExists = isRailBlock(AutoCartMod.mc.world.getBlockState(basePos.up()).getBlock());
        InventoryUtil.InvResult railResult = findRailInHotbar();
        InventoryUtil.InvResult cartResult = InventoryUtil.findItemInHotbar(Items.TNT_MINECART);
        if (!cartResult.found() || (!railExists && !railResult.found())) return;

        int prevSlot = AutoCartMod.mc.player.getInventory().selectedSlot;
        int delay    = cfg().delay;
        Vec3d placeVec = new Vec3d(basePos.getX() + 0.5, basePos.up().getY(), basePos.getZ() + 0.5);

        runOnClient(() -> applyRotation(InteractionUtil.calculateAngle(placeVec)));
        AsyncManager.sleep(delay);

        if (!railExists) {
            runOnClient(() -> {
                if (!isRailBlock(AutoCartMod.mc.world.getBlockState(basePos.up()).getBlock())) {
                    selectSlot(railResult.slot());
                    placeRailOn(basePos);
                }
            });
            AsyncManager.sleep(delay);
        }

        runOnClient(() -> {
            selectSlot(cartResult.slot());
            placeMinecartOn(basePos);
        });
        AsyncManager.sleep(delay);

        runOnClient(() -> {
            if (cfg().swapBack) selectSlot(prevSlot);
            endRotation();
        });
    }

    // ─── crossbow mode ────────────────────────────────────────────────────────

    private void executeCrossBowMode() {
        if (AutoCartMod.nullCheck()) return;

        InventoryUtil.InvResult crossbow = findLoadedCrossbowInHotbar();
        InventoryUtil.InvResult rail     = findRailInHotbar();
        InventoryUtil.InvResult cart     = InventoryUtil.findItemInHotbar(Items.TNT_MINECART);
        if (!crossbow.found() || !rail.found() || !cart.found()) return;

        boolean hasFlame   = hasFlameEnchant(AutoCartMod.mc.player.getInventory().getStack(crossbow.slot()));
        InventoryUtil.InvResult flint = InventoryUtil.findItemInHotbar(Items.FLINT_AND_STEEL);
        if (!hasFlame && !flint.found()) return;

        BlockHitResult ray = rayFromEyes(4.5);
        if (ray == null || ray.getType() != net.minecraft.util.hit.HitResult.Type.BLOCK) return;

        BlockPos hitPos = ray.getBlockPos();
        if (PlayerUtil.squaredDistanceFromEyes(hitPos.toCenterPos()) > 20.25f) return;

        BlockPos basePos = AutoCartMod.mc.world.getBlockState(hitPos).isReplaceable()
                ? hitPos.down() : hitPos;

        BlockPos firePos = null;
        if (!hasFlame) {
            firePos = findFirePosition(basePos);
            if (firePos == null) return;
        }

        final BlockPos finalFirePos = firePos;
        final boolean  finalFlame   = hasFlame;
        int prevSlot = AutoCartMod.mc.player.getInventory().selectedSlot;
        int delay    = cfg().delay;

        async.run(() -> {
            Vec3d placeVec = new Vec3d(basePos.getX() + 0.5, basePos.up().getY(), basePos.getZ() + 0.5);
            runOnClient(() -> applyRotation(InteractionUtil.calculateAngle(placeVec)));
            AsyncManager.sleep(delay);

            runOnClient(() -> {
                if (!isRailBlock(AutoCartMod.mc.world.getBlockState(basePos.up()).getBlock())) {
                    selectSlot(rail.slot());
                    placeRailOn(basePos);
                }
            });
            AsyncManager.sleep(delay);

            runOnClient(() -> {
                selectSlot(cart.slot());
                placeMinecartOn(basePos);
            });
            AsyncManager.sleep(delay);

            if (!finalFlame && finalFirePos != null) {
                runOnClient(() -> {
                    Vec3d fv = new Vec3d(finalFirePos.getX() + 0.5, finalFirePos.getY() + 1.0, finalFirePos.getZ() + 0.5);
                    applyRotation(InteractionUtil.calculateAngle(fv));
                    selectSlot(flint.slot());
                    interactBlock(new BlockHitResult(fv, Direction.UP, finalFirePos, false));
                });
                AsyncManager.sleep(delay);
            }

            runOnClient(() -> {
                Vec3d center = new Vec3d(basePos.getX() + 0.5, basePos.getY() + 1.5, basePos.getZ() + 0.5);
                applyRotation(InteractionUtil.calculateAngle(center));
                selectSlot(crossbow.slot());
                interactItem();
                AutoCartMod.mc.player.swingHand(Hand.MAIN_HAND);
            });
            AsyncManager.sleep(delay);

            runOnClient(() -> {
                if (cfg().swapBack) selectSlot(prevSlot);
                endRotation();
            });
        });
    }

    // ─── cart aura ────────────────────────────────────────────────────────────

    private void handleCartAuraPop(PlayerEntity popTarget) {
        if (AutoCartMod.nullCheck() || cartAuraExecuting) return;
        if (cfg().getMode() != AutoCartConfig.Mode.CrossBow || !cfg().cartAura) return;
        if (popTarget == null || popTarget == AutoCartMod.mc.player) return;

        updateCartAuraTargetMemory();
        boolean isAuraTarget  = cfg().cartAuraTarget  && isRememberedAuraTarget(popTarget);
        boolean isOtherPlayer = cfg().cartOtherPlayer
                && AutoCartMod.mc.player.getPos().distanceTo(popTarget.getPos()) <= 6.0;
        if (!isAuraTarget && !isOtherPlayer) return;

        AutoCartMod.mc.execute(() -> {
            if (AutoCartMod.nullCheck() || cartAuraExecuting || !enabled) return;
            if (cfg().getMode() != AutoCartConfig.Mode.CrossBow || !cfg().cartAura) return;
            if (popTarget.isRemoved()) return;
            if (cfg().totemCheck && isHoldingTotem(popTarget)) return;
            if (!hasBasicCartAuraResources()) return;
            executeCartAura(popTarget);
        });
    }

    private void executeCartAura(PlayerEntity target) {
        cartAuraExecuting = true;
        int delay        = cfg().delay;
        int startDelayMs = cfg().cartAuraDelay * 50;

        async.run(() -> {
            try {
                AsyncManager.sleep(startDelayMs);
                if (AutoCartMod.nullCheck() || !enabled
                        || cfg().getMode() != AutoCartConfig.Mode.CrossBow
                        || !cfg().cartAura) return;

                int[] prevSlotRef = {-1};
                CartAuraPlan plan = callOnClient(() -> {
                    if (cfg().totemCheck && isHoldingTotem(target)) return null;
                    CartAuraPlan p = createCartAuraPlan(target);
                    if (AutoCartMod.mc.player != null)
                        prevSlotRef[0] = AutoCartMod.mc.player.getInventory().selectedSlot;
                    return p;
                });
                if (plan == null) return;

                BlockPos basePos = plan.basePos();
                int prevSlot = prevSlotRef[0];
                Vec3d placeVec = new Vec3d(basePos.getX() + 0.5, basePos.up().getY(), basePos.getZ() + 0.5);

                runOnClient(() -> applyRotation(InteractionUtil.calculateAngle(placeVec)));
                AsyncManager.sleep(delay);

                if (!plan.railExists()) {
                    runOnClient(() -> {
                        if (!isRailBlock(AutoCartMod.mc.world.getBlockState(basePos.up()).getBlock())) {
                            selectSlot(plan.railResult().slot());
                            placeRailOn(basePos);
                        }
                    });
                    AsyncManager.sleep(delay);
                }

                runOnClient(() -> {
                    selectSlot(plan.cartResult().slot());
                    placeMinecartOn(basePos);
                });
                AsyncManager.sleep(delay);

                if (!plan.hasFlame()) {
                    BlockPos firePos = findFirePosition(basePos);
                    if (firePos != null) {
                        runOnClient(() -> {
                            Vec3d fv = new Vec3d(firePos.getX() + 0.5, firePos.getY() + 1.0, firePos.getZ() + 0.5);
                            applyRotation(InteractionUtil.calculateAngle(fv));
                            selectSlot(plan.flintResult().slot());
                            interactBlock(new BlockHitResult(fv, Direction.UP, firePos, false));
                        });
                        AsyncManager.sleep(delay);
                    }
                }

                runOnClient(() -> {
                    Vec3d center = new Vec3d(basePos.getX() + 0.5, basePos.getY() + 1.5, basePos.getZ() + 0.5);
                    applyRotation(InteractionUtil.calculateAngle(center));
                    selectSlot(plan.crossbowResult().slot());
                    interactItem();
                    AutoCartMod.mc.player.swingHand(Hand.MAIN_HAND);
                });
                AsyncManager.sleep(delay);

                runOnClient(() -> {
                    if (cfg().swapBack) selectSlot(prevSlot);
                    endRotation();
                });

            } finally {
                cartAuraExecuting = false;
            }
        });
    }

    private void updateCartAuraTargetMemory() {
        // No standalone Aura module — skip target tracking by UUID/ID
        // CartAura still works via totem pop + proximity check
    }

    private boolean isRememberedAuraTarget(PlayerEntity player) {
        if (player == null) return false;
        if (lastAuraTargetUuid == null) return false;
        if (System.currentTimeMillis() - lastAuraTargetAt > 1000L) return false;
        return player.getUuid().equals(lastAuraTargetUuid) || player.getId() == lastAuraTargetId;
    }

    private CartAuraPlan createCartAuraPlan(PlayerEntity target) {
        if (!isValidCartAuraTarget(target)) return null;
        BlockPos basePos = findCartAuraPosition(target);
        if (basePos == null) return null;

        InventoryUtil.InvResult crossbow = findLoadedCrossbowInHotbar();
        InventoryUtil.InvResult cart     = InventoryUtil.findItemInHotbar(Items.TNT_MINECART);
        if (!crossbow.found() || !cart.found()) return null;

        boolean hasFlame = hasFlameEnchant(AutoCartMod.mc.player.getInventory().getStack(crossbow.slot()));
        InventoryUtil.InvResult flint = InventoryUtil.findItemInHotbar(Items.FLINT_AND_STEEL);
        if (!hasFlame && !flint.found()) return null;

        boolean railExists = isRailBlock(AutoCartMod.mc.world.getBlockState(basePos.up()).getBlock());
        InventoryUtil.InvResult rail = findRailInHotbar();
        if (!railExists && !rail.found()) return null;

        return new CartAuraPlan(basePos, crossbow, rail, cart, flint, hasFlame, railExists);
    }

    private boolean hasBasicCartAuraResources() {
        InventoryUtil.InvResult cbow = findLoadedCrossbowInHotbar();
        InventoryUtil.InvResult cart = InventoryUtil.findItemInHotbar(Items.TNT_MINECART);
        if (!cbow.found() || !cart.found()) return false;
        boolean flame = hasFlameEnchant(AutoCartMod.mc.player.getInventory().getStack(cbow.slot()));
        return flame || InventoryUtil.findItemInHotbar(Items.FLINT_AND_STEEL).found();
    }

    private boolean isValidCartAuraTarget(PlayerEntity t) {
        return t != null && t != AutoCartMod.mc.player && !t.isRemoved();
    }

    private boolean isHoldingTotem(PlayerEntity p) {
        return p.getMainHandStack().getItem() == Items.TOTEM_OF_UNDYING
                || p.getOffHandStack().getItem() == Items.TOTEM_OF_UNDYING;
    }

    // ─── refill ───────────────────────────────────────────────────────────────

    private void handleRefill() {
        if (cfg().getReFill() == AutoCartConfig.ReFillMode.None) { clearRefillState(); return; }
        if (scheduledRefillSwap || AutoCartMod.mc.currentScreen != null) return;
        int targetSlot = cfg().refillSlot - 1;
        if (AutoCartMod.mc.player.getInventory().getStack(targetSlot).getItem() == Items.TNT_MINECART) return;
        InventoryUtil.InvResult cartResult = InventoryUtil.findItemInInventory(Items.TNT_MINECART);
        if (!cartResult.found()) return;
        if (cfg().getReFill() == AutoCartConfig.ReFillMode.Legit && MovementUtil.isMoving()) {
            scheduleRefill(cartResult.slot(), targetSlot, 5L);
        } else {
            doRefillSwap(cartResult.slot(), targetSlot);
        }
    }

    private void scheduleRefill(int invSlot, int hotSlot, long delayMs) {
        scheduledRefillSwap    = true;
        scheduledRefillInvSlot = invSlot;
        scheduledRefillHotSlot = hotSlot;
        scheduledRefillAt      = System.currentTimeMillis() + delayMs;
    }

    private void handleScheduledRefill() {
        if (!scheduledRefillSwap || System.currentTimeMillis() < scheduledRefillAt) return;
        try { doRefillSwap(scheduledRefillInvSlot, scheduledRefillHotSlot); }
        finally { clearRefillState(); }
    }

    private void doRefillSwap(int invSlot, int hotSlot) {
        if (invSlot < 0 || hotSlot < 0 || hotSlot > 8) return;
        if (AutoCartMod.mc.currentScreen != null) return;
        if (AutoCartMod.mc.player.getInventory().getStack(hotSlot).getItem() == Items.TNT_MINECART) return;
        if (AutoCartMod.mc.interactionManager == null) return;
        AutoCartMod.mc.interactionManager.clickSlot(
                AutoCartMod.mc.player.currentScreenHandler.syncId,
                invSlot, hotSlot, SlotActionType.SWAP, AutoCartMod.mc.player);
    }

    private void clearRefillState() {
        scheduledRefillSwap    = false;
        scheduledRefillInvSlot = -1;
        scheduledRefillHotSlot = -1;
        scheduledRefillAt      = -1L;
    }

    // ─── rotation helpers ─────────────────────────────────────────────────────

    private void applyRotation(float[] angle) {
        if (cfg().changeLook) {
            AutoCartMod.mc.player.setYaw(angle[0]);
            AutoCartMod.mc.player.setPitch(angle[1]);
        } else {
            silentRotation = angle;
            rotating       = true;
        }
    }

    private void endRotation() {
        silentRotation = null;
        rotating       = false;
    }

    private float[] getMoveFixRotation() {
        float[] r = silentRotation;
        return enabled && !AutoCartMod.nullCheck() && !cfg().changeLook
                && rotating && r != null && !AutoCartMod.mc.player.isRiding() ? r : null;
    }

    // ─── interaction helpers ──────────────────────────────────────────────────

    private void selectSlot(int slot) {
        if (AutoCartMod.mc.player == null || AutoCartMod.mc.interactionManager == null) return;
        if (slot < 0 || slot > 8) return;
        AutoCartMod.mc.player.getInventory().selectedSlot = slot;
        ((IInteractionManager) AutoCartMod.mc.interactionManager).syncSlot();
    }

    private void interactItem() {
        if (AutoCartMod.mc.player == null || AutoCartMod.mc.interactionManager == null) return;
        withRotation(() -> AutoCartMod.mc.interactionManager.interactItem(AutoCartMod.mc.player, Hand.MAIN_HAND));
    }

    private void interactBlock(BlockHitResult hit) {
        withRotation(() -> AutoCartMod.mc.interactionManager.interactBlock(AutoCartMod.mc.player, Hand.MAIN_HAND, hit));
        AutoCartMod.mc.player.swingHand(Hand.MAIN_HAND);
    }

    private void placeRailOn(BlockPos base) {
        if (AutoCartMod.mc.world == null || AutoCartMod.mc.player == null) return;
        BlockHitResult hit = new BlockHitResult(
                new Vec3d(base.getX() + 0.5, base.up().getY(), base.getZ() + 0.5), Direction.UP, base, false);
        interactBlock(hit);
    }

    private void placeMinecartOn(BlockPos base) {
        if (AutoCartMod.mc.world == null || AutoCartMod.mc.player == null) return;
        BlockHitResult hit = new BlockHitResult(
                new Vec3d(base.getX() + 0.5, base.up().getY() + 0.125, base.getZ() + 0.5), Direction.UP, base.up(), false);
        interactBlock(hit);
    }

    private void withRotation(Runnable action) {
        if (AutoCartMod.mc.player == null) return;
        float[] r = silentRotation;
        if (!cfg().changeLook && r != null) {
            float py = AutoCartMod.mc.player.getYaw();
            float pp = AutoCartMod.mc.player.getPitch();
            AutoCartMod.mc.player.setYaw(r[0]);
            AutoCartMod.mc.player.setPitch(r[1]);
            try { action.run(); } finally {
                AutoCartMod.mc.player.setYaw(py);
                AutoCartMod.mc.player.setPitch(pp);
            }
        } else {
            action.run();
        }
    }

    // ─── threading helpers ────────────────────────────────────────────────────

    private void runOnClient(Runnable action) {
        if (AutoCartMod.mc.isOnThread()) { action.run(); return; }
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<RuntimeException> err = new AtomicReference<>();
        AutoCartMod.mc.execute(() -> {
            try { action.run(); } catch (RuntimeException e) { err.set(e); } finally { latch.countDown(); }
        });
        try { latch.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        if (err.get() != null) throw err.get();
    }

    private <T> T callOnClient(Supplier<T> s) {
        AtomicReference<T> ref = new AtomicReference<>();
        runOnClient(() -> ref.set(s.get()));
        return ref.get();
    }

    // ─── bow trajectory ───────────────────────────────────────────────────────

    private BlockPos calcBowTrajectory(float yaw) {
        if (AutoCartMod.mc.player == null || AutoCartMod.mc.world == null) return null;

        double x = AutoCartMod.mc.player.getX();
        double y = AutoCartMod.mc.player.getY()
                + AutoCartMod.mc.player.getEyeHeight(AutoCartMod.mc.player.getPose()) - 0.1;
        double z = AutoCartMod.mc.player.getZ();

        float pitch = AutoCartMod.mc.player.getPitch();
        double mx = -MathHelper.sin(yaw / 180f * (float) Math.PI) * MathHelper.cos(pitch / 180f * (float) Math.PI);
        double my = -MathHelper.sin(pitch / 180f * (float) Math.PI);
        double mz =  MathHelper.cos(yaw / 180f * (float) Math.PI) * MathHelper.cos(pitch / 180f * (float) Math.PI);

        float power = AutoCartMod.mc.player.getItemUseTime() / 20f;
        power = (power * power + power * 2f) / 3f;
        if (power > 1f) power = 1f;
        if (power < 0.1f) return null;

        float len = MathHelper.sqrt((float)(mx*mx + my*my + mz*mz));
        float pow = power * 3f;
        mx = mx / len * pow;
        my = my / len * pow;
        mz = mz / len * pow;

        if (!AutoCartMod.mc.player.isOnGround()) my += AutoCartMod.mc.player.getVelocity().getY();

        for (int i = 0; i < 300; i++) {
            Vec3d prev = new Vec3d(x, y, z);
            x += mx; y += my; z += mz;
            mx *= 0.99; my *= 0.99; mz *= 0.99;
            my -= 0.05;

            for (Entity ent : AutoCartMod.mc.world.getEntities()) {
                if (!(ent instanceof ArrowEntity) && ent != AutoCartMod.mc.player
                        && ent.getBoundingBox().intersects(new Box(x-0.3,y-0.3,z-0.3,x+0.3,y+0.3,z+0.3)))
                    return null;
            }

            BlockHitResult bhr = AutoCartMod.mc.world.raycast(new RaycastContext(
                    prev, new Vec3d(x,y,z), RaycastContext.ShapeType.OUTLINE,
                    RaycastContext.FluidHandling.NONE, AutoCartMod.mc.player));
            if (bhr != null && bhr.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK) return bhr.getBlockPos();
            if (y <= -65.0) break;
        }
        return null;
    }

    // ─── cart aura position ───────────────────────────────────────────────────

    private BlockPos findCartAuraPosition(Entity target) {
        if (AutoCartMod.mc.player == null || AutoCartMod.mc.world == null) return null;
        Vec3d playerEyes = AutoCartMod.mc.player.getEyePos();
        Box   targetBox  = target.getBoundingBox();
        Vec3d targetFeet = new Vec3d(target.getX(), targetBox.minY, target.getZ());
        BlockPos tbp = target.getBlockPos();
        int r = 4;
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;

        for (int px = tbp.getX()-r; px <= tbp.getX()+r; px++) {
        for (int pz = tbp.getZ()-r; pz <= tbp.getZ()+r; pz++) {
        for (int py = tbp.getY()-r; py <= tbp.getY(); py++) {
            BlockPos bp = new BlockPos(px, py, pz);
            if (!AutoCartMod.mc.world.getBlockState(bp).isSolid()) continue;
            if (bp.getY() + 1 > targetBox.minY) continue;
            BlockState above = AutoCartMod.mc.world.getBlockState(bp.up());
            if (!above.isReplaceable() && !isRailBlock(above.getBlock())) continue;
            Vec3d surface = new Vec3d(bp.getX()+0.5, bp.getY()+1.0, bp.getZ()+0.5);
            if (PlayerUtil.squaredDistanceFromEyes(surface) > 20.25f) continue;
            BlockHitResult placeCheck = AutoCartMod.mc.world.raycast(new RaycastContext(
                    playerEyes, surface, RaycastContext.ShapeType.COLLIDER,
                    RaycastContext.FluidHandling.NONE, AutoCartMod.mc.player));
            if (placeCheck != null && placeCheck.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK
                    && !placeCheck.getBlockPos().equals(bp)) continue;
            if (!isPathClearOfPlayers(playerEyes, surface, target)) continue;
            Vec3d center = new Vec3d(bp.getX()+0.5, bp.getY()+1.5, bp.getZ()+0.5);
            BlockHitResult dmgCheck = AutoCartMod.mc.world.raycast(new RaycastContext(
                    center, targetFeet, RaycastContext.ShapeType.COLLIDER,
                    RaycastContext.FluidHandling.NONE, AutoCartMod.mc.player));
            if (dmgCheck != null && dmgCheck.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK) continue;
            double dist = center.squaredDistanceTo(targetFeet);
            if (dist < bestDist) { bestDist = dist; best = bp; }
        }}}
        return best;
    }

    private boolean isPathClearOfPlayers(Vec3d from, Vec3d to, Entity exclude) {
        if (AutoCartMod.mc.world == null) return true;
        for (PlayerEntity p : AutoCartMod.mc.world.getPlayers()) {
            if (p != AutoCartMod.mc.player && p != exclude && p.getBoundingBox().raycast(from, to).isPresent())
                return false;
        }
        return true;
    }

    // ─── fire position ────────────────────────────────────────────────────────

    private BlockPos findFirePosition(BlockPos targetPos) {
        if (AutoCartMod.mc.player == null || AutoCartMod.mc.world == null) return null;
        Vec3d eyePos      = AutoCartMod.mc.player.getEyePos();
        Vec3d minecartPos = new Vec3d(targetPos.getX()+0.5, targetPos.getY()+1.5, targetPos.getZ()+0.5);
        Vec3d dir = minecartPos.subtract(eyePos).normalize();
        Direction[] h = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;

        for (Direction d : h) {
            BlockPos c = targetPos.offset(d);
            if (!AutoCartMod.mc.world.getBlockState(c).isSolid() || !AutoCartMod.mc.world.isAir(c.up())) continue;
            Vec3d fc = new Vec3d(c.getX()+0.5, c.getY()+1.0, c.getZ()+0.5);
            double t = fc.subtract(eyePos).dotProduct(dir);
            if (t <= 0) continue;
            double tMax = minecartPos.subtract(eyePos).dotProduct(dir);
            if (t >= tMax) continue;
            double score = eyePos.add(dir.multiply(t)).distanceTo(fc);
            if (score < bestScore) { bestScore = score; best = c; }
        }
        if (best == null) {
            for (Direction d : h) {
                BlockPos c = targetPos.offset(d);
                if (AutoCartMod.mc.world.getBlockState(c).isSolid() && AutoCartMod.mc.world.isAir(c.up())) return c;
            }
        }
        return best;
    }

    // ─── misc helpers ─────────────────────────────────────────────────────────

    private BlockPos getCartBasePos(BlockPos target) {
        BlockState s = AutoCartMod.mc.world.getBlockState(target);
        return (!isRailBlock(s.getBlock()) && !s.isReplaceable()) ? target : target.down();
    }

    private InventoryUtil.InvResult findRailInHotbar() {
        return InventoryUtil.findItemInHotbar(Items.RAIL, Items.ACTIVATOR_RAIL,
                Items.DETECTOR_RAIL, Items.POWERED_RAIL);
    }

    private InventoryUtil.InvResult findLoadedCrossbowInHotbar() {
        return InventoryUtil.findInHotbar(this::isCrossbowCharged);
    }

    private boolean isCrossbowCharged(ItemStack stack) {
        if (stack.getItem() != Items.CROSSBOW) return false;
        ChargedProjectilesComponent c = stack.get(DataComponentTypes.CHARGED_PROJECTILES);
        return c != null && !c.isEmpty();
    }

    private boolean isRailBlock(Block block) {
        return block == Blocks.RAIL || block == Blocks.POWERED_RAIL
                || block == Blocks.DETECTOR_RAIL || block == Blocks.ACTIVATOR_RAIL;
    }

    public boolean hasFlameEnchant(ItemStack stack) {
        if (AutoCartMod.mc.world == null) return false;
        try {
            var reg = AutoCartMod.mc.world.getRegistryManager()
                    .getOptional(RegistryKeys.ENCHANTMENT).orElse(null);
            if (reg == null) return false;
            return reg.getEntry(Enchantments.FLAME.getValue())
                    .map(e -> EnchantmentHelper.getLevel(e, stack) > 0)
                    .orElse(false);
        } catch (Exception e) { return false; }
    }

    private BlockHitResult rayFromEyes(double range) {
        if (AutoCartMod.mc.player == null || AutoCartMod.mc.world == null) return null;
        Vec3d eye  = AutoCartMod.mc.player.getEyePos();
        Vec3d look = AutoCartMod.mc.player.getRotationVec(1f);
        return AutoCartMod.mc.world.raycast(new RaycastContext(
                eye, eye.add(look.multiply(range)),
                RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE,
                AutoCartMod.mc.player));
    }

    // ─── records ──────────────────────────────────────────────────────────────

    private record CartAuraPlan(
            BlockPos basePos,
            InventoryUtil.InvResult crossbowResult,
            InventoryUtil.InvResult railResult,
            InventoryUtil.InvResult cartResult,
            InventoryUtil.InvResult flintResult,
            boolean hasFlame,
            boolean railExists
    ) {}
}
