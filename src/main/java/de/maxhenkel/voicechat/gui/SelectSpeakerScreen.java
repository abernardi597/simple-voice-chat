package de.maxhenkel.voicechat.gui;

import com.mojang.blaze3d.matrix.MatrixStack;
import de.maxhenkel.voicechat.Main;
import de.maxhenkel.voicechat.voice.client.Client;
import de.maxhenkel.voicechat.voice.client.DataLines;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.util.text.*;

import javax.annotation.Nullable;

public class SelectSpeakerScreen extends ListScreen<String> {

    protected int selected;

    public SelectSpeakerScreen() {
        super(DataLines.getSpeakerNames(), new TranslationTextComponent("gui.voicechat.select_speaker.title"));
        for (int i = 0; i < elements.size(); i++) {
            String element = elements.get(i);
            if (element.equals(Main.CLIENT_CONFIG.speaker.get())) {
                index = i;
                selected = i;
                break;
            }
        }
    }

    @Override
    public void updateCurrentElement() {
        super.updateCurrentElement();
        String currentElement = getCurrentElement();
        if (currentElement == null) {
            return;
        }
        int bw = 60;
        Button b = addButton(new Button(width / 2 - bw / 2, guiTop + 35, bw, 20, new TranslationTextComponent("message.voicechat.select"), button -> {
            Main.CLIENT_CONFIG.speaker.set(currentElement);
            button.active = false;
            Client client = Main.CLIENT_VOICE_EVENTS.getClient();
            if (client != null) {
                client.reloadDataLines();
            }
        }));

        b.active = !currentElement.equals(Main.CLIENT_CONFIG.speaker.get());
    }

    @Override
    protected void renderText(MatrixStack stack, @Nullable String element, int mouseX, int mouseY, float partialTicks) {
        ITextComponent title = getTitle();
        int titleWidth = font.getStringPropertyWidth(title);
        font.func_238422_b_(stack, title.func_241878_f(), (float) (guiLeft + (xSize - titleWidth) / 2), guiTop + 7, FONT_COLOR);

        IFormattableTextComponent name = getCurrentElement() == null ? new TranslationTextComponent("message.voicechat.no_speaker") : new StringTextComponent(getCurrentElement());
        int nameWidth = font.getStringPropertyWidth(name);
        font.func_238422_b_(stack, name.mergeStyle(TextFormatting.WHITE).func_241878_f(), (float) (guiLeft + (xSize - nameWidth) / 2), guiTop + 7 + font.FONT_HEIGHT + 7, 0);
    }
}
