package uz.minestax.autodeposit;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

@Mod(modid = AutoDepositMod.MODID, name = "AutoDeposit", version = "1.0")
public class AutoDepositMod {

    public static final String MODID = "autodeposit";

    // === SOZLAMALAR ===
    private static final int INTERVAL_TICKS = 3 * 60 * 20; // 3 daqiqa (20 tick/sek)
    private static final int SYNC_DELAY_TICKS = 15;        // GUI ochilgach slotlar sync bo'lishi uchun kutish
    private static final int GUI_TIMEOUT_TICKS = 100;      // GUI ochilmasa 5 sekunddan keyin bekor qilish
    private static final int CLICK_PASS_INTERVAL = 3;      // Har necha tickda bir pass (server javob berishi uchun)
    private static final int MAX_DEPOSIT_TICKS = 100;      // 5 sekunddan keyin majburiy to'xtash (echest to'lib qolsa)
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
                    boolean anyLeft = depositPass(mc);
                    if (!anyLeft || depositCounter >= MAX_DEPOSIT_TICKS) {
                        mc.player.closeScreen();
                        state = State.IDLE;
                    }
                } else if (depositCounter >= MAX_DEPOSIT_TICKS) {
                    mc.player.closeScreen();
                    state = State.IDLE;
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

        boolean anyLeft = false;
        for (int i = playerInvStart; i < totalSlots; i++) {
            Slot slot = container.inventorySlots.get(i);
            if (slot != null && slot.getHasStack()) {
                mc.playerController.windowClick(container.windowId, slot.slotNumber, 0, ClickType.QUICK_MOVE, player);
                anyLeft = true;
            }
        }
        return anyLeft;
    }
}
