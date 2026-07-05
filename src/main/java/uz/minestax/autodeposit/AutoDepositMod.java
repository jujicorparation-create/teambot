package uz.minestax.autodeposit;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

@Mod(modid = AutoDepositMod.MODID, name = "AutoDeposit", version = "1.4")
public class AutoDepositMod {

    public static final String MODID = "autodeposit";

    // === SOZLAMALAR ===
    private static final int INTERVAL_TICKS = 3 * 60 * 20; // 3 daqiqa (Echest ochilish oralig'i)
    private static final int SYNC_DELAY_TICKS = 15;        // GUI yuklanishi uchun kutish
    private static final int GUI_TIMEOUT_TICKS = 100;      
    private static final int CLICK_PASS_INTERVAL = 2;      // Har 2 tickda 1 marta bosish
    private static final int MAX_DEPOSIT_TICKS = 30 * 20;  // ROSA 30 SONIYA (30 * 20 tick)
    private static final String OPEN_COMMAND = "/team echest";

    private int tickCounter = 0;
    private int waitCounter = 0;
    private int syncCounter = 0;
    private int depositCounter = 0;

    private enum State { IDLE, WAITING_GUI, SYNCING, DEPOSITING }
    private State state = State.IDLE;

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

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
                    
                    // Bloklar tugasa yoki 30 soniya o'tsa GUI yopiladi
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
        if (state != State.WAITING_GUI) return;
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

        // 1. Kursorda (sichqonchada) blok bo'lsa, uni Echestga joylaymiz
        if (!cursorStack.isEmpty()) {
            Item item = cursorStack.getItem();
            boolean isTargetBlock = (item == Item.getItemFromBlock(Blocks.DIAMOND_BLOCK) || 
                                     item == Item.getItemFromBlock(Blocks.EMERALD_BLOCK) || 
                                     item == Item.getItemFromBlock(Blocks.GOLD_BLOCK) || 
                                     item == Item.getItemFromBlock(Blocks.IRON_BLOCK));

            if (isTargetBlock) {
                int maxLimit = 32; // HAMMA BLOKLAR UCHUN CHEKLOV 32 TA!

                // Birinchi navbatda: Echestda xuddi shu blokdan bor va soni 32 tadan kam bo'lgan slotlarni to'ldiramiz
                for (int i = 0; i < playerInvStart; i++) {
                    Slot targetSlot = container.getSlot(i);
                    ItemStack targetStack = targetSlot.getStack();

                    if (!targetStack.isEmpty() && targetStack.getItem() == item && targetStack.getCount() < maxLimit) {
                        // Sichqonchaning o'ng tugmasi bilan 1 donadan tashlaydi
                        mc.playerController.windowClick(container.windowId, targetSlot.slotNumber, 1, ClickType.PICKUP, player);
                        return true;
                    }
                }

                // Ikkinchi navbatda: Butunlay bo'sh slotlarga qo'yamiz
                for (int i = 0; i < playerInvStart; i++) {
                    Slot targetSlot = container.getSlot(i);
                    ItemStack targetStack = targetSlot.getStack();

                    if (targetStack.isEmpty()) {
                        if (cursorStack.getCount() > 32) {
                            // Qo'limizda 32 tadan ko'p bo'lsa, o'ng tugma (1) bilan donalab tashlaymiz (32 ta bo'lguncha)
                            mc.playerController.windowClick(container.windowId, targetSlot.slotNumber, 1, ClickType.PICKUP, player);
                        } else {
                            // Qo'limizda 32 ta yoki undan kam bo'lsa, chap tugma (0) bilan hammasini qo'yamiz
                            mc.playerController.windowClick(container.windowId, targetSlot.slotNumber, 0, ClickType.PICKUP, player);
                        }
                        return true;
                    }
                }
            }
            
            // Echest butunlay to'la bo'lsa, qo'limizdagi blokni inventarimizga qaytaramiz
            for (int i = playerInvStart; i < totalSlots; i++) {
                Slot targetSlot = container.getSlot(i);
                if (targetSlot.getStack().isEmpty()) {
                    mc.playerController.windowClick(container.windowId, targetSlot.slotNumber, 0, ClickType.PICKUP, player);
                    return true;
                }
            }
            return false;
        }

        // 2. Qo'limiz bo'sh bo'lsa, inventardan 4 ta blokdan birini qidirib qo'lga olamiz
        for (int i = playerInvStart; i < totalSlots; i++) {
            Slot slot = container.getSlot(i);
            if (slot != null && slot.getHasStack()) {
                ItemStack stack = slot.getStack();
                Item item = stack.getItem();

                if (item == Item.getItemFromBlock(Blocks.DIAMOND_BLOCK) ||
                    item == Item.getItemFromBlock(Blocks.EMERALD_BLOCK) ||
                    item == Item.getItemFromBlock(Blocks.GOLD_BLOCK) ||
                    item == Item.getItemFromBlock(Blocks.IRON_BLOCK)) {
                    
                    // Chap tugma bilan stakni butunlay qo'lga olish
                    mc.playerController.windowClick(container.windowId, slot.slotNumber, 0, ClickType.PICKUP, player);
                    return true;
                }
            }
        }

        return false;
    }
}

