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

@Mod(modid = AutoDepositMod.MODID, name = "AutoDeposit", version = "1.1")
public class AutoDepositMod {

    public static final String MODID = "autodeposit";

    // === SOZLAMALAR ===
    private static final int INTERVAL_TICKS = 3 * 60 * 20; // 3 daqiqa
    private static final int SYNC_DELAY_TICKS = 20;        // GUI yuklanishi uchun bir oz ko'proq kutish
    private static final int GUI_TIMEOUT_TICKS = 100;      
    private static final int CLICK_PASS_INTERVAL = 4;      // Bloklarni donalab qo'yganda server adashmasligi uchun 4 tick
    private static final int MAX_DEPOSIT_TICKS = 300;      // Ko'p marta bosilishi sababli taymautni uzaytirdik
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
                    // Agar qo'lda narsa qolmagan bo'lsa va boshqa ko'chadigan narsa bo'lmasa yoki vaqt tugasa
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
        int playerInvStart = totalSlots - 36; // Odatda Chest ochilganda oxirgi 36 slot player inventari bo'ladi
        if (playerInvStart < 0) return false;

        ItemStack cursorStack = player.inventory.getItemStack();

        // 1. Agar sichqonchada (kursorda) hozir blok ushlab turilgan bo'lsa, uni echestga joylashga harakat qilamiz
        if (!cursorStack.isEmpty()) {
            Item item = cursorStack.getItem();
            boolean isDiamondOrEmerald = (item == Item.getItemFromBlock(Blocks.DIAMOND_BLOCK) || item == Item.getItemFromBlock(Blocks.EMERALD_BLOCK));
            boolean isGoldOrIron = (item == Item.getItemFromBlock(Blocks.GOLD_BLOCK) || item == Item.getItemFromBlock(Blocks.IRON_BLOCK));

            if (isDiamondOrEmerald || isGoldOrIron) {
                // Echest slotlarini aylanib chiqamiz (0 dan playerInvStart gacha)
                for (int i = 0; i < playerInvStart; i++) {
                    Slot targetSlot = container.getSlot(i);
                    ItemStack targetStack = targetSlot.getStack();

                    int maxLimit = isDiamondOrEmerald ? 32 : 64;

                    // Slot bo'sh bo'lsa
                    if (targetStack.isEmpty()) {
                        if (isDiamondOrEmerald && cursorStack.getCount() > 32) {
                            // Agar qo'limizda 32 dan ko'p bo'lsa, o'ng tugma bilan donalab 32 ta qo'yamiz
                            mc.playerController.windowClick(container.windowId, targetSlot.slotNumber, 1, ClickType.PICKUP, player);
                            return true;
                        } else {
                            // Hammasini qo'yamiz
                            mc.playerController.windowClick(container.windowId, targetSlot.slotNumber, 0, ClickType.PICKUP, player);
                            return true;
                        }
                    }
                    // Slotda xuddi shu blok bo'lsa va limiti to'lmagan bo'lsa
                    else if (targetStack.getItem() == item && targetStack.getCount() < maxLimit) {
                        // O'ng tugmani bossak 1 donadan tashlaydi (Slot to'lguncha bosaveradi)
                        mc.playerController.windowClick(container.windowId, targetSlot.slotNumber, 1, ClickType.PICKUP, player);
                        return true;
                    }
                }
            }
            // Agar bu bloklarni echestga joylab bo'lmasa yoki u yer to'la bo'lsa, o'z inventarimizga qaytarib tashlaymiz
            for (int i = playerInvStart; i < totalSlots; i++) {
                Slot targetSlot = container.getSlot(i);
                if (targetSlot.getStack().isEmpty()) {
                    mc.playerController.windowClick(container.windowId, targetSlot.slotNumber, 0, ClickType.PICKUP, player);
                    return true;
                }
            }
            return false;
        }

        // 2. Agar qo'limiz bo'sh bo'lsa, inventardan kerakli bloklarni qidiramiz va uni kursorda ushlaymiz (Click)
        for (int i = playerInvStart; i < totalSlots; i++) {
            Slot slot = container.getSlot(i);
            if (slot != null && slot.getHasStack()) {
                ItemStack stack = slot.getStack();
                Item item = stack.getItem();

                if (item == Item.getItemFromBlock(Blocks.DIAMOND_BLOCK) ||
                    item == Item.getItemFromBlock(Blocks.EMERALD_BLOCK) ||
                    item == Item.getItemFromBlock(Blocks.GOLD_BLOCK) ||
                    item == Item.getItemFromBlock(Blocks.IRON_BLOCK)) {
                    
                    // Inventardan blokni chap tugma bilan bosib qo'lga olamiz
                    mc.playerController.windowClick(container.windowId, slot.slotNumber, 0, ClickType.PICKUP, player);
                    return true;
                }
            }
        }

        return false;
    }
}
