package forge.ai.ability;

import com.google.common.base.Predicates;
import com.google.common.collect.Lists;
import forge.ai.AiCardMemory;
import forge.ai.ComputerUtilAbility;
import forge.ai.ComputerUtilCard;
import forge.ai.ComputerUtilMana;
import forge.ai.SpellAbilityAi;
import forge.card.CardType;
import forge.game.ability.AbilityUtils;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.card.CardCollectionView;
import forge.game.card.CardLists;
import forge.game.card.CardPredicates;
import forge.game.keyword.Keyword;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import forge.util.Aggregates;
import java.util.List;

public class ChooseTypeAi extends SpellAbilityAi {
    @Override
    protected boolean canPlayAI(Player aiPlayer, SpellAbility sa) {
        if (!sa.hasParam("AILogic")) {
            return false;
        } else if ("MostProminentComputerControls".equals(sa.getParam("AILogic"))) {
            if (ComputerUtilAbility.getAbilitySourceName(sa).equals("Mirror Entity Avatar")) {
                return doMirrorEntityLogic(aiPlayer, sa);
            }
        }

        return doTriggerAINoCost(aiPlayer, sa, false);
    }

    private boolean doMirrorEntityLogic(Player aiPlayer, SpellAbility sa) {
        if (AiCardMemory.isRememberedCard(aiPlayer, sa.getHostCard(), AiCardMemory.MemorySet.ANIMATED_THIS_TURN)) {
            return false;
        }
        if (!aiPlayer.getGame().getPhaseHandler().is(PhaseType.MAIN1, aiPlayer)) {
            return false;
        }
        
        CardCollectionView otb = aiPlayer.getCardsIn(ZoneType.Battlefield);
        List<String> valid = Lists.newArrayList(CardType.getAllCreatureTypes());

        String chosenType = ComputerUtilCard.getMostProminentType(otb, valid);
        if (chosenType.isEmpty()) {
            // Account for the situation when only changelings are on the battlefield
            boolean allChangeling = false;
            for (Card c : otb) {
                if (c.isCreature() && c.hasStartOfKeyword(Keyword.CHANGELING.toString())) {
                    chosenType = Aggregates.random(valid); // just choose a random type for changelings
                    allChangeling = true;
                    break;
                }
            }

            if (!allChangeling) {
                // Still empty, probably no creatures on board
                return false;
            }
        }
        
        int maxX = ComputerUtilMana.determineMaxAffordableX(aiPlayer, sa);
        int avgPower = 0;
        
        // predict the opposition
        CardCollection oppCreatures = CardLists.filter(aiPlayer.getOpponents().getCreaturesInPlay(), CardPredicates.Presets.UNTAPPED);
        int maxOppPower = 0;
        int maxOppToughness = 0;
        int oppUsefulCreatures = 0;
        
        for (Card oppCre : oppCreatures) {
            if (ComputerUtilCard.isUselessCreature(aiPlayer, oppCre)) {
                continue;
            }
            // TODO: should low-power tokens be considered here?
            if (oppCre.isToken() && oppCre.getCurrentPower() < 2 && oppCre.getCurrentToughness() < 3) {
                continue;
            }
            if (oppCre.getCurrentPower() > maxOppPower) {
                maxOppPower = oppCre.getCurrentPower();
            }
            if (oppCre.getCurrentToughness() > maxOppToughness) {
                maxOppToughness = oppCre.getCurrentToughness();
            }
            oppUsefulCreatures++;
        }

        if (maxX > 1) {
            CardCollection cre = CardLists.filter(aiPlayer.getCardsIn(ZoneType.Battlefield),
                    Predicates.and(CardPredicates.isType(chosenType), CardPredicates.Presets.UNTAPPED));
            if (!cre.isEmpty()) {
                for (Card c: cre) {
                    avgPower += c.getCurrentPower();
                }
                avgPower /= cre.size();
                if (maxX > avgPower && maxX > maxOppPower && maxX > maxOppToughness && cre.size() >= oppUsefulCreatures) {
                    sa.setSVar("PayX", String.valueOf(maxX));
                    AiCardMemory.rememberCard(aiPlayer, sa.getHostCard(), AiCardMemory.MemorySet.ANIMATED_THIS_TURN);
                    return true;
                }
            }
        }
        
        return false;
    }

    @Override
    protected boolean doTriggerAINoCost(Player ai, SpellAbility sa, boolean mandatory) {
        if (sa.usesTargeting()) {
            sa.resetTargets();
            sa.getTargets().add(ai);
        } else {
            for (final Player p : AbilityUtils.getDefinedPlayers(sa.getHostCard(), sa.getParam("Defined"), sa)) {
                if (p.isOpponentOf(ai) && !mandatory) {
                    return false;
                }
            }
        }
        return true;
    }

}
