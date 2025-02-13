package keystrokesmod.module.impl.player;

import keystrokesmod.Raven;
import keystrokesmod.event.PreMotionEvent;
import keystrokesmod.event.PreUpdateEvent;
import keystrokesmod.event.ReceivePacketEvent;
import keystrokesmod.event.SlotUpdateEvent;
import keystrokesmod.mixin.interfaces.IMixinItemRenderer;
import keystrokesmod.module.Module;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.impl.movement.LongJump;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.utility.*;
import keystrokesmod.utility.Timer;
import net.minecraft.block.Block;
import net.minecraft.block.BlockAir;
import net.minecraft.block.BlockTNT;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S27PacketExplosion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.*;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class Scaffold extends Module {
    private final SliderSetting motion;
    public SliderSetting rotation;
    private SliderSetting sprint;
    private SliderSetting fastScaffold;
    private SliderSetting multiPlace;
    public ButtonSetting autoSwap;
    private ButtonSetting cancelKnockBack;
    private ButtonSetting fastOnRMB;
    public ButtonSetting highlightBlocks;
    private ButtonSetting jumpFacingForward;
    public ButtonSetting safeWalk;
    public ButtonSetting showBlockCount;
    private ButtonSetting silentSwing;

    private String[] rotationModes = new String[] { "None", "Simple", "Offset", "Precise" };
    private String[] sprintModes = new String[] { "None", "Vanilla", "Float" };
    private String[] fastScaffoldModes = new String[] { "None", "Jump B", "Jump B Low", "Jump E", "Keep-Y", "Keep-Y Low", "Jump Nigger" };
    private String[] multiPlaceModes = new String[] { "Disabled", "1 extra", "2 extra" };

    public Map<BlockPos, Timer> highlight = new HashMap<>();

    private ScaffoldBlockCount scaffoldBlockCount;

    public AtomicInteger lastSlot = new AtomicInteger(-1);

    public boolean hasSwapped;
    private boolean hasPlaced;

    private boolean rotateForward;
    private double startYPos = -1;
    public boolean fastScaffoldKeepY;
    private boolean firstKeepYPlace;
    private boolean rotatingForward;
    private int keepYTicks;
    public boolean lowhop;
    private int rotationDelay;
    private int blockSlot = -1;

    public boolean canBlockFade;

    private boolean floatJumped;
    private boolean floatStarted;
    private boolean floatWasEnabled;
    private boolean floatKeepY;

    private Vec3 targetBlock;
    private PlaceData blockInfo;
    private Vec3 blockPos, hitVec, lookVec;
    private float[] blockRotations;
    private long lastPlaceTime, rotationTimeout = 250L;
    private float lastYaw = 0.0f;
    private float lastPitch = 0.0f;
    public float yaw, pitch, blockYaw, groundYaw, yawOffset, lastYawOffset;
    private boolean set2;

    public boolean moduleEnabled;
    public boolean isEnabled;
    private boolean disabledModule;
    private boolean dontDisable, towerEdge;
    private int disableTicks;
    private int scaffoldTicks;

    private boolean was451, was452;

    private float minOffset, pOffset, minPitch;

    private float edge;

    private long firstStroke;
    private float lastEdge, lastEdge2, yawAngle, theYaw;
    private boolean lastAir;
    private double thisX, thisY, thisZ;

    private int speedEdge;

    private EnumFacing[] facings = { EnumFacing.EAST, EnumFacing.WEST, EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.UP };
    private BlockPos[] offsets = { new BlockPos(-1, 0, 0), new BlockPos(1, 0, 0), new BlockPos(0, 0, 1), new BlockPos(0, 0, -1), new BlockPos(0, -1, 0) };

    //private final SliderSetting offsetAmount;

    public Scaffold() {
        super("Scaffold", category.player);
        this.registerSetting(motion = new SliderSetting("Motion", "%", 100, 50, 150, 1));
        this.registerSetting(rotation = new SliderSetting("Rotation", 1, rotationModes));
        this.registerSetting(sprint = new SliderSetting("Sprint mode", 0, sprintModes));
        this.registerSetting(fastScaffold = new SliderSetting("Fast scaffold", 0, fastScaffoldModes));
        this.registerSetting(multiPlace = new SliderSetting("Multi-place", 0, multiPlaceModes));
        this.registerSetting(autoSwap = new ButtonSetting("Auto swap", true));
        this.registerSetting(cancelKnockBack = new ButtonSetting("Cancel knockback", false));
        this.registerSetting(fastOnRMB = new ButtonSetting("Fast on RMB", true));
        this.registerSetting(highlightBlocks = new ButtonSetting("Highlight blocks", true));
        this.registerSetting(jumpFacingForward = new ButtonSetting("Jump facing forward", false));
        this.registerSetting(safeWalk = new ButtonSetting("Safewalk", true));
        this.registerSetting(showBlockCount = new ButtonSetting("Show block count", true));
        this.registerSetting(silentSwing = new ButtonSetting("Silent swing", false));
        //this.registerSetting(pitchOffset = new SliderSetting("Pitch offset", "", 9, 0, 30, 1));

        //this.registerSetting(offsetAmount = new SliderSetting("Offset amount", "%", 100, 0, 100, 0.25));

        this.alwaysOn = true;
    }

    public void onDisable() {
        if (ModuleManager.tower.canTower() && (ModuleManager.tower.dCount == 0 || !Utils.isMoving())) {
            towerEdge = true;
        }
        disabledModule = true;
        moduleEnabled = false;
    }

    public void onEnable() {
        isEnabled = true;
        moduleEnabled = true;
        ModuleUtils.fadeEdge = 0;
        edge = -999999929;

        FMLCommonHandler.instance().bus().register(scaffoldBlockCount = new ScaffoldBlockCount(mc));
        lastSlot.set(-1);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onMouse(MouseEvent e) {
        if (!isEnabled) {
            return;
        }
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindAttack.getKeyCode(), false);
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), false);
        if (e.button >= 0) {
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onPreMotion(PreMotionEvent e) {
        if (!Utils.nullCheck()) {
            return;
        }
        if (!isEnabled) {
            return;
        }
        if (Utils.isMoving()) {
            scaffoldTicks++;
        }
        else {
            scaffoldTicks = 0;
        }
        canBlockFade = true;
        int simpleY = (int) Math.round((e.posY % 1) * 10000);
        if (Utils.keysDown() && usingFastScaffold() && fastScaffold.getInput() >= 1 && !ModuleManager.tower.canTower() && !ModuleManager.LongJump.function) { // jump mode
            if (mc.thePlayer.onGround && Utils.isMoving()) {
                if (scaffoldTicks > 1) {
                    rotateForward();
                    mc.thePlayer.jump();
                    Utils.setSpeed(getSpeed(getSpeedLevel()) - Utils.randomizeDouble(0.0003, 0.0001));
                    speedEdge++;
                    if (fastScaffold.getInput() == 5 || fastScaffold.getInput() == 2 && firstKeepYPlace) {
                        lowhop = true;
                    }
                    //Utils.print("Keepy");
                    if (startYPos == -1 || Math.abs(startYPos - e.posY) > 5) {
                        startYPos = e.posY;
                        fastScaffoldKeepY = true;
                    }
                }
            }
        }
        else if (fastScaffoldKeepY) {
            fastScaffoldKeepY = firstKeepYPlace = false;
            startYPos = -1;
            keepYTicks = speedEdge = 0;
        }

        //Float
        if (sprint.getInput() == 2 && !usingFastScaffold() && !ModuleManager.bhop.isEnabled() && !ModuleManager.tower.canTower() && !ModuleManager.LongJump.function) {
            floatWasEnabled = true;
            if (!floatStarted) {
                if (ModuleUtils.groundTicks > 8 && mc.thePlayer.onGround) {
                    floatKeepY = true;
                    startYPos = e.posY;
                    mc.thePlayer.jump();
                    if (Utils.isMoving()) {
                        Utils.setSpeed(getSpeed(getSpeedLevel()) - Utils.randomizeDouble(0.0003, 0.0001));
                    }
                    floatJumped = true;
                } else if (ModuleUtils.groundTicks <= 8 && mc.thePlayer.onGround) {
                    floatStarted = true;
                }
                if (floatJumped && !mc.thePlayer.onGround) {
                    floatStarted = true;
                }
            }

            if (floatStarted && mc.thePlayer.onGround) {
                floatKeepY = false;
                startYPos = -1;
                if (moduleEnabled) {
                    e.setPosY(e.getPosY() + ModuleUtils.offsetValue);
                    if (Utils.isMoving()) Utils.setSpeed(getFloatSpeed(getSpeedLevel()));
                }
            }
        } else if (floatWasEnabled && moduleEnabled) {
            if (floatKeepY) {
                startYPos = -1;
            }
            floatStarted = floatJumped = floatKeepY = floatWasEnabled = false;
        }


        if (targetBlock != null) {
            Vec3 lookAt = new Vec3(targetBlock.xCoord - lookVec.xCoord, targetBlock.yCoord - lookVec.yCoord, targetBlock.zCoord - lookVec.zCoord);
            blockRotations = RotationUtils.getRotations(lookAt);
            targetBlock = null;
        }

        switch ((int) rotation.getInput()) {
            case 1:
                yaw = mc.thePlayer.rotationYaw - hardcodedYaw();
                pitch = 80F;
                e.setRotations(yaw, pitch);
                break;
            case 2:
                float moveAngle = (float) getMovementAngle();
                float relativeYaw = mc.thePlayer.rotationYaw + moveAngle;
                float normalizedYaw = (relativeYaw % 360 + 360) % 360;
                float quad = normalizedYaw % 90;

                float side = MathHelper.wrapAngleTo180_float(getMotionYaw() - yaw);
                float yawBackwards = MathHelper.wrapAngleTo180_float(mc.thePlayer.rotationYaw) - hardcodedYaw();
                float blockYawOffset = MathHelper.wrapAngleTo180_float(yawBackwards - blockYaw);

                long strokeDelay = 225;

                if (quad <= 5 || quad >= 85) {
                    yawAngle = 127F;//(float) first.getInput();
                    minOffset = 18;//(int) first1.getInput();
                    minPitch = 71.08F;
                }
                if (quad > 5 && quad <= 15 || quad >= 75 && quad < 85) {
                    yawAngle = 128F;//(float) second.getInput();
                    minOffset = 15;//(int) second1.getInput();
                    minPitch = 71.84F;
                }
                if (quad > 15 && quad <= 25 || quad >= 65 && quad < 75) {
                    yawAngle = 129F;//(float) three.getInput();
                    minOffset = 12;//(int) three1.getInput();
                    minPitch = 72.45F;
                }
                if (quad > 25 && quad <= 32 || quad >= 58 && quad < 65) {
                    yawAngle = 130F;//(float) four.getInput();
                    minOffset = 9;//(int) four1.getInput();
                    minPitch = 75.43F;
                }
                if (quad > 32 && quad <= 38 || quad >= 52 && quad < 58) {
                    yawAngle = 131F;//(float) five.getInput();
                    minOffset = 7;//(int) five1.getInput();
                    minPitch = 75.8F;
                }
                if (quad > 38 && quad <= 42 || quad >= 48 && quad < 52) {
                    yawAngle = 132.50F;//(float) six.getInput();
                    minOffset = 5;//(int) six1.getInput();
                    minPitch = 76.84F;
                }
                if (quad > 42 && quad <= 45 || quad >= 45 && quad < 48) {
                    yawAngle = 137F;//(float) seven.getInput();
                    minOffset = 3;//(int) seven1.getInput();
                    minPitch = 78.1F;
                }
                //float offsetAmountD = ((((float) offsetAmount.getInput() / 10) - 10) * -2) - (((float) offsetAmount.getInput() / 10) - 10);
                //yawAngle += offsetAmountD;
                //Utils.print("" + offsetAmountD);

                float offset = yawAngle;//(!Utils.scaffoldDiagonal(false)) ? 125.500F : 143.500F;


                if (firstStroke > 0 && (System.currentTimeMillis() - firstStroke) > strokeDelay) {
                    firstStroke = 0;
                }

                if (blockRotations != null) {
                    blockYaw = blockRotations[0];
                    pitch = blockRotations[1];
                    yawOffset = blockYawOffset;
                    if (pitch < minPitch) {
                        pitch = minPitch;
                    }
                } else {
                    pitch = 80F;
                    if (edge == 1) {
                        firstStroke = Utils.time();
                    }
                    yawOffset = 0;
                }
                //minOffset = 0;//turning this off for now
                Block blockBelow = BlockUtils.getBlock(new BlockPos(Utils.getPosDirectionX(2), mc.thePlayer.posY - 1, Utils.getPosDirectionZ(2)));
                lastAir = blockBelow instanceof BlockAir;

                if (!Utils.isMoving() || Utils.getHorizontalSpeed() == 0.0D) {
                    e.setRotations(theYaw, pitch);
                    break;
                }

                float motionYaw = getMotionYaw();

                float newYaw = motionYaw - offset * Math.signum(
                        MathHelper.wrapAngleTo180_float(motionYaw - yaw)
                );
                yaw = MathHelper.wrapAngleTo180_float(newYaw);

                if (quad > 5 && quad < 85) {
                    if (quad < 45F) {
                        if (firstStroke == 0) {
                            if (side >= 0) {
                                set2 = false;
                            } else {
                                set2 = true;
                            }
                        }
                        if (was452) {
                            firstStroke = Utils.time();
                        }
                        was451 = true;
                        was452 = false;
                    } else {
                        if (firstStroke == 0) {
                            if (side >= 0) {
                                set2 = true;
                            } else {
                                set2 = false;
                            }
                        }
                        if (was451) {
                            firstStroke = Utils.time();
                        }
                        was452 = true;
                        was451 = false;
                    }
                }

                double minSwitch = (!Utils.scaffoldDiagonal(false)) ? 9 : 15;
                if (side >= 0) {
                    if (yawOffset <= -minSwitch && firstStroke == 0) {
                        if (quad <= 5 || quad >= 85) {
                            if (set2) {
                                firstStroke = Utils.time();
                            }
                            set2 = false;
                        }
                    } else if (yawOffset >= 0 && firstStroke == 0) {
                        if (quad <= 5 || quad >= 85) {
                            if (yawOffset >= minSwitch) {
                                if (!set2) {
                                    firstStroke = Utils.time();
                                }
                                set2 = true;
                            }
                        }
                    }
                    if (set2) {
                        if (yawOffset <= -0) yawOffset = -0;
                        if (yawOffset >= minOffset) yawOffset = minOffset;
                        e.setRotations((yaw + offset * 2) - yawOffset, pitch);
                        theYaw = e.getYaw();
                        break;
                    }
                } else if (side <= -0) {
                    if (yawOffset >= minSwitch && firstStroke == 0) {
                        if (quad <= 5 || quad >= 85) {
                            if (set2) {
                                firstStroke = Utils.time();
                            }
                            set2 = false;
                        }
                    } else if (yawOffset <= 0 && firstStroke == 0) {
                        if (quad <= 5 || quad >= 85) {
                            if (yawOffset <= -minSwitch) {
                                if (!set2) {
                                    firstStroke = Utils.time();
                                }
                                set2 = true;
                            }
                        }
                    }
                    if (set2) {
                        if (yawOffset >= 0) yawOffset = 0;
                        if (yawOffset <= -minOffset) yawOffset = -minOffset;
                        e.setRotations((yaw - offset * 2) - yawOffset, pitch);
                        theYaw = e.getYaw();
                        break;
                    }
                }

                if (side >= 0) {
                    if (yawOffset >= 0) yawOffset = 0;
                    if (yawOffset <= -minOffset) yawOffset = -minOffset;
                } else if (side <= -0) {
                    if (yawOffset <= -0) yawOffset = -0;
                    if (yawOffset >= minOffset) yawOffset = minOffset;
                }
                e.setRotations(yaw - yawOffset, pitch);
                theYaw = e.getYaw();
                break;
            case 3:
                if (blockRotations != null) {
                    yaw = blockRotations[0];
                    pitch = blockRotations[1];
                }
                else {
                    yaw = mc.thePlayer.rotationYaw - hardcodedYaw();
                    pitch = 80F;
                }

                e.setRotations(yaw, pitch);
                theYaw = e.getYaw();
                break;
        }
        if (edge != 1) {
            firstStroke = Utils.time();
            edge = 1;
        }

        //get yaw - player yaw offset
        float yv = MathHelper.wrapAngleTo180_float(mc.thePlayer.rotationYaw) - hardcodedYaw();
        if (Raven.debug) {
            Utils.sendModuleMessage(this, "" + MathHelper.wrapAngleTo180_float(yv - e.getYaw()) + " " + e.getPitch() + " " + minOffset);
        }

        //Utils.print("" + mc.thePlayer.rotationYaw + " " + mc.thePlayer.rotationPitch);

        //jump facing forward
        if (ModuleUtils.inAirTicks >= 1) {
            rotateForward = false;
        }
        if (rotateForward && jumpFacingForward.isToggled()) {
            if (rotation.getInput() > 0) {
                if (!rotatingForward) {
                    rotationDelay = 2;
                    rotatingForward = true;
                }
                float forwardYaw = (mc.thePlayer.rotationYaw - hardcodedYaw() - 180);
                e.setYaw(forwardYaw);
                e.setPitch(10);
            }
        }
        else {
            rotatingForward = false;
        }

        if (ModuleManager.tower.isVerticalTowering()) {
            if (blockRotations != null) {
                e.setYaw(blockRotations[0]);
            }
            if (ModuleManager.tower.yaw != 0) {
                e.setYaw(ModuleManager.tower.yaw);
            }
            if (ModuleManager.tower.pitch != 0) {
                e.setPitch(ModuleManager.tower.pitch);
            }
        }

        //Smoothing
        float yawDifference = getAngleDifference(lastEdge2, e.getYaw());
        float smoothingFactor = (1.0f - (30.0f / 100.0f));
        if (!mc.thePlayer.onGround) {
            //e.setYaw(lastEdge2 + yawDifference * smoothingFactor);
        }
        lastEdge2 = e.getYaw();

        //pitch fix
        if (e.getPitch() > 89.9F) {
            e.setPitch(89.9F);
        }

        lastYaw = mc.thePlayer.rotationYaw;
        if (lastPlaceTime > 0 && (System.currentTimeMillis() - lastPlaceTime) > rotationTimeout) blockRotations = null;
        if (rotationDelay > 0) --rotationDelay;
    }

    @SubscribeEvent
    public void onSlotUpdate(SlotUpdateEvent e) {
        if (isEnabled) {
            lastSlot.set(e.slot);
            //Utils.print("Switched slot | " + e.slot);
        }
    }

    @SubscribeEvent
    public void onPreUpdate(PreUpdateEvent e) {
        if (!isEnabled) {
            return;
        }
        if (LongJump.function) {
            startYPos = -1;
        }
        if (holdingBlocks() && setSlot()) {

            if (LongJump.stopModules) {
                //Utils.print("Stopped scaffold due to longjump swapping");
                return;
            }
            if (ModuleManager.killAura.isTargeting || ModuleManager.killAura.justUnTargeted) {
                //Utils.print("Stopped scaffold due to ka untargeting");
                return;
            }

            hasSwapped = true;
            int mode = (int) fastScaffold.getInput();
            if (rotation.getInput() == 0 || rotationDelay == 0) {
                placeBlock(0, 0);
            }
            if (ModuleManager.tower.placeExtraBlock) {
                placeBlock(0, -1);
            }
            if (fastScaffoldKeepY && !ModuleManager.tower.canTower()) {
                ++keepYTicks;
                if ((int) mc.thePlayer.posY > (int) startYPos) {
                    switch (mode) {
                        case 1:
                            if (!firstKeepYPlace && keepYTicks == 8 || keepYTicks == 11) {
                                placeBlock(1, 0);
                                firstKeepYPlace = true;
                            }
                            break;
                        case 2:
                            if (!firstKeepYPlace && keepYTicks == 8 || firstKeepYPlace && keepYTicks == 7) {
                                placeBlock(1, 0);
                                firstKeepYPlace = true;
                            }
                            break;
                        case 3:
                            if (!firstKeepYPlace && keepYTicks == 7) {
                                placeBlock(1, 0);
                                firstKeepYPlace = true;
                            }
                            break;
                        case 6:
                            if (!firstKeepYPlace && keepYTicks == 3) {
                                placeBlock(1, 0);
                                firstKeepYPlace = true;
                            }
                            break;
                    }
                }
                if (mc.thePlayer.onGround) keepYTicks = 0;
                if ((int) mc.thePlayer.posY == (int) startYPos) firstKeepYPlace = false;
            }
            handleMotion();
        }

        if (disabledModule) {
            if (hasPlaced && (towerEdge || floatStarted && Utils.isMoving())) {
                dontDisable = true;
            }

            if (dontDisable && ++disableTicks >= 2) {
                isEnabled = false;
                //Utils.print("Extra tick");
            }
            if (!dontDisable) {
                isEnabled = false;
            }


            if (!isEnabled) {
                disabledModule = dontDisable = false;
                disableTicks = 0;
                //Utils.print("Disabled");

                if (ModuleManager.tower.speed) {
                    Utils.setSpeed(Utils.getHorizontalSpeed(mc.thePlayer) / 1.6);
                }

                if (lastSlot.get() != -1) {
                    mc.thePlayer.inventory.currentItem = lastSlot.get();
                    lastSlot.set(-1);
                }
                blockSlot = -1;
                if (autoSwap.isToggled() && ModuleManager.autoSwap.spoofItem.isToggled()) {
                    ((IMixinItemRenderer) mc.getItemRenderer()).setCancelUpdate(false);
                    ((IMixinItemRenderer) mc.getItemRenderer()).setCancelReset(false);
                }
                scaffoldBlockCount.beginFade();
                hasSwapped = hasPlaced = false;
                targetBlock = null;
                blockInfo = null;
                blockRotations = null;
                fastScaffoldKeepY = firstKeepYPlace = rotateForward = rotatingForward = floatStarted = floatJumped = floatWasEnabled = towerEdge =
                was451 = was452 = lastAir = false;
                rotationDelay = keepYTicks = scaffoldTicks = speedEdge = 0;
                firstStroke = 0;
                startYPos = -1;
                lookVec = null;
            }
        }
    }

    @SubscribeEvent
    public void onReceivePacket(ReceivePacketEvent e) {
        if (!isEnabled) {
            return;
        }
        if (!Utils.nullCheck() || !cancelKnockBack.isToggled()) {
            return;
        }
        if (e.getPacket() instanceof S12PacketEntityVelocity) {
            if (((S12PacketEntityVelocity) e.getPacket()).getEntityID() == mc.thePlayer.getEntityId()) {
                e.setCanceled(true);
            }
        }
        else if (e.getPacket() instanceof S27PacketExplosion) {
            e.setCanceled(true);
        }
    }

    @Override
    public String getInfo() {
        String info;
        if (fastOnRMB.isToggled()) {
            info = Mouse.isButtonDown(1) && Utils.tabbedIn() ? fastScaffoldModes[(int) fastScaffold.getInput()] : sprintModes[(int) sprint.getInput()];
        }
        else {
            info = fastScaffold.getInput() > 0 ? fastScaffoldModes[(int) fastScaffold.getInput()] : sprintModes[(int) sprint.getInput()];
        }
        return info;
    }

    public boolean stopFastPlace() {
        return this.isEnabled();
    }

    float getAngleDifference(float from, float to) {
        float difference = (to - from) % 360.0F;
        if (difference < -180.0F) {
            difference += 360.0F;
        } else if (difference >= 180.0F) {
            difference -= 360.0F;
        }
        return difference;
    }

    public void rotateForward() {
        rotateForward = true;
        rotatingForward = false;
    }

    public boolean blockAbove() {
        return !(BlockUtils.getBlock(new BlockPos(mc.thePlayer.posX, mc.thePlayer.posY + 2, mc.thePlayer.posZ)) instanceof BlockAir);
    }

    public boolean sprint() {
        if (isEnabled) {
            return handleFastScaffolds() > 0 || !holdingBlocks();
        }
        return false;
    }

    private int handleFastScaffolds() {
        if (fastOnRMB.isToggled()) {
            return Mouse.isButtonDown(1) && Utils.tabbedIn() ? (int) fastScaffold.getInput() : (int) sprint.getInput();
        }
        else {
            return fastScaffold.getInput() > 0 ? (int) fastScaffold.getInput() : (int) sprint.getInput();
        }
    }

    private boolean usingFloat() {
        return sprint.getInput() == 2 && Utils.isMoving() && !usingFastScaffold();
    }

    private boolean usingFastScaffold() {
        return fastScaffold.getInput() > 0 && (!fastOnRMB.isToggled() || (Mouse.isButtonDown(1) || ModuleManager.bhop.isEnabled()) && Utils.tabbedIn());
    }

    public boolean safewalk() {
        return this.isEnabled() && safeWalk.isToggled();
    }

    public boolean stopRotation() {
        return this.isEnabled() && rotation.getInput() > 0;
    }

    private void place(PlaceData block) {
        ItemStack heldItem = mc.thePlayer.getHeldItem();
        if (heldItem == null || !(heldItem.getItem() instanceof ItemBlock) || !Utils.canBePlaced((ItemBlock) heldItem.getItem())) {
            return;
        }
        if (mc.playerController.onPlayerRightClick(mc.thePlayer, mc.theWorld, heldItem, block.blockPos, block.enumFacing, block.hitVec)) {
            if (silentSwing.isToggled()) {
                mc.thePlayer.sendQueue.addToSendQueue(new C0APacketAnimation());
            }
            else {
                mc.thePlayer.swingItem();
                if (!(autoSwap.isToggled() && ModuleManager.autoSwap.spoofItem.isToggled())) {
                    mc.getItemRenderer().resetEquippedProgress();
                }
            }
            highlight.put(block.blockPos.offset(block.enumFacing), null);
            hasPlaced = true;
        }
    }

    public boolean canSafewalk() {
        if (usingFastScaffold()) {
            return false;
        }
        if (ModuleManager.tower.canTower()) {
            return false;
        }
        if (!isEnabled) {
            return false;
        }
        return true;
    }

    public int totalBlocks() {
        int totalBlocks = 0;
        for (int i = 0; i < 9; ++i) {
            final ItemStack stack = mc.thePlayer.inventory.mainInventory[i];
            if (stack != null && stack.getItem() instanceof ItemBlock && Utils.canBePlaced((ItemBlock) stack.getItem()) && stack.stackSize > 0) {
                totalBlocks += stack.stackSize;
            }
        }
        return totalBlocks;
    }

    private void placeBlock(int yOffset, int xOffset) {
        locateAndPlaceBlock(yOffset, xOffset);
        int input = (int) multiPlace.getInput();
        if (sprint.getInput() == 0 && mc.thePlayer.onGround && !ModuleManager.tower.canTower() && !usingFastScaffold()) {
            return;
        }
        if (ModuleManager.tower.canTower() && !ModuleManager.tower.tower) {
            return;
        }
        if (input >= 1) {
            locateAndPlaceBlock(yOffset, xOffset);
            if (input >= 2) {
                locateAndPlaceBlock(yOffset, xOffset);
            }
        }
    }

    private void locateAndPlaceBlock(int yOffset, int xOffset) {
        locateBlocks(yOffset, xOffset);
        if (blockInfo == null) {
            return;
        }
        place(blockInfo);
        blockInfo = null;
    }

    private void locateBlocks(int yOffset, int xOffset) {
        List<PlaceData> blocksInfo = findBlocks(yOffset, xOffset);

        if (blocksInfo == null) {
            return;
        }

        double sumX = 0, sumY = !mc.thePlayer.onGround ? 0 : blocksInfo.get(0).blockPos.getY(), sumZ = 0;
        int index = 0;
        for (PlaceData blockssInfo : blocksInfo) {
            if (index > 1 || (!Utils.isDiagonal(false) && index > 0 && mc.thePlayer.onGround)) {
                break;
            }
            sumX += blockssInfo.blockPos.getX();
            if (!mc.thePlayer.onGround) {
                sumY += blockssInfo.blockPos.getY();
            }
            sumZ += blockssInfo.blockPos.getZ();
            index++;
        }

        double avgX = sumX / index;
        double avgY = !mc.thePlayer.onGround ? sumY / index : blocksInfo.get(0).blockPos.getY();
        double avgZ = sumZ / index;

        targetBlock = new Vec3(avgX, avgY, avgZ);

        PlaceData blockInfo2 = blocksInfo.get(0);
        int blockX = blockInfo2.blockPos.getX();
        int blockY = blockInfo2.blockPos.getY();
        int blockZ = blockInfo2.blockPos.getZ();
        EnumFacing blockFacing = blockInfo2.enumFacing;
        blockInfo = blockInfo2;

        double hitX = (blockX + 0.5D) + getCoord(blockFacing.getOpposite(), "x") * 0.5D;
        double hitY = (blockY + 0.5D) + getCoord(blockFacing.getOpposite(), "y") * 0.5D;
        double hitZ = (blockZ + 0.5D) + getCoord(blockFacing.getOpposite(), "z") * 0.5D;
        lookVec = new Vec3(0.5D + getCoord(blockFacing.getOpposite(), "x") * 0.5D, 0.5D + getCoord(blockFacing.getOpposite(), "y") * 0.5D, 0.5D + getCoord(blockFacing.getOpposite(), "z") * 0.5D);
        hitVec = new Vec3(hitX, hitY, hitZ);
        blockInfo.hitVec = hitVec;
    }

    private double getCoord(EnumFacing facing, String axis) {
        switch (axis) {
            case "x": return (facing == EnumFacing.WEST) ? -0.5 : (facing == EnumFacing.EAST) ? 0.5 : 0;
            case "y": return (facing == EnumFacing.DOWN) ? -0.5 : (facing == EnumFacing.UP) ? 0.5 : 0;
            case "z": return (facing == EnumFacing.NORTH) ? -0.5 : (facing == EnumFacing.SOUTH) ? 0.5 : 0;
        }
        return 0;
    }

    /*private List<PlaceData> findBlocks(int yOffset, int xOffset) {
        List<PlaceData> possibleBlocks = new ArrayList<>();
        int x = (int) Math.floor(mc.thePlayer.posX + xOffset);
        int y = (int) Math.floor(((startYPos != -1) ? startYPos : (mc.thePlayer.posY)) + yOffset);
        int z = (int) Math.floor(mc.thePlayer.posZ);

        BlockPos pos = new BlockPos(x, y - 1, z);

        for (int lastCheck = 0; lastCheck < 2; lastCheck++) {
            for (int i = 0; i < offsets.length; i++) {
                BlockPos newPos = pos.add(offsets[i]);
                Block block = BlockUtils.getBlock(newPos);
                if (lastCheck == 0) {
                    continue;
                }
                if (!block.getMaterial().isReplaceable() && !BlockUtils.isInteractable(block)) {
                    possibleBlocks.add(new PlaceData(facings[i], newPos));
                }
            }
        }
        BlockPos[] additionalOffsets = { // adjust these for perfect placement
                pos.add(-1, 0, 0),
                pos.add(1, 0, 0),
                pos.add(0, 0, 1),
                pos.add(0, 0, -1),
                pos.add(0, -1, 0),

                pos.add(-2, 0, 0),
                pos.add(2, 0, 0),
                pos.add(0, 0, 2),
                pos.add(0, 0, -2),
                pos.add(0, -2, 0),

                pos.add(-3, 0, 0),
                pos.add(3, 0, 0),
                pos.add(0, 0, 3),
                pos.add(0, 0, -3),
                pos.add(0, -3, 0),
        };
        for (int lastCheck = 0; lastCheck < 2; lastCheck++) {
            for (BlockPos additionalPos : additionalOffsets) {
                for (int i = 0; i < offsets.length; i++) {
                    BlockPos newPos = additionalPos.add(offsets[i]);
                    Block block = BlockUtils.getBlock(newPos);
                    if (lastCheck == 0) {
                        continue;
                    }
                    if (!block.getMaterial().isReplaceable() && !BlockUtils.isInteractable(block)) {
                        possibleBlocks.add(new PlaceData(facings[i], newPos));
                    }
                }
            }
        }
        BlockPos[] additionalOffsets2 = { // adjust these for perfect placement
                new BlockPos(-1, 0, 0),
                new BlockPos(1, 0, 0),
                new BlockPos(0, 0, 1),
                new BlockPos(0, 0, -1),
                new BlockPos(0, -1, 0),

                new BlockPos(-2, 0, 0),
                new BlockPos(2, 0, 0),
                new BlockPos(0, 0, 2),
                new BlockPos(0, 0, -2),
                new BlockPos(0, -2, 0),

                new BlockPos(-3, 0, 0),
                new BlockPos(3, 0, 0),
                new BlockPos(0, 0, 3),
                new BlockPos(0, 0, -3),
                new BlockPos(0, -3, 0),
        };
        for (int lastCheck = 0; lastCheck < 2; lastCheck++) {
            for (BlockPos additionalPos2 : additionalOffsets2) {
                for (BlockPos additionalPos : additionalOffsets) {
                    for (int i = 0; i < offsets.length; i++) {
                        BlockPos newPos = additionalPos2.add(additionalPos.add(offsets[i]));
                        Block block = BlockUtils.getBlock(newPos);
                        if (lastCheck == 0) {
                            continue;
                        }
                        if (!block.getMaterial().isReplaceable() && !BlockUtils.isInteractable(block)) {
                            possibleBlocks.add(new PlaceData(facings[i], newPos));
                        }
                    }
                }
            }
        }
        return possibleBlocks.isEmpty() ? null : possibleBlocks;
    }*/

    private List<PlaceData> findBlocks(int yOffset, int xOffset) {
        List<PlaceData> possibleBlocks = new ArrayList<>();
        int x = (int) Math.floor(mc.thePlayer.posX + xOffset);
        int y = (int) Math.floor(((startYPos != -1) ? startYPos : (mc.thePlayer.posY)) + yOffset);
        int z = (int) Math.floor(mc.thePlayer.posZ);

        if (BlockUtils.replaceable(new BlockPos(x, y - 1, z))) {
            for (EnumFacing enumFacing : EnumFacing.values()) {
                if (enumFacing != EnumFacing.UP && placeConditions(enumFacing, yOffset, xOffset)) {
                    BlockPos offsetPos = new BlockPos(x, y - 1, z).offset(enumFacing);
                    if (!BlockUtils.replaceable(offsetPos) && !BlockUtils.isInteractable(BlockUtils.getBlock(offsetPos))) {
                        possibleBlocks.add(new PlaceData(offsetPos, enumFacing.getOpposite()));
                    }
                }
            }
            for (EnumFacing enumFacing2 : EnumFacing.values()) {
                if (enumFacing2 != EnumFacing.UP && placeConditions(enumFacing2, yOffset, xOffset)) {
                    BlockPos offsetPos2 = new BlockPos(x, y - 1, z).offset(enumFacing2);
                    if (BlockUtils.replaceable(offsetPos2)) {
                        for (EnumFacing enumFacing3 : EnumFacing.values()) {
                            if (enumFacing3 != EnumFacing.UP && placeConditions(enumFacing3, yOffset, xOffset)) {
                                BlockPos offsetPos3 = offsetPos2.offset(enumFacing3);
                                if (!BlockUtils.replaceable(offsetPos3) && !BlockUtils.isInteractable(BlockUtils.getBlock(offsetPos3))) {
                                    possibleBlocks.add(new PlaceData(offsetPos3, enumFacing3.getOpposite()));
                                }
                            }
                        }
                    }
                }
            }
            if (mc.thePlayer.motionY > -0.0784) {
                for (EnumFacing enumFacing5 : EnumFacing.values()) {
                    if (enumFacing5 != EnumFacing.UP && placeConditions(enumFacing5, yOffset, xOffset)) {
                        BlockPos offsetPos5 = new BlockPos(x, y - 2, z).offset(enumFacing5);
                        if (BlockUtils.replaceable(offsetPos5)) {
                            for (EnumFacing enumFacing6 : EnumFacing.values()) {
                                if (enumFacing6 != EnumFacing.UP && placeConditions(enumFacing6, yOffset, xOffset)) {
                                    BlockPos offsetPos6 = offsetPos5.offset(enumFacing6);
                                    if (!BlockUtils.replaceable(offsetPos6) && !BlockUtils.isInteractable(BlockUtils.getBlock(offsetPos6))) {
                                        possibleBlocks.add(new PlaceData(offsetPos6, enumFacing6.getOpposite()));
                                    }
                                }
                            }
                        }
                    }
                }
                for (EnumFacing enumFacing7 : EnumFacing.values()) {
                    if (enumFacing7 != EnumFacing.UP && placeConditions(enumFacing7, yOffset, xOffset)) {
                        BlockPos offsetPos7 = new BlockPos(x, y - 3, z).offset(enumFacing7);
                        if (BlockUtils.replaceable(offsetPos7)) {
                            for (EnumFacing enumFacing8 : EnumFacing.values()) {
                                if (enumFacing8 != EnumFacing.UP && placeConditions(enumFacing8, yOffset, xOffset)) {
                                    BlockPos offsetPos8 = offsetPos7.offset(enumFacing8);
                                    if (!BlockUtils.replaceable(offsetPos8) && !BlockUtils.isInteractable(BlockUtils.getBlock(offsetPos8))) {
                                        possibleBlocks.add(new PlaceData(offsetPos8, enumFacing8.getOpposite()));
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        else {
            return null;
        }
        return possibleBlocks.isEmpty() ? null : possibleBlocks;
    }

    private boolean placeConditions(EnumFacing enumFacing, int yCondition, int xCondition) {
        if (xCondition == -1) {
            return enumFacing == EnumFacing.EAST;
        }
        if (yCondition == 1) {
            return enumFacing == EnumFacing.DOWN;
        }

        return true;
    }

    /*private boolean allowedFaces(EnumFacing enumFacing) {
        if (yaw >= 0 && yaw < 90) {
            //Utils.print("1");
            //west south
            return enumFacing == EnumFacing.DOWN || enumFacing == EnumFacing.WEST || enumFacing == EnumFacing.SOUTH;
        }
        else if (yaw >= 90 && yaw < 180) {
            //Utils.print("2");
            //north west
            return enumFacing == EnumFacing.DOWN || enumFacing == EnumFacing.NORTH || enumFacing == EnumFacing.WEST;
        }
        else if (yaw == 180 || yaw >= -180 && yaw < -90) {
            //Utils.print("3");
            //north east
            return enumFacing == EnumFacing.DOWN || enumFacing == EnumFacing.NORTH || enumFacing == EnumFacing.EAST;
        }
        else if (yaw >= -90 && yaw <= 0) {
            //Utils.print("4");
            //east south
            return enumFacing == EnumFacing.DOWN || enumFacing == EnumFacing.EAST || enumFacing == EnumFacing.SOUTH;
        }

        return false;
    }*/

    float applyGcd(float value) {
        float gcd = 0.2F * 0.2F * 0.2F * 8.0F;
        return (float) ((double) value - (double) value % ((double) gcd * 0.15D));
    }

    float getMotionYaw() {
        return MathHelper.wrapAngleTo180_float((float) Math.toDegrees(Math.atan2(mc.thePlayer.motionZ, mc.thePlayer.motionX)) - 90.0F);
    }

    float getMotionYaw2() {
        return (float) Math.toDegrees(Math.atan2(mc.thePlayer.motionZ, mc.thePlayer.motionX)) - 90.0F;
    }

    private int getSpeedLevel() {
        for (PotionEffect potionEffect : mc.thePlayer.getActivePotionEffects()) {
            if (potionEffect.getEffectName().equals("potion.moveSpeed")) {
                return potionEffect.getAmplifier() + 1;
            }
            return 0;
        }
        return 0;
    }

    double[] speedLevels = {0.48, 0.5, 0.52, 0.58, 0.68};

    double getSpeed(int speedLevel) {
        if (speedLevel >= 0) {
            return speedLevels[speedLevel];
        }
        return speedLevels[0];
    }

    double[] floatSpeedLevels = {0.2, 0.22, 0.28, 0.29, 0.3};

    double getFloatSpeed(int speedLevel) {
        double min = 0;
        double value = 0;
        double input = (motion.getInput() / 100);
        if (mc.thePlayer.moveStrafing != 0 && mc.thePlayer.moveForward != 0) min = 0.003;
        value = floatSpeedLevels[0] - min;
        if (speedLevel >= 0) {
            value = floatSpeedLevels[speedLevel] - min;
        }
        value *= input;
        return value;
    }

    private void handleMotion() {
        if (handleFastScaffolds() > 0 || ModuleManager.tower.canTower()) {
            return;
        }
        double input = (motion.getInput() / 100);
        mc.thePlayer.motionX *= input;
        mc.thePlayer.motionZ *= input;
    }

    public float hardcodedYaw() {
        float simpleYaw = 0F;
        float f = 0.8F;

        if (mc.thePlayer.moveForward >= f) {
            simpleYaw -= 180;
            if (mc.thePlayer.moveStrafing >= f) simpleYaw += 45;
            if (mc.thePlayer.moveStrafing <= -f) simpleYaw -= 45;
        }
        else if (mc.thePlayer.moveForward == 0) {
            simpleYaw -= 180;
            if (mc.thePlayer.moveStrafing >= f) simpleYaw += 90;
            if (mc.thePlayer.moveStrafing <= -f) simpleYaw -= 90;
        }
        else if (mc.thePlayer.moveForward <= -f) {
            if (mc.thePlayer.moveStrafing >= f) simpleYaw -= 45;
            if (mc.thePlayer.moveStrafing <= -f) simpleYaw += 45;
        }
        return simpleYaw;
    }

    public boolean holdingBlocks() {
        if (autoSwap.isToggled() && ModuleManager.autoSwap.spoofItem.isToggled() && lastSlot.get() != mc.thePlayer.inventory.currentItem && totalBlocks() > 0) {
            ((IMixinItemRenderer) mc.getItemRenderer()).setCancelUpdate(true);
            ((IMixinItemRenderer) mc.getItemRenderer()).setCancelReset(true);
        }
        ItemStack heldItem = mc.thePlayer.getHeldItem();
        if (!autoSwap.isToggled() || getSlot() == -1) {
            if (heldItem == null || !(heldItem.getItem() instanceof ItemBlock) || !Utils.canBePlaced((ItemBlock) heldItem.getItem())) {
                return false;
            }
        }
        return true;
    }

    private double getMovementAngle() {
        double angle = Math.toDegrees(Math.atan2(-mc.thePlayer.moveStrafing, mc.thePlayer.moveForward));
        return angle == -0 ? 0 : angle;
    }

    private int getSlot() {
        int slot = -1;
        int highestStack = -1;
        ItemStack heldItem = mc.thePlayer.getHeldItem();
        for (int i = 0; i < 9; ++i) {
            final ItemStack itemStack = mc.thePlayer.inventory.mainInventory[i];
            if (itemStack != null && itemStack.getItem() instanceof ItemBlock && Utils.canBePlaced((ItemBlock) itemStack.getItem()) && itemStack.stackSize > 0) {
                if (Utils.getBedwarsStatus() == 2 && ((ItemBlock) itemStack.getItem()).getBlock() instanceof BlockTNT) {
                    continue;
                }
                if (itemStack != null && heldItem != null && (heldItem.getItem() instanceof ItemBlock) && Utils.canBePlaced((ItemBlock) heldItem.getItem()) && ModuleManager.autoSwap.sameType.isToggled() && !(itemStack.getItem().getClass().equals(heldItem.getItem().getClass()))) {
                    continue;
                }
                if (itemStack.stackSize > highestStack) {
                    highestStack = itemStack.stackSize;
                    slot = i;
                }
            }
        }
        return slot;
    }

    public static boolean bypassRots() {
        return (ModuleManager.scaffold.rotation.getInput() == 2 || ModuleManager.scaffold.rotation.getInput() == 0);
    }

    public boolean setSlot() {
        int slot = getSlot();
        if (slot == -1) {
            return false;
        }
        if (blockSlot == -1) {
            blockSlot = slot;
        }
        if (lastSlot.get() == -1) {
            lastSlot.set(mc.thePlayer.inventory.currentItem);
        }
        if (autoSwap.isToggled() && blockSlot != -1) {
            if (ModuleManager.autoSwap.swapToGreaterStack.isToggled()) {
                mc.thePlayer.inventory.currentItem = slot;
            }
            else {
                mc.thePlayer.inventory.currentItem = blockSlot;
            }
            //Utils.print("set slot?");
        }

        ItemStack heldItem = mc.thePlayer.getHeldItem();
        if (heldItem == null || !(heldItem.getItem() instanceof ItemBlock) || !Utils.canBePlaced((ItemBlock) heldItem.getItem())) {
            blockSlot = -1;
            return false;
        }
        return true;
    }


    static class PlaceData {
        EnumFacing enumFacing;
        BlockPos blockPos;
        Vec3 hitVec;

        PlaceData(BlockPos blockPos, EnumFacing enumFacing) {
            this.enumFacing = enumFacing;
            this.blockPos = blockPos;
        }

        public PlaceData(EnumFacing enumFacing, BlockPos blockPos) {
            this.enumFacing = enumFacing;
            this.blockPos = blockPos;
        }
    }
}