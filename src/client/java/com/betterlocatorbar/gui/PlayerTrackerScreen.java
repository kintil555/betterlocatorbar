package com.betterlocatorbar.gui;

import com.betterlocatorbar.network.PlayerDataPacket;
import com.betterlocatorbar.network.TrackerDataStore;
import com.betterlocatorbar.renderer.PlayerHeadRenderer;
import com.betterlocatorbar.util.TrackedPlayerData;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * [EXPERIMENTAL] Player Tracker GUI.
 *
 * <p>Opened with the configured keybind (default: B).
 * Shows all players on the server with:</p>
 * <ul>
 *   <li>Skin head icon</li>
 *   <li>Player name</li>
 *   <li>Coordinates (X, Y, Z)</li>
 *   <li>Current dimension</li>
 *   <li>Online/offline status</li>
 * </ul>
 * <p>A search box allows filtering by player name.</p>
 */
public class PlayerTrackerScreen extends Screen {

    // ─── Layout constants ─────────────────────────────────────────────────────
    private static final int PANEL_WIDTH  = 340;
    private static final int PANEL_HEIGHT = 220;
    private static final int ROW_HEIGHT   = 36;
    private static final int HEAD_SIZE    = 20;
    private static final int PADDING      = 8;
    private static final int SCROLLBAR_W  = 6;

    // ─── Colors (ARGB) ────────────────────────────────────────────────────────
    private static final int COLOR_BG          = 0xCC1A1A2E;
    private static final int COLOR_PANEL       = 0xDD16213E;
    private static final int COLOR_ROW_EVEN    = 0x220E4D91;
    private static final int COLOR_ROW_HOVER   = 0x440E4D91;
    private static final int COLOR_BORDER      = 0xFF0F3460;
    private static final int COLOR_ACCENT      = 0xFF533483;
    private static final int COLOR_TITLE       = 0xFFE94560;
    private static final int COLOR_TEXT        = 0xFFE0E0E0;
    private static final int COLOR_TEXT_DIM    = 0xFF808080;
    private static final int COLOR_ONLINE      = 0xFF44FF88;
    private static final int COLOR_OFFLINE     = 0xFFFF4444;
    private static final int COLOR_SCROLLBAR   = 0xFF533483;
    private static final int COLOR_EXPERIMENTAL = 0xFFFFAA00;

    // ─── State ────────────────────────────────────────────────────────────────
    private TextFieldWidget searchField;
    private int scrollOffset = 0;
    private int hoveredRow   = -1;
    private int panelX, panelY;

    /** All players received from server */
    private List<TrackedPlayerData> allPlayers = new ArrayList<>();
    /** Filtered list based on search query */
    private List<TrackedPlayerData> filteredPlayers = new ArrayList<>();

    private int lastRefreshTick = 0;
    private static final int REFRESH_INTERVAL = 40; // ticks (~2 seconds)

    public PlayerTrackerScreen() {
        super(Text.translatable("screen.betterlocatorbar.tracker"));
    }

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    @Override
    protected void init() {
        panelX = (width - PANEL_WIDTH) / 2;
        panelY = (height - PANEL_HEIGHT) / 2;

        // Search text field
        searchField = new TextFieldWidget(
                textRenderer,
                panelX + PADDING,
                panelY + 28,
                PANEL_WIDTH - PADDING * 2 - SCROLLBAR_W - 4,
                16,
                Text.translatable("gui.betterlocatorbar.search")
        );
        searchField.setSuggestion(Text.translatable("gui.betterlocatorbar.search_hint").getString());
        searchField.setChangedListener(query -> {
            scrollOffset = 0;
            applyFilter(query);
        });
        addSelectableChild(searchField);
        setFocused(searchField);

        // Request fresh data from server
        requestRefresh();
    }

    @Override
    public void tick() {
        super.tick();

        // Periodically refresh player data
        lastRefreshTick++;
        if (lastRefreshTick >= REFRESH_INTERVAL) {
            lastRefreshTick = 0;
            requestRefresh();
        }
    }

