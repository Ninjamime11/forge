/**
 * 
 */
package forge.game.card;

import java.util.Map;

import com.google.common.collect.ForwardingTable;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Table;

import forge.game.Game;
import forge.game.spellability.SpellAbility;
import forge.game.trigger.TriggerType;
import forge.game.zone.ZoneType;

public class CardZoneTable extends ForwardingTable<ZoneType, ZoneType, CardCollection> {
    // TODO use EnumBasedTable if exist
    private Table<ZoneType, ZoneType, CardCollection> dataMap = HashBasedTable.create();

    /**
     * special put logic, add Card to Card Collection
     */
    public CardCollection put(ZoneType rowKey, ZoneType columnKey, Card value) {
        CardCollection old;
        if (contains(rowKey, columnKey)) {
            old = get(rowKey, columnKey);
            old.add(value);
        } else {
            old = new CardCollection(value);
            dataMap.put(rowKey, columnKey, old);
        }
        return old;
    }

    @Override
    protected Table<ZoneType, ZoneType, CardCollection> delegate() {
        return dataMap;
    }

    public void triggerChangesZoneAll(final Game game) {
        if (!isEmpty()) {
            final Map<String, Object> runParams = Maps.newHashMap();
            runParams.put("Cards", this);
            game.getTriggerHandler().runTrigger(TriggerType.ChangesZoneAll, runParams, false);
        }
    }

    public CardCollection filterCards(Iterable<ZoneType> origin, ZoneType destination, String valid, Card host, SpellAbility sa) {
        CardCollection allCards = new CardCollection();
        if (destination != null) {
            if (!containsColumn(destination)) {
                return allCards;
            }
        }
        if (origin != null) {
            for (ZoneType z : origin) {
                if (containsRow(z)) {
                    if (destination != null) {
                        allCards.addAll(row(z).get(destination));
                    } else {
                        for (CardCollection c : row(z).values()) {
                            allCards.addAll(c);
                        }
                    }
                }
            }
        } else if (destination != null) {
            for (CardCollection c : column(destination).values()) {
                allCards.addAll(c);
            }
        } else {
            for (CardCollection c : values()) {
                allCards.addAll(c);
            }
        }

        if (valid != null) {
            allCards = CardLists.getValidCards(allCards, valid.split(","), host.getController(), host, sa);
        }
        return allCards;
    }

    public Iterable<Card> getAllCards() {
        return Iterables.concat(values());
    }
}
