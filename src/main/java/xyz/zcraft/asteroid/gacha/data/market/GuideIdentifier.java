package xyz.zcraft.asteroid.gacha.data.market;

import lombok.Getter;
import xyz.zcraft.asteroid.util.Query;

import java.util.Objects;

public abstract class GuideIdentifier {
    private static final Object OBJ = new Object();

    public abstract Query getGuideQueryParam();

    @Getter
    public static class Card extends GuideIdentifier {
        private final String cardId;

        private Card(String cardId) {
            this.cardId = cardId;
        }

        public static Card from(xyz.zcraft.asteroid.gacha.data.Card card) {
            return new Card(card.id());
        }

        @Override
        public Query getGuideQueryParam() {
            final Query query = Query.create();
            query.param("cardId", cardId);
            return query;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof Card other) {
                return cardId.equalsIgnoreCase(other.cardId);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return Objects.hash(OBJ, cardId);
        }

        @Override
        public String toString() {
            return "GuideIdentifier@Card@" + cardId;
        }
    }

    @Getter
    public static class Item extends GuideIdentifier {
        private final String itemId;

        private Item(String itemId) {
            this.itemId = itemId;
        }

        public static Item of(String itemId) {
            return new Item(itemId);
        }

        public static Item from(xyz.zcraft.asteroid.gacha.data.Item item) {
            return new Item(item.id());
        }

        @Override
        public Query getGuideQueryParam() {
            return Query.of("itemId", itemId);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof Item other) {
                return itemId.equalsIgnoreCase(other.itemId);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return Objects.hash(OBJ, itemId);
        }

        @Override
        public String toString() {
            return "GuideIdentifier@Item@" + itemId;
        }
    }
}
