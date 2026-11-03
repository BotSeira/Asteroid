package xyz.zcraft.asteroid.gacha.data.market;

import lombok.Getter;
import xyz.zcraft.asteroid.util.Query;

import java.util.Objects;

public abstract class Identifier {
    private static final Object OBJ = new Object();
    @Getter
    private final GuideIdentifier guideIdentifier;

    protected Identifier(GuideIdentifier guideIdentifier) {
        this.guideIdentifier = guideIdentifier;
    }

    public static Identifier empty() {
        return new Identifier.Empty();
    }

    public abstract String getUniqueId();

    public abstract Query getListingQueryParam();

    @Getter
    public static class Card extends Identifier {
        private final String username;
        private final boolean sig;
        private final long rarityPopularity;

        private Card(String username, boolean sig, long rarityPopularity, GuideIdentifier guideIdentifier) {
            super(guideIdentifier);
            this.username = username;
            this.sig = sig;
            this.rarityPopularity = rarityPopularity;
        }

        public static Identifier.Card of(String username, boolean sig) {
            return new Identifier.Card(username, sig, 0, null);
        }

        public static Identifier.Card from(xyz.zcraft.asteroid.gacha.data.Card card) {
            return new Identifier.Card(card.player().username(), card.sig() != null, card.rarityPopularity(), GuideIdentifier.Card.from(card));
        }

        public boolean shouldFetchGuide() {
            return sig || rarityPopularity >= 7300;
        }

        @Override
        public String getUniqueId() {
            return (sig ? "sig@" : "@") + username;
        }

        @Override
        public Query getListingQueryParam() {
            final Query query = Query.create();
            query.param("q", username);
            if (sig) {
                query.param("rarity", "signature");
            }
            return query;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof Identifier.Card other) {
                return sig == other.sig && username.equalsIgnoreCase(other.username);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return Objects.hash(OBJ, username, sig);
        }

        @Override
        public String toString() {
            return "Identifier@Card@" + getUsername() + "@" + isSig() + "@" + getRarityPopularity();
        }
    }

    @Getter
    public static class Item extends Identifier {
        private final String itemId;

        private Item(String itemId) {
            super(GuideIdentifier.Item.of(itemId));
            this.itemId = itemId;
        }

        public static Identifier.Item of(String itemId) {
            return new Item(itemId);
        }

        public static Identifier.Item from(xyz.zcraft.asteroid.gacha.data.Item item) {
            return new Item(item.id());
        }

        @Override
        public String getUniqueId() {
            return itemId;
        }

        @Override
        public Query getListingQueryParam() {
            return Query.of("itemId", itemId);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof Identifier.Item other) {
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
            return "Identifier@Item@" + itemId;
        }
    }

    private static class Empty extends Identifier {
        private Empty() {
            super(null);
        }

        @Override
        public String getUniqueId() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Query getListingQueryParam() {
            return null;
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof Empty;
        }

        @Override
        public int hashCode() {
            return Objects.hash(OBJ);
        }

        @Override
        public String toString() {
            return "Identifier@Empty";
        }
    }
}
