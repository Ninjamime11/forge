package forge.game.ability.effects;


import java.util.List;
import java.util.Map;

import forge.game.Game;
import forge.game.GameEntity;
import forge.game.GameObject;
import forge.game.ability.AbilityUtils;
import forge.game.ability.SpellAbilityEffect;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.card.CardCollectionView;
import forge.game.card.CardLists;
import forge.game.card.CardZoneTable;
import forge.game.card.CardPredicates;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.spellability.TargetRestrictions;
import forge.game.zone.ZoneType;
import forge.util.CardTranslation;
import forge.util.Lang;
import forge.util.Localizer;
import forge.util.collect.FCollection;

import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;

public class AttachEffect extends SpellAbilityEffect {
    @Override
    public void resolve(SpellAbility sa) {
        final Card host = sa.getHostCard();
        final Game game = host.getGame();

        if (host.isAura() && sa.isSpell()) {
            CardZoneTable table = new CardZoneTable();
            host.setController(sa.getActivatingPlayer(), 0);

            ZoneType previousZone = host.getZone().getZoneType();

            // The Spell_Permanent (Auras) version of this AF needs to
            // move the card into play before Attaching
            final Card c = game.getAction().moveToPlay(host, sa);
            sa.setHostCard(c);

            ZoneType newZone = c.getZone().getZoneType();
            if (newZone != previousZone) {
                table.put(previousZone, newZone, c);
            }
            table.triggerChangesZoneAll(game, sa);
        }

        final Card source = sa.getHostCard();

        CardCollection attachments;

        final Player p = sa.getActivatingPlayer();

        Player chooser = p;
        if (sa.hasParam("Chooser")) {
            chooser = Iterables.getFirst(AbilityUtils.getDefinedPlayers(source, sa.getParam("Chooser"), sa), null);
        };

        if (sa.hasParam("Object")) {
            attachments = AbilityUtils.getDefinedCards(source, sa.getParam("Object"), sa);
        } else if (sa.hasParam("Choices")) {
            ZoneType choiceZone = ZoneType.Battlefield;
            if (sa.hasParam("ChoiceZone")) {
                choiceZone = ZoneType.smartValueOf(sa.getParam("ChoiceZone"));
            }
            String title = sa.hasParam("ChoiceTitle") ? sa.getParam("ChoiceTitle") : Localizer.getInstance().getMessage("lblChoose") + " ";

            CardCollection choices = CardLists.getValidCards(game.getCardsIn(choiceZone), sa.getParam("Choices"), p, source, sa);

            Map<String, Object> params = Maps.newHashMap();
            params.put("Target", Iterables.getFirst(getDefinedEntitiesOrTargeted(sa, "Defined"), null));

            Card c = chooser.getController().chooseSingleEntityForEffect(choices, sa, title, params);
            if (c == null) {
                return;
            }
            attachments = new CardCollection(c);
        } else {
            attachments = new CardCollection(source);
        }

        if (attachments.isEmpty()) {
            return;
        }

        GameEntity attachTo;

        if (sa.hasParam("Object") && sa.hasParam("Choices")) {
            ZoneType choiceZone = ZoneType.Battlefield;
            if (sa.hasParam("ChoiceZone")) {
                choiceZone = ZoneType.smartValueOf(sa.getParam("ChoiceZone"));
            }
            String title = sa.hasParam("ChoiceTitle") ? sa.getParam("ChoiceTitle") : Localizer.getInstance().getMessage("lblChoose") + " ";

            CardCollection choices = CardLists.getValidCards(game.getCardsIn(choiceZone), sa.getParam("Choices"), p, source, sa);
            // Object + Choices means Attach Aura/Equipment onto new another card it can attach
            // if multiple attachments, all of them need to be able to attach to new card
            for (final Card attachment : attachments) {
                if (sa.hasParam("Move")) {
                    Card e = attachment.getAttachedTo();
                    if (e != null)
                        choices.remove(e);
                }
                choices = CardLists.filter(choices, CardPredicates.canBeAttached(attachment));
            }

            Map<String, Object> params = Maps.newHashMap();
            params.put("Attachments", attachments);

            attachTo = chooser.getController().chooseSingleEntityForEffect(choices, sa, title, params);
        } else {
            FCollection<GameEntity> targets = new FCollection<>(getDefinedEntitiesOrTargeted(sa, "Defined"));
            if (targets.isEmpty()) {
                return;
            } else {
                String title = Localizer.getInstance().getMessage("lblChoose");
                Map<String, Object> params = Maps.newHashMap();
                params.put("Attachments", attachments);
                attachTo = chooser.getController().chooseSingleEntityForEffect(targets, sa, title, params);
            }
        }

        String attachToName = null;
        if (attachTo == null) {
            return;
        } else if (attachTo instanceof Card) {
            attachToName = CardTranslation.getTranslatedName(((Card)attachTo).getName());
        } else {
            attachToName = attachTo.toString();
        }

        // If Cast Targets will be checked on the Stack
        for (final Card attachment : attachments) {
            String message = Localizer.getInstance().getMessage("lblDoYouWantAttachSourceToTarget", CardTranslation.getTranslatedName(attachment.getName()), attachToName);
            if (sa.hasParam("Optional") && !p.getController().confirmAction(sa, null, message))
            // TODO add params for message
                continue;

            attachment.attachToEntity(attachTo);
        }
    }

    @Override
    protected String getStackDescription(SpellAbility sa) {
        final StringBuilder sb = new StringBuilder();

        sb.append(" Attach to ");

        final List<GameObject> targets = getTargets(sa);
        // Should never allow more than one Attachment per card

        sb.append(Lang.joinHomogenous(targets));
        return sb.toString();
    }

    /**
     * Attach aura on indirect enter battlefield.
     *
     * @param source
     *            the source
     * @return true, if successful
     */
    public static boolean attachAuraOnIndirectEnterBattlefield(final Card source) {
        // When an Aura ETB without being cast you can choose a valid card to
        // attach it to
        final SpellAbility aura = source.getFirstAttachSpell();

        if (aura == null) {
            return false;
        }
        aura.setActivatingPlayer(source.getController());
        final Game game = source.getGame();
        final TargetRestrictions tgt = aura.getTargetRestrictions();

        Player p = source.getController();
        if (tgt.canTgtPlayer()) {
            final FCollection<Player> players = new FCollection<>();

            for (Player player : game.getPlayers()) {
                if (player.isValid(tgt.getValidTgts(), aura.getActivatingPlayer(), source, aura)) {
                    players.add(player);
                }
            }
            final Player pa = p.getController().chooseSingleEntityForEffect(players, aura,
                    Localizer.getInstance().getMessage("lblSelectAPlayerAttachSourceTo", CardTranslation.getTranslatedName(source.getName())), null);
            if (pa != null) {
                source.attachToEntity(pa);
                return true;
            }
        }
        else {
            CardCollectionView list = game.getCardsIn(tgt.getZone());
            list = CardLists.getValidCards(list, tgt.getValidTgts(), aura.getActivatingPlayer(), source, aura);
            if (list.isEmpty()) {
                return false;
            }

            final Card o = p.getController().chooseSingleEntityForEffect(list, aura,
                    Localizer.getInstance().getMessage("lblSelectACardAttachSourceTo", CardTranslation.getTranslatedName(source.getName())), null);
            if (o != null) {
                source.attachToEntity(o);
                return true;
            }
        }
        return false;
    }
}
