package uz.minestax.autodeposit;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

@Mod(modid = AutoDepositMod.MODID, name = "AutoDeposit", version = "1.7")
public class AutoDepositMod {

    public static final String MODID = "autodeposit";

    // === SOZLAMALAR ===
    private static final int INTERVAL_TICKS = 1 * 60 * 20; // Har 1 daqiqada
    private static final int SYNC_DELAY_TICKS = 15;        
    private static final int GUI_TIMEOUT_TICKS = 100;      
    private static final int CLICK_PASS_INTERVAL = 2;      
    private static final int MAX_DEPOSIT_TICKS = 30 * 20;  // 30 soniya max ishlaydi
    private static final String OPEN_COMMAND = "/team echest";

    // === TUGMALAR ===
    private static KeyBinding keyStart;
    private static KeyBinding keyStop;
    private boolean isModEnabled = true;

    private int tickCounter = 0;
    private int waitCounter = 0;
    private int syncCounter = 0;
    private int depositCounter = 0;

    private enum State { IDLE, WAITING_GUI, SYNCING, DEPOSITING }
    private State state = State.IDLE;

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(this);
        
        keyStart = new KeyBinding("AutoDeposit Start", Keyboard.KEY_P, "AutoDeposit");
        keyStop = new KeyBinding("AutoDeposit Stop", Keyboard.KEY_J, "AutoDeposit");
        
        ClientRegistry.registerKeyBinding(keyStart);
        ClientRegistry.registerKeyBinding(keyStop);
    }

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) return;

        if (keyStart.isPressed()) {
            isModEnabled = true;
            tickCounter = 0;
            mc.player.sendMessage(new TextComponentString(TextFormatting.GREEN + "[AutoDeposit] Mod YOQILDI. Har 1 daqiqada ishlaydi."));
        }
        
        if (keyStop.isPressed()) {
            isModEnabled = false;
            if (state != State.IDLE) {
                mc.player.closeScreen();
            }
            state = State.IDLE;
            mc.player.sendMessage(new TextComponentString(TextFormatting.RED + "[AutoDeposit] Mod O'CHIRILDI."));
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!isModEnabled) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.world == null) return;

        switch (state) {
            case IDLE:
                tickCounter++;
                if (tickCounter >= INTERVAL_TICKS) {
                    tickCounter = 0;
                    waitCounter = 0;
                    mc.player.sendChatMessage(OPEN_COMMAND);
                    state = State.WAITING_GUI;
                }
                break;

            case WAITING_GUI:
                waitCounter++;
                if (waitCounter >= GUI_TIMEOUT_TICKS) {
                    state = State.IDLE;
                }
                break;

            case SYNCING:
                syncCounter++;
                if (syncCounter >= SYNC_DELAY_TICKS) {
                    depositCounter = 0;
                    state = State.DEPOSITING;
                }
                break;

            case DEPOSITING:
                depositCounter++;
                if (depositCounter % CLICK_PASS_INTERVAL == 0) {
                    boolean movedAnything = depositPass(mc);
                    
                    if ((!movedAnything && mc.player.inventory.getItemStack().isEmpty()) || depositCounter >= MAX_DEPOSIT_TICKS) {
                        mc.player.closeScreen();
                        state = State.IDLE;
                    }
                }
                break;
        }
    }

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public void onGuiOpen(GuiOpenEvent event) {
        if (!isModEnabled || state != State.WAITING_GUI) return;
        if (event.getGui() instanceof GuiChest) {
            syncCounter = 0;
            state = State.SYNCING;
        }
    }

    private boolean depositPass(Minecraft mc) {
        EntityPlayerSP player = mc.player;
        Container container = player.openContainer;
        if (container == null) return false;

        int totalSlots = container.inventorySlots.size();
        int playerInvStart = totalSlots - 36;
        if (playerInvStart < 0) return false;

        ItemStack cursorStack = player.inventory.getItemStack();

        // 1. Kursorda narsa bo'lsa
        if (!cursorStack.isEmpty()) {
            Item item = cursorStack.getItem();
            
            // SRAZI YERGA TASHVORIYORISH: Qo'lga temir bloki tushsa, srazi yerga otib yuboradi!
            if (item == Item.getItemFromBlock(Blocks.IRON_BLOCK) || item.getRegistryName().toString().contains("iron_block")) {
                // slotNumber = -999 oynadan tashqariga chertish degani, bu narsani srazi yerga uloqtiradi
                mc.playerController.windowClick(container.windowId, -999, 0, ClickType.PICKUP, player);
                return true;
            }

            boolean isTargetBlock = (item == Item.getItemFromBlock(Blocks.DIAMOND_BLOCK) || 
                                     item == Item.getItemFromBlock(Blocks.EMERALD_BLOCK) || 
                                     item == Item.getItemFromBlock(Blocks.GOLD_BLOCK));

            if (isTargetBlock) {
                int maxLimit = 32;

                for (int i = 0; i < playerInvStart; i++) {
                    Slot targetSlot = container.getSlot(i);
                    ItemStack targetStack = targetSlot.getStack();

                    if (!targetStack.isEmpty() && targetStack.getItem() == item && targetStack.getCount() < maxLimit) {
                        mc.playerController.windowClick(container.windowId, targetSlot.slotNumber, 1, ClickType.PICKUP, player);
                        return true;
                    }
                }

                for (int i = 0; i < playerInvStart; i++) {
                    Slot targetSlot = container.getSlot(i);
                    ItemStack targetStack = targetSlot.getStack();

                    if (targetStack.isEmpty()) {
                        if (cursorStack.getCount() > 32) {
                            mc.playerController.windowClick(container.windowId, targetSlot.slotNumber, 1, ClickType.PICKUP, player);
                        } else {
                            mc.playerController.windowClick(container.windowId, targetSlot.slotNumber, 0, ClickType.PICKUP, player);
                        }
                        return true;
                    }
                }
            }
            
            // Boshqa narsa bo'lsa inventarga qaytarish
            for (int i = playerInvStart; i < totalSlots; i++) {
                Slot targetSlot = container.getSlot(i);
                if (targetSlot.getStack().isEmpty()) {
                    mc.playerController.windowClick(container.windowId, targetSlot.slotNumber, 0, ClickType.PICKUP, player);
                    return true;
                }
            }
            return false;
        }

        // 2. Inventardan blok qidirish (Qo'limiz bo'sh bo'lsa)
        for (int i = playerInvStart; i < totalSlots; i++) {
            Slot slot = container.getSlot(i);
            if (slot != null && slot.getHasStack()) {
                ItemStack stack = slot.getStack();
                Item item = stack.getItem();
                String registryName = item.getRegistryName().toString();

                // Agar slotda temir bloki bo'lsa, unga umuman tegmasdan o'tib ketamiz
                if (item == Item.getItemFromBlock(Blocks.IRON_BLOCK) || registryName.contains("iron_block")) {
                    continue; 
                }

                if (item == Item.getItemFromBlock(Blocks.DIAMOND_BLOCK) ||
                    item == Item.getItemFromBlock(Blocks.EMERALD_BLOCK) ||
                    item == Item.getItemFromBlock(Blocks.GOLD_BLOCK)) {
                    
                    mc.playerController.windowClick(container.windowId, slot.slotNumber, 0, ClickType.PICKUP, player);
                    return true;
                }
            }
        }

        return false;
    }
}
