package forge.screens.deckeditor;

import org.apache.commons.lang3.StringUtils;

import com.google.common.collect.ImmutableList;

import forge.Singletons;
import forge.deck.DeckProxy;
import forge.deck.io.DeckPreferences;
import forge.gui.framework.FScreen;
import forge.model.FModel;
import forge.screens.deckeditor.controllers.CAllDecks;
import forge.screens.deckeditor.controllers.DeckController;
import forge.screens.deckeditor.views.VAllDecks;
import forge.screens.deckeditor.views.VCurrentDeck;
import forge.toolbox.FOptionPane;
import forge.util.Localizer;

/** 
 * Handles editor preferences saving and loading.
 * 
 * <br><br><i>(S at beginning of class name denotes a static factory.)</i>
 */
public class SEditorIO {
    /**
     * Saves the current deck, with various prompts depending on the
     * current save environment.
     * 
     * @return boolean, true if success
     */
    public static boolean saveDeck() {
        final DeckController<?> controller = CDeckEditorUI.SINGLETON_INSTANCE.getCurrentEditorController().getDeckController();
        final String name = VCurrentDeck.SINGLETON_INSTANCE.getTxfTitle().getText();
        final String deckStr = DeckProxy.getDeckString(controller.getModelPath(), name);
        boolean performSave = false;

        // Warn if no name
        if (name == null || name.isEmpty()) {
            FOptionPane.showMessageDialog(Localizer.getInstance().getMessage("lblPleaseTitleBoxNameYourDeck"),
                    Localizer.getInstance().getMessage("lblSaveErrorWarning"), FOptionPane.ERROR_ICON);
            return false;
        }
        // Confirm if overwrite
        else if (controller.fileExists(name)) {
            if (!StringUtils.equals(name, controller.getModelName())) { // prompt only if name was changed
                performSave = FOptionPane.showConfirmDialog(
                    Localizer.getInstance().getMessage("lblAlreadyDeckName") + name + Localizer.getInstance().getMessage("lblOverwriteConfirm"),
                    Localizer.getInstance().getMessage("lblOverwriteDeck"));
            } else {
                performSave = true;
            }
        }
        // Confirm if a new deck will be created
        else if (FOptionPane.showConfirmDialog(Localizer.getInstance().getMessage("lblThisWillCreateNewDeckNameIs", name),
                    Localizer.getInstance().getMessage("lblCreateDeckConfirm"))) {
            performSave = true;
        }

        if (performSave) {
            controller.saveAs(name);
            CAllDecks.SINGLETON_INSTANCE.refresh(); //pull new deck into deck list and select it
            VAllDecks.SINGLETON_INSTANCE.getLstDecks().setSelectedString(deckStr);
            // Set current quest deck to selected
            if (Singletons.getControl().getCurrentScreen() == FScreen.DECK_EDITOR_QUEST) {
                FModel.getQuest().setCurrentDeck(name);
            }
        }

        if (Singletons.getControl().getCurrentScreen() == FScreen.DECK_EDITOR_CONSTRUCTED) {
            DeckPreferences.setCurrentDeck(deckStr);
        }

        return true;
    }

    private final static ImmutableList<String> confirmSaveOptions = ImmutableList.of(
        Localizer.getInstance().getMessage("lblSave"),
        Localizer.getInstance().getMessage("lblDontSave"),
        Localizer.getInstance().getMessage("lblCancel")
    );
    /**
     * Prompts to save changes if necessary.
     * 
     * @return boolean, true if success
     */
    public static boolean confirmSaveChanges(FScreen screen, boolean isClosing) {
        if (CDeckEditorUI.SINGLETON_INSTANCE.hasChanges()) {
            //ensure Deck Editor is active before showing dialog
            if (!Singletons.getControl().ensureScreenActive(screen)) {
                return false;
            }

            final int choice = FOptionPane.showOptionDialog(Localizer.getInstance().getMessage("lblSaveChangesCurrentDeck"), Localizer.getInstance().getMessage("lblSaveChangesConfirm"),
                    FOptionPane.QUESTION_ICON, confirmSaveOptions);

            if (choice == -1 || choice == 2) { return false; }

            if (choice == 0 && !saveDeck()) { return false; }

            //reload deck if user chose not to save changes and deck isn't being closed
            if (!isClosing) {
                CDeckEditorUI.SINGLETON_INSTANCE.getCurrentEditorController().getDeckController().reload();
            }
        }
        return true;
    }
}