    private void requestRefresh() {
        // Load from client-side cache (updated by server packet handler)
        PlayerDataPacket.sendRequest();

        // Also populate from local PlayerListEntries as fallback
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getNetworkHandler() != null) {
            List<TrackedPlayerData> fromNetwork = mc.getNetworkHandler()
                    .getPlayerList()
                    .stream()
                    .filter(e -> e.getProfile() != null
                            && (mc.player == null
                            || !e.getProfile().id().equals(mc.player.getUuid())))
                    .map(e -> new TrackedPlayerData(
                            e.getProfile().id(),
                            e.getProfile().name(),
                            0, 0, 0,   // coords unknown without server mod
                            "minecraft:overworld",
                            true
                    ))
                    .toList();

            // Merge: prefer server data (has real coords) over local data
            List<TrackedPlayerData> serverData = TrackerDataStore.getPlayers();
            if (!serverData.isEmpty()) {
                allPlayers = new ArrayList<>(serverData);
            } else {
                allPlayers = new ArrayList<>(fromNetwork);
            }
            applyFilter(searchField != null ? searchField.getText() : "");
        }
    }

    private void applyFilter(String query) {
        String q = query.toLowerCase(Locale.ROOT).trim();
        if (q.isEmpty()) {
            filteredPlayers = new ArrayList<>(allPlayers);
        } else {
            filteredPlayers = allPlayers.stream()
                    .filter(p -> p.name().toLowerCase(Locale.ROOT).contains(q))
                    .toList();
        }
    }

    // ─── Rendering ────────────────────────────────────────────────────────────

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Dim background
        context.fill(0, 0, width, height, 0x88000000);

        // Panel background
        context.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, COLOR_PANEL);

        // Panel border
        drawBorder(context, panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT, COLOR_BORDER, 1);

        // Title
        context.drawTextWithShadow(textRenderer,
                Text.translatable("screen.betterlocatorbar.tracker"),
                panelX + PADDING, panelY + PADDING, COLOR_TITLE);

        // [EXPERIMENTAL] badge
        String expBadge = "⚗ EXPERIMENTAL";
        int badgeX = panelX + PANEL_WIDTH - textRenderer.getWidth(expBadge) - PADDING;
        context.drawTextWithShadow(textRenderer, expBadge, badgeX, panelY + PADDING, COLOR_EXPERIMENTAL);

        // Accent line below title
        context.fill(panelX + PADDING, panelY + 19,
                panelX + PANEL_WIDTH - PADDING, panelY + 20, COLOR_ACCENT);

        // Search field
        searchField.render(context, mouseX, mouseY, delta);

        // Player list area
        int listX  = panelX + PADDING;
        int listY  = panelY + 50;
        int listW  = PANEL_WIDTH - PADDING * 2 - SCROLLBAR_W - 4;
        int listH  = PANEL_HEIGHT - 60;
        int maxVisible = listH / ROW_HEIGHT;

        // Scissor to list area
        context.enableScissor(listX, listY, listX + listW, listY + listH);

        hoveredRow = -1;
        if (filteredPlayers.isEmpty()) {
            String empty = allPlayers.isEmpty()
                    ? "No players online"
                    : "No results for \"" + searchField.getText() + "\"";
            context.drawCenteredTextWithShadow(textRenderer, empty,
                    listX + listW / 2, listY + listH / 2 - 4, COLOR_TEXT_DIM);
        } else {
            for (int i = 0; i < filteredPlayers.size(); i++) {
                int rowY = listY + (i - scrollOffset) * ROW_HEIGHT;
                if (rowY + ROW_HEIGHT < listY || rowY > listY + listH) continue;

                TrackedPlayerData player = filteredPlayers.get(i);
                boolean hovered = mouseX >= listX && mouseX < listX + listW
                        && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;
                if (hovered) hoveredRow = i;

                renderPlayerRow(context, player, listX, rowY, listW, i, hovered);
            }
        }

        context.disableScissor();

        // Scrollbar
        renderScrollbar(context,
                panelX + PANEL_WIDTH - PADDING - SCROLLBAR_W + 2,
                panelY + 50,
                SCROLLBAR_W,
                PANEL_HEIGHT - 60);

        // Footer: player count
        String countText = filteredPlayers.size() + " / " + allPlayers.size() + " players";
        context.drawTextWithShadow(textRenderer, countText,
                panelX + PADDING,
                panelY + PANEL_HEIGHT - 12,
                COLOR_TEXT_DIM);

        // Close hint
        String closeHint = "Press ESC to close";
        context.drawTextWithShadow(textRenderer, closeHint,
                panelX + PANEL_WIDTH - textRenderer.getWidth(closeHint) - PADDING,
                panelY + PANEL_HEIGHT - 12,
                COLOR_TEXT_DIM);

        super.render(context, mouseX, mouseY, delta);
    }

    private void renderPlayerRow(DrawContext context, TrackedPlayerData player,
                                  int x, int y, int width, int index, boolean hovered) {
        // Row background
        int rowColor = hovered ? COLOR_ROW_HOVER : (index % 2 == 0 ? COLOR_ROW_EVEN : 0);
        if (rowColor != 0) {
            context.fill(x, y, x + width, y + ROW_HEIGHT, rowColor);
        }

        // Head icon
        int headX = x + 4;
        int headY = y + (ROW_HEIGHT - HEAD_SIZE) / 2;

        // Try to get PlayerListEntry for skin texture
        MinecraftClient mc = MinecraftClient.getInstance();
        PlayerListEntry entry = null;
        if (mc.getNetworkHandler() != null) {
            entry = mc.getNetworkHandler().getPlayerListEntry(player.uuid());
        }
        if (entry != null) {
            PlayerHeadRenderer.drawPlayerHead(context, entry, headX, headY, HEAD_SIZE, 1.0f);
        } else {
            // Fallback: colored square with first letter
            context.fill(headX, headY, headX + HEAD_SIZE, headY + HEAD_SIZE, COLOR_ACCENT);
            String initial = player.name().isEmpty() ? "?" : player.name().substring(0, 1).toUpperCase();
            context.drawCenteredTextWithShadow(textRenderer, initial,
                    headX + HEAD_SIZE / 2, headY + HEAD_SIZE / 2 - 4, COLOR_TEXT);
        }

        // Online indicator dot
        int dotColor = player.isOnline() ? COLOR_ONLINE : COLOR_OFFLINE;
        context.fill(headX + HEAD_SIZE - 5, headY + HEAD_SIZE - 5,
                headX + HEAD_SIZE, headY + HEAD_SIZE, dotColor);

        // Player name
        int textX = headX + HEAD_SIZE + 6;
        int nameY  = y + 5;
        context.drawTextWithShadow(textRenderer, player.name(), textX, nameY, COLOR_TEXT);

        // Coordinates (only show if we have real data)
        boolean hasCoords = player.x() != 0 || player.y() != 0 || player.z() != 0;
        String coordsLine = hasCoords
                ? player.coordsString()
                : "Coordinates unavailable (client-only mod)";
        context.drawTextWithShadow(textRenderer, coordsLine,
                textX, nameY + 10, hasCoords ? COLOR_TEXT_DIM : 0xFF555555);

        // Dimension badge
        String dim = player.dimensionLabel();
        int dimColor = switch (player.dimension()) {
            case "minecraft:the_nether" -> 0xFFFF6644;
            case "minecraft:the_end"    -> 0xFFAA88FF;
            default -> 0xFF44AAFF;
        };
        context.drawTextWithShadow(textRenderer, "[" + dim + "]",
                textX, nameY + 20, dimColor);
    }

    private void renderScrollbar(DrawContext context, int x, int y, int w, int h) {
        if (filteredPlayers.size() <= 1) return;

        int maxVisible = h / ROW_HEIGHT;
        if (filteredPlayers.size() <= maxVisible) return;

        // Track background
        context.fill(x, y, x + w, y + h, 0x33FFFFFF);

        // Thumb
        float thumbRatio = (float) maxVisible / filteredPlayers.size();
        int thumbH = Math.max(16, (int) (h * thumbRatio));
        float scrollRatio = (float) scrollOffset / Math.max(1, filteredPlayers.size() - maxVisible);
        int thumbY = y + (int) ((h - thumbH) * scrollRatio);

        context.fill(x, thumbY, x + w, thumbY + thumbH, COLOR_SCROLLBAR);
    }

    // ─── Input ────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int maxVisible = (PANEL_HEIGHT - 60) / ROW_HEIGHT;
        int maxScroll  = Math.max(0, filteredPlayers.size() - maxVisible);
        scrollOffset   = Math.clamp((int) (scrollOffset - verticalAmount), 0, maxScroll);
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false; // Don't pause in multiplayer
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private static void drawBorder(DrawContext ctx, int x, int y, int w, int h, int color, int thickness) {
        ctx.fill(x, y, x + w, y + thickness, color);                  // top
        ctx.fill(x, y + h - thickness, x + w, y + h, color);          // bottom
        ctx.fill(x, y, x + thickness, y + h, color);                   // left
        ctx.fill(x + w - thickness, y, x + w, y + h, color);           // right
    }
}
