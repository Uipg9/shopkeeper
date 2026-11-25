package dev.brian.shopkeeper.client;

import com.pocketuicore.component.GridPanel;
import com.pocketuicore.component.HoverButton;
import com.pocketuicore.component.TextInputComponent;
import com.pocketuicore.component.UIComponent;
import com.pocketuicore.component.VerticalListPanel;
import com.pocketuicore.screen.PocketScreen;
import net.minecraft.network.chat.Component;

/**
 * Minimal PocketUICore screen: a {@link VerticalListPanel} holding a click-counter
 * {@link HoverButton} and a {@link GridPanel} of cell buttons. Open it with
 * {@code Minecraft.getInstance().gui.setScreen(new ExamplePocketScreen())}.
 */
public class ExamplePocketScreen extends PocketScreen {
	/** Left-click count on the counter button (read by the gametest). */
	public static int clicks;

	private HoverButton counter;
	private TextInputComponent input;

	public ExamplePocketScreen() {
		super(Component.literal("Pocket UI example"));
		clicks = 0;
	}

	@Override
	protected UIComponent buildUI() {
		int w = 240;
		int h = 134;
		VerticalListPanel list = new VerticalListPanel((width - w) / 2, (height - h) / 2, w, h, 8, 6);

		counter = new HoverButton(0, 0, 0, 24, "Clicked 0 times", null);
		counter.setOnClick(() -> {
			clicks++;
			counter.setLabel("Clicked " + clicks + " times");
		});
		list.addChild(counter);

		input = new TextInputComponent(0, 0, 0, 20);
		input.setPlaceholder("Type here");
		list.addChild(input);

		GridPanel grid = new GridPanel(0, 0, w - 16, 60, 3, 4);
		for (int i = 1; i <= 6; i++) {
			int cell = i;
			grid.addChild(new HoverButton(0, 0, 40, 24, "#" + cell, () -> counter.setLabel("Cell " + cell)));
		}
		list.addChild(grid);
		grid.layout(); // re-run now that the list has positioned and stretched the grid
		return list;
	}

	/** Current text of the input field (read by the gametest). */
	public String inputText() {
		return input.getText();
	}

	/** Centre of the text input in GUI-scaled coordinates. */
	public int[] inputCenter() {
		return new int[] { input.getX() + input.getWidth() / 2, input.getY() + input.getHeight() / 2 };
	}

	/** Centre of the counter button in GUI-scaled coordinates. */
	public int[] counterCenter() {
		return new int[] { counter.getX() + counter.getWidth() / 2, counter.getY() + counter.getHeight() / 2 };
	}
}
