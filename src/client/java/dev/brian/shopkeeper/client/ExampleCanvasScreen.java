package dev.brian.shopkeeper.client;

import com.pocketcanvascore.render.PocketCanvas;
import com.pocketcanvascore.render.PocketRenderContexts;
import com.pocketcanvascore.render.PocketTheme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Minimal example of drawing with PocketCanvasCore primitives and PocketTheme colors. */
public class ExampleCanvasScreen extends Screen {
	public ExampleCanvasScreen() {
		super(Component.literal("Canvas example"));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		PocketCanvas canvas = new PocketCanvas(PocketRenderContexts.of(graphics, minecraft.getDeltaTracker()));
		int cx = width / 2;
		int cy = height / 2;
		canvas.drawRoundedRect(cx - 80, cy - 40, 160, 80, 8, PocketTheme.BACKGROUND_PANEL);
		canvas.drawRect(cx - 70, cy - 30, 140, 8, PocketTheme.ACCENT_PRIMARY);
		canvas.drawCircle(cx, cy + 10, 14, PocketTheme.ACCENT_SECONDARY);
	}
}
